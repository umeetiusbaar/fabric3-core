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

package org.fabric3.binding.ws.metro.generator;

import javax.jws.WebMethod;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fabric3.api.binding.ws.model.WsBindingDefinition;
import org.fabric3.api.model.type.component.BindingHandlerDefinition;
import org.fabric3.api.model.type.contract.DataType;
import org.fabric3.api.model.type.contract.Operation;
import org.fabric3.binding.ws.metro.provision.ConnectionConfiguration;
import org.fabric3.spi.model.physical.PhysicalBindingHandlerDefinition;

/**
 *
 */
public class GenerationHelper {
    private static final String WS_SECURITY_UTILITY_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";

    private GenerationHelper() {
    }


    /**
     * Parses HTTP connection information and creates a connection configuration.
     *
     * @param definition the binding definition
     * @return the HTTP configuration
     * @throws InvalidConfigurationException if a configuration value is invalid
     */
    public static ConnectionConfiguration createConnectionConfiguration(WsBindingDefinition definition) throws InvalidConfigurationException {
        ConnectionConfiguration configuration = new ConnectionConfiguration();
        Map<String, String> configProperties = definition.getConfiguration();
        if (configProperties != null) {
            String connectTimeout = configProperties.get("connectTimeout");
            if (connectTimeout != null) {
                try {
                    configuration.setConnectTimeout(Integer.parseInt(connectTimeout));
                } catch (NumberFormatException e) {
                    throw new InvalidConfigurationException("Invalid connectTimeout", e);
                }
            }
            String requestTimeout = configProperties.get("requestTimeout");
            if (requestTimeout != null) {
                try {
                    configuration.setRequestTimeout(Integer.parseInt(requestTimeout));
                } catch (NumberFormatException e) {
                    throw new InvalidConfigurationException("Invalid requestTimeout", e);
                }
            }
            String clientStreamingChunkSize = configProperties.get("clientStreamingChunkSize");
            if (clientStreamingChunkSize != null) {
                try {
                    configuration.setClientStreamingChunkSize(Integer.parseInt(clientStreamingChunkSize));
                } catch (NumberFormatException e) {
                    throw new InvalidConfigurationException("Invalid clientStreamingChunkSize", e);
                }
            }
        }
        return configuration;
    }

    public static List<PhysicalBindingHandlerDefinition> generateBindingHandlers(URI domainUri, WsBindingDefinition definition) {
        List<PhysicalBindingHandlerDefinition> handlers = new ArrayList<>();
        for (BindingHandlerDefinition handlerDefinition : definition.getHandlers()) {
            // URIs specified in handler elements in a composite are relative and must be made absolute
            URI resolvedUri = URI.create(domainUri.toString() + "/" + handlerDefinition.getTarget());
            handlers.add(new PhysicalBindingHandlerDefinition(resolvedUri));
        }
        return handlers;
    }

    /**
     * Returns the WSDL name for an operation following JAX-WS rules. Namely, if present the <code>@WebMethod.operationName()</code> attribute value is used,
     * otherwise the default operation name is returned.
     *
     * @param operation    the operation definition
     * @param serviceClass the implementation class
     * @return the WSDL operation name
     */
    private static String getWsdlName(Operation operation, Class<?> serviceClass) {
        Method method = findMethod(operation, serviceClass);
        WebMethod annotation = method.getAnnotation(WebMethod.class);
        if (annotation == null || annotation.operationName().length() < 1) {
            return operation.getName();
        }
        return annotation.operationName();
    }

    /**
     * Returns a Method corresponding to the operation definition on a service implementation class.
     *
     * @param operation    the operation definition
     * @param serviceClass the implementation class
     * @return the method
     */
    @SuppressWarnings({"unchecked"})
    private static Method findMethod(Operation operation, Class<?> serviceClass) {
        List<DataType> types = operation.getInputTypes();
        Class<?>[] params = new Class<?>[types.size()];
        for (int i = 0; i < types.size(); i++) {
            DataType type = types.get(i);
            params[i] = type.getType();
        }
        try {
            return serviceClass.getMethod(operation.getName(), params);
        } catch (NoSuchMethodException e) {
            // should not happen
            throw new AssertionError(e);
        }
    }

}