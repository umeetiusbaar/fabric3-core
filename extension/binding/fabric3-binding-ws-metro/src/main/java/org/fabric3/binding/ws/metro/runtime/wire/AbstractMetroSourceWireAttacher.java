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
package org.fabric3.binding.ws.metro.runtime.wire;

import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.Handler;
import java.util.ArrayList;
import java.util.List;

import org.fabric3.binding.ws.metro.provision.MetroWireSourceDefinition;
import org.fabric3.binding.ws.metro.runtime.core.EndpointService;
import org.fabric3.binding.ws.metro.runtime.core.SOAPMessageHandlerAdapter;
import org.fabric3.spi.container.binding.handler.BindingHandler;
import org.fabric3.spi.container.binding.handler.BindingHandlerRegistry;
import org.fabric3.spi.container.builder.component.SourceWireAttacher;
import org.fabric3.spi.container.objectfactory.ObjectFactory;
import org.fabric3.spi.model.physical.PhysicalBindingHandlerDefinition;
import org.fabric3.spi.model.physical.PhysicalWireTargetDefinition;

/**
 * Base source wire attacher that provisions web service endpoints.
 */
public abstract class AbstractMetroSourceWireAttacher<T extends MetroWireSourceDefinition> implements SourceWireAttacher<T> {
    protected EndpointService endpointService;
    private BindingHandlerRegistry handlerRegistry;

    public AbstractMetroSourceWireAttacher(EndpointService endpointService, BindingHandlerRegistry handlerRegistry) {
        this.endpointService = endpointService;
        this.handlerRegistry = handlerRegistry;
    }

    public void detachObjectFactory(T source, PhysicalWireTargetDefinition target) {
    }

    public void attachObjectFactory(T source, ObjectFactory<?> objectFactory, PhysicalWireTargetDefinition target) {
        throw new UnsupportedOperationException();
    }

    protected List<Handler> createHandlers(MetroWireSourceDefinition source) {
        if (source.getHandlers().isEmpty() && !source.isBidirectional()) {
            return null;
        }
        List<Handler> handlers = new ArrayList<>();

        for (PhysicalBindingHandlerDefinition handlerDefinition : source.getHandlers()) {
            BindingHandler<SOAPMessage> handler = handlerRegistry.createHandler(SOAPMessage.class, handlerDefinition);
            SOAPMessageHandlerAdapter soapHandlerAdaptor = new SOAPMessageHandlerAdapter(handler);
            handlers.add(soapHandlerAdaptor);
        }
        return handlers;
    }

}