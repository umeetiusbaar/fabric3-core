/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fabric3.management.rest.runtime;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.fabric3.api.Role;
import org.fabric3.api.annotation.management.Management;
import org.fabric3.api.annotation.management.ManagementOperation;
import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.api.model.type.java.ManagementInfo;
import org.fabric3.api.model.type.java.ManagementOperationInfo;
import org.fabric3.api.model.type.java.OperationType;
import org.fabric3.management.rest.framework.DynamicResourceService;
import org.fabric3.management.rest.spi.ResourceHost;
import org.fabric3.management.rest.spi.ResourceListener;
import org.fabric3.management.rest.spi.ResourceMapping;
import org.fabric3.management.rest.spi.Verb;
import org.fabric3.management.rest.transformer.TransformerPair;
import org.fabric3.management.rest.transformer.TransformerPairService;
import org.fabric3.spi.management.ManagementExtension;
import org.fabric3.spi.model.type.java.JavaType;
import org.oasisopen.sca.annotation.Init;
import org.oasisopen.sca.annotation.Property;
import org.oasisopen.sca.annotation.Reference;

/**
 * Responsible for exporting components and instances as management resources.  As part of this process, a fully-navigable management resource hierarchy
 * will be dynamically created. For example, if a component is exported to /runtime/foo/bar and a /runtime/foo resource is not configured, one will be created
 * dynamically with a link to runtime/foo/bar. If a configured resource is later exported, any previously generated dynamic resource will be overriden.
 */
public class RestfulManagementExtension implements ManagementExtension {
    private static final JavaType JSON_INPUT_TYPE = new JavaType(InputStream.class, "JSON");
    private static final JavaType JSON_OUTPUT_TYPE = new JavaType(byte[].class, "JSON");

    private static final String EMPTY_PATH = "";
    private static final String ROOT_PATH = "/";

    private TransformerPairService pairService;

    private Method rootResourceMethod;
    private Method dynamicGetResourceMethod;

    private ResourceHost resourceHost;
    private ManagementSecurity security = ManagementSecurity.DISABLED;

    private List<ResourceListener> listeners = new ArrayList<>();
    private Map<String, ResourceMapping> dynamicResources = new ConcurrentHashMap<>();

    public RestfulManagementExtension(@Reference TransformerPairService pairService, @Reference Marshaller marshaller, @Reference ResourceHost resourceHost) {
        this.pairService = pairService;
        this.resourceHost = resourceHost;
    }

    @Property(required = false)
    public void setSecurity(String level) throws Fabric3Exception {
        try {
            security = ManagementSecurity.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new Fabric3Exception("Invalid management security setting:" + level);
        }
    }

    /**
     * Setter to support re-injection of optional listeners.
     *
     * @param listeners the listeners
     */
    @Reference(required = false)
    public void setListeners(List<ResourceListener> listeners) {
        this.listeners = listeners;
    }

    @Init()
    public void init() throws NoSuchMethodException {
        rootResourceMethod = ResourceInvoker.class.getMethod("invoke", HttpServletRequest.class);
        dynamicGetResourceMethod = DynamicResourceService.class.getMethod("getResource", HttpServletRequest.class);
    }

    public String getType() {
        return "fabric3.rest";
    }

    public void export(URI componentUri, ManagementInfo info, Supplier<?> supplier) throws Fabric3Exception {
        String root = info.getPath();
        if (root.length() == 0) {
            root = componentUri.getPath();
        }
        List<ResourceMapping> getMappings = new ArrayList<>();

        String identifier = componentUri.toString();

        boolean rootResourcePathOverride = false;
        for (ManagementOperationInfo operationInfo : info.getOperations()) {
            // calculate if a root resource needs to be created
            String path = operationInfo.getPath();
            if (ROOT_PATH.equals(path)) {
                rootResourcePathOverride = true;
            }
        }
        for (ManagementOperationInfo operationInfo : info.getOperations()) {
            Method method = operationInfo.getMethod();
            String path = operationInfo.getPath();
            OperationType type = operationInfo.getOperationType();
            Verb verb = getVerb(method, type);

            Set<Role> roles = operationInfo.getRoles();
            if (roles.isEmpty()) {
                // No roles specified for operation. Default to read/write roles specified on the class.
                if (Verb.GET == verb) {
                    roles = info.getReadRoles();
                } else {
                    roles = info.getWriteRoles();
                }
            }
            TransformerPair pair = pairService.getTransformerPair(Collections.singletonList(method), JSON_INPUT_TYPE, JSON_OUTPUT_TYPE);
            ResourceMapping mapping = createMapping(identifier, root, path, method, verb, supplier, pair, roles);
            if (Verb.GET == mapping.getVerb()) {
                getMappings.add(mapping);
            }

            createDynamicResources(mapping, root, rootResourcePathOverride);

            if (dynamicResources.remove(mapping.getPath()) != null) {
                resourceHost.unregisterPath(mapping.getPath(), mapping.getVerb());
            }
            resourceHost.register(mapping);
            notifyExport(path, mapping);
        }
        if (!rootResourcePathOverride) {
            createRootResource(identifier, root, getMappings);
        }
    }

    public void export(String name, String group, String description, Object instance) throws Fabric3Exception {
        String root = "/runtime/" + name;

        Set<Role> readRoles = new HashSet<>();
        Set<Role> writeRoles = new HashSet<>();
        parseRoles(instance, readRoles, writeRoles);

        boolean rootResourcePathOverride = false;
        List<ResourceMapping> getMappings = new ArrayList<>();

        List<Method> methods = Arrays.asList(instance.getClass().getMethods());
        for (Method method : methods) {
            ManagementOperation opAnnotation = method.getAnnotation(ManagementOperation.class);
            if (opAnnotation == null) {
                continue;
            }
            String path = opAnnotation.path();
            if (ROOT_PATH.equals(path)) {
                rootResourcePathOverride = true;
            }

        }
        for (Method method : methods) {
            Set<Role> roles;
            ManagementOperation opAnnotation = method.getAnnotation(ManagementOperation.class);
            if (opAnnotation != null) {
                OperationType type = OperationType.valueOf(opAnnotation.type().toString());
                Verb verb = getVerb(method, type);
                String[] rolesAllowed = opAnnotation.rolesAllowed();
                if (rolesAllowed.length == 0) {
                    if (Verb.GET == verb) {
                        roles = readRoles;
                    } else {
                        roles = writeRoles;
                    }
                } else {
                    roles = new HashSet<>();
                    for (String roleName : rolesAllowed) {
                        roles.add(new Role(roleName));
                    }
                }

                TransformerPair pair = pairService.getTransformerPair(Collections.singletonList(method), JSON_INPUT_TYPE, JSON_OUTPUT_TYPE);
                ResourceMapping mapping = createMapping(name, root, EMPTY_PATH, method, verb, instance, pair, roles);

                if (Verb.GET == mapping.getVerb()) {
                    getMappings.add(mapping);
                }

                createDynamicResources(mapping, root, rootResourcePathOverride);

                resourceHost.register(mapping);
                notifyExport(mapping.getRelativePath(), mapping);

            }
        }
        if (!rootResourcePathOverride) {
            createRootResource(name, root, getMappings);
        }
    }

    public void remove(URI componentUri, ManagementInfo info) throws Fabric3Exception {
        String identifier = componentUri.toString();
        resourceHost.unregister(identifier);
        for (ResourceListener listener : listeners) {
            listener.onRootResourceRemove(identifier);
            listener.onSubResourceRemove(identifier);
        }
    }

    public void remove(String name, String group) throws Fabric3Exception {
        resourceHost.unregister(name);
        for (ResourceListener listener : listeners) {
            listener.onRootResourceRemove(name);
            listener.onSubResourceRemove(name);
        }
    }

    /**
     * Returns the HTTP verb for the operation.
     *
     * @param method the method name
     * @param type   the operation type
     * @return the HTTP verb
     */
    private Verb getVerb(Method method, OperationType type) {
        String methodName = method.getName();
        if (OperationType.UNDEFINED == type) {
            return MethodHelper.convertToVerb(methodName);
        } else {
            return Verb.valueOf(type.toString());
        }
    }

    /**
     * Notifies listeners of a root or sub- resource export event
     *
     * @param path    the resource path
     * @param mapping the resource mapping
     */
    private void notifyExport(String path, ResourceMapping mapping) {
        if (ROOT_PATH.equals(path)) {
            for (ResourceListener listener : listeners) {
                listener.onRootResourceExport(mapping);
            }
        } else {
            for (ResourceListener listener : listeners) {
                listener.onSubResourceExport(mapping);
            }
        }
    }

    /**
     * Creates a managed artifact mapping.
     *
     * @param identifier the identifier used to group a set of mappings during deployment and undeployment
     * @param root       the root path for the artifact
     * @param path       the relative path of the operation. The path may be blank, in which case one will be calculated from the method name
     * @param method     the management operation
     * @param verb       the HTTP verb the operation uses
     * @param instance   the artifact
     * @param pair       the transformer pair for deserializing JSON requests and serializing responses
     * @param roles      the roles required to invoke the operation
     * @return the mapping
     */
    private ResourceMapping createMapping(String identifier,
                                          String root,
                                          String path,
                                          Method method,
                                          Verb verb,
                                          Object instance,
                                          TransformerPair pair,
                                          Set<Role> roles) {
        String methodName = method.getName();
        String rootPath;
        if (path.length() == 0) {
            path = MethodHelper.convertToPath(methodName);
        }
        path = path.toLowerCase();
        if (ROOT_PATH.equals(path)) {
            // if the path is for the root resource, there is no sub-path
            rootPath = root.toLowerCase();
        } else {
            rootPath = root.toLowerCase() + "/" + path;
        }
        return new ResourceMapping(identifier, rootPath, path, verb, method, instance, pair, roles);
    }

    /**
     * Creates a root resource that aggreggates information from sub-resources.
     *
     * @param identifier the identifier used to group a set of mappings during deployment and undeployment
     * @param root       the root path
     * @param mappings   the sub-resource mappings   @throws ManagementException if an error occurs creating the root resource
     * @throws Fabric3Exception if there is an error creating the mapping
     */
    private void createRootResource(String identifier, String root, List<ResourceMapping> mappings) throws Fabric3Exception {
        ResourceInvoker invoker = new ResourceInvoker(mappings, security);
        List<Method> methods = mappings.stream().map(ResourceMapping::getMethod).collect(Collectors.toList());
        TransformerPair pair = pairService.getTransformerPair(methods, JSON_INPUT_TYPE, JSON_OUTPUT_TYPE);
        root = root.toLowerCase();
        Set<Role> roles = Collections.emptySet();
        ResourceMapping mapping = new ResourceMapping(identifier, root, root, Verb.GET, rootResourceMethod, invoker, pair, roles);
        ResourceMapping previous = dynamicResources.remove(root);
        if (previous != null) {
            resourceHost.unregisterPath(previous.getPath(), Verb.GET);
        }
        resourceHost.register(mapping);
        for (ResourceListener listener : listeners) {
            listener.onRootResourceExport(mapping);
        }
        createDynamicResources(mapping, root, false);
    }

    /**
     * Parses read and write roles specified on an {@link Management} annotation.
     *
     * @param instance   the instance containing the annotation
     * @param readRoles  the collection of read roles to populate
     * @param writeRoles the collection of write roles to populate
     */
    private void parseRoles(Object instance, Set<Role> readRoles, Set<Role> writeRoles) {
        Management annotation = instance.getClass().getAnnotation(Management.class);
        if (annotation != null) {
            String[] readRoleNames = annotation.readRoles();
            for (String roleName : readRoleNames) {
                readRoles.add(new Role(roleName));
            }
            String[] writeRoleNames = annotation.writeRoles();
            for (String roleName : writeRoleNames) {
                writeRoles.add(new Role(roleName));
            }
        }

        // set default roles if none specified
        if (readRoles.isEmpty()) {
            readRoles.add(new Role(Management.FABRIC3_ADMIN_ROLE));
            readRoles.add(new Role(Management.FABRIC3_OBSERVER_ROLE));
        }
        if (writeRoles.isEmpty()) {
            writeRoles.add(new Role(Management.FABRIC3_ADMIN_ROLE));
        }
    }

    /**
     * Creates parent resources dynamically for the given mapping if they do not already exist.
     *
     * @param mapping            the mapping
     * @param rootResourcePath   the root resource path for this hierarchy
     * @param createRootResource true if a dynamic root resource should be dynamically created
     * @throws Fabric3Exception if there was an error creating parent resources
     */
    private void createDynamicResources(ResourceMapping mapping, String rootResourcePath, boolean createRootResource) throws Fabric3Exception {
        ResourceMapping previous = dynamicResources.remove(mapping.getPath());
        if (previous != null) {
            // A dynamic resource service was already registered. Remove it since it is being replaced by a configured resource service.
            resourceHost.unregisterPath(previous.getPath(), previous.getVerb());
        } else {
            List<ResourceMapping> dynamicMappings = createDynamicResourceMappings(mapping, rootResourcePath, createRootResource);
            // add the resources as listeners first as parents need to be notified of children in order to generate links during registration
            listeners.addAll(dynamicMappings.stream().map(dynamicMapping -> (ResourceListener) dynamicMapping.getInstance()).collect(Collectors.toList()));
            dynamicMappings.stream().filter(dynamicMapping -> !resourceHost.isPathRegistered(dynamicMapping.getPath(), dynamicMapping.getVerb())).forEach(
                    dynamicMapping -> {
                        resourceHost.register(dynamicMapping);
                        notifyExport(dynamicMapping.getRelativePath(), dynamicMapping);
                    });
        }
    }

    /**
     * Creates a collection of parent resources dynamically for the given mapping if they do not already exist.
     *
     * @param mapping            the mapping
     * @param rootResourcePath   the root resource path for this hierarchy
     * @param createRootResource true if a dynamic root resource should be dynamically created
     * @return the an ordered collection of resources starting with the top-most resource in the hierarchy
     */
    private List<ResourceMapping> createDynamicResourceMappings(ResourceMapping mapping, String rootResourcePath, boolean createRootResource) {
        String path = mapping.getPath();
        List<ResourceMapping> mappings = new ArrayList<>();
        while (path != null) {
            String current = PathHelper.getParentPath(path);
            if (path.equals(current)) {
                // reached the path hierarchy root or the top
                break;
            } else if (dynamicResources.containsKey(current)) {
                break;
            } else if (resourceHost.isPathRegistered(current, Verb.GET)) {
                break;
            }
            path = current;
            try {
                if (!createRootResource && current.equals(rootResourcePath)) {
                    continue; // skip creating the root resource since one is provided
                }
                DynamicResourceService resourceService = new DynamicResourceService(current);
                List<Method> list = Collections.singletonList(dynamicGetResourceMethod);
                TransformerPair pair = pairService.getTransformerPair(list, JSON_INPUT_TYPE, JSON_OUTPUT_TYPE);
                ResourceMapping dynamicMapping = new ResourceMapping(current,
                                                                     current,
                                                                     ROOT_PATH,
                                                                     Verb.GET,
                                                                     dynamicGetResourceMethod,
                                                                     resourceService,
                                                                     pair,
                                                                     mapping.getRoles());
                mappings.add(dynamicMapping);
                dynamicResources.put(dynamicMapping.getPath(), dynamicMapping);
            } catch (Fabric3Exception e) {
                throw new AssertionError(e);
            }
        }
        // reverse the collection to register the top-most parent first
        Collections.reverse(mappings);
        return mappings;
    }

}
