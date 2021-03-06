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
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.binding.ws.introspection;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.fabric3.api.annotation.wire.Key;
import org.fabric3.api.binding.ws.model.EndpointReference;
import org.fabric3.api.binding.ws.model.WsBinding;
import org.fabric3.api.model.type.component.BindingHandler;
import org.fabric3.spi.introspection.IntrospectionContext;
import org.fabric3.spi.introspection.xml.AbstractValidatingTypeLoader;
import org.fabric3.spi.introspection.xml.InvalidValue;
import org.fabric3.spi.introspection.xml.LoaderRegistry;
import org.fabric3.spi.introspection.xml.MissingAttribute;
import org.oasisopen.sca.Constants;
import org.oasisopen.sca.annotation.EagerInit;
import org.oasisopen.sca.annotation.Reference;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 *
 */
@EagerInit
@Key(Constants.SCA_PREFIX + "binding.ws")
public class WsBindingLoader extends AbstractValidatingTypeLoader<WsBinding> {
    private static final String WSDL_NS = "http://www.w3.org/ns/wsdl-instance";
    private static final String WSDL_2004_NS = "http://www.w3.org/2004/08/wsdl-instance";

    private final LoaderRegistry registry;

    /**
     * Constructor.
     *
     * @param registry the loader registry
     */
    public WsBindingLoader(@Reference LoaderRegistry registry) {
        this.registry = registry;
        addAttributes("uri", "impl", "wsdlElement", "wsdlLocation", "requires", "policySets", "name", "retries");
    }

    @SuppressWarnings({"unchecked"})
    public WsBinding load(XMLStreamReader reader, IntrospectionContext context) throws XMLStreamException {
        Location location = reader.getLocation();
        String wsdlElement = reader.getAttributeValue(null, "wsdlElement");
        String wsdlLocation = reader.getAttributeValue(WSDL_NS, "wsdlLocation");
        if (wsdlLocation == null) {
            wsdlLocation = reader.getAttributeValue(WSDL_2004_NS, "wsdlLocation");
        }

        int retries = parseRetries(reader, context);

        String bindingName = reader.getAttributeValue(null, "name");

        URI targetUri = parseTargetUri(reader, context);

        WsBinding binding = new WsBinding(bindingName, targetUri, wsdlLocation, wsdlElement, retries);

        validateAttributes(reader, context, binding);

        if (wsdlLocation != null && wsdlElement == null) {
            MissingAttribute error = new MissingAttribute("A wsdlLocation was specified but not a wsdlElement", location, binding);
            context.addError(error);
        }

        //Load optional sub elements config parameters
        while (true) {
            switch (reader.next()) {
                case START_ELEMENT:
                    Object elementValue = registry.load(reader, Object.class, context);
                    Location startAttribute = reader.getLocation();
                    if (elementValue instanceof BindingHandler) {
                        binding.addHandler((BindingHandler) elementValue);
                    } else if (elementValue instanceof Map) {
                        binding.setConfiguration((Map<String, String>) elementValue);
                    } else if (elementValue instanceof EndpointReference) {
                        EndpointReference endpointReference = (EndpointReference) elementValue;
                        if (targetUri != null) {
                            InvalidValue error = new InvalidValue("Cannot specify both a target URI and endpoint reference on a web services binding",
                                                                  startAttribute,
                                                                  binding);
                            context.addError(error);
                        } else {
                            binding.setTargetUri(endpointReference.getAddress());
                        }
                    }
                    break;
                case END_ELEMENT:
                    String name = reader.getName().getLocalPart();
                    if ("binding.ws".equals(name)) {
                        return binding;
                    }
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    // avoid infinite loop if end element not present
                    return binding;
            }
        }
    }

    private URI parseTargetUri(XMLStreamReader reader, IntrospectionContext context) {
        String uri = reader.getAttributeValue(null, "uri");
        URI targetUri = null;
        if (uri != null) {
            try {
                targetUri = new URI(uri);
            } catch (URISyntaxException ex) {
                Location location = reader.getLocation();
                InvalidValue failure = new InvalidValue("The web services binding URI is not a valid: " + uri, location);
                context.addError(failure);
            }
        }
        return targetUri;
    }

    private int parseRetries(XMLStreamReader reader, IntrospectionContext context) {
        String retries = reader.getAttributeValue(null, "retries");
        if (retries != null) {
            try {
                return Integer.parseInt(retries);
            } catch (NumberFormatException e) {
                Location location = reader.getLocation();
                InvalidValue error = new InvalidValue("The retries attribute must be a valid number", location);
                context.addError(error);
            }
        }
        return 0;
    }

}
