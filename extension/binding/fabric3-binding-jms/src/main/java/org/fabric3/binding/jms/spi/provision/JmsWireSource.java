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
package org.fabric3.binding.jms.spi.provision;

import java.net.URI;
import java.util.List;

import org.fabric3.api.binding.jms.model.JmsBindingMetadata;
import org.fabric3.api.model.type.contract.DataType;
import org.fabric3.spi.model.physical.PhysicalBindingHandler;
import org.fabric3.spi.model.physical.PhysicalWireSource;

/**
 * Generated metadata used for attaching a service endpoint to a JMS destination.
 */
public class JmsWireSource extends PhysicalWireSource {
    private JmsBindingMetadata metadata;
    private SessionType sessionType;
    private List<OperationPayloadTypes> payloadTypes;
    private List<PhysicalBindingHandler> handlers;

    /**
     * Constructor.
     *
     * @param uri             the service URI
     * @param metadata        metadata used to create a JMS message consumer.
     * @param payloadTypes    the JMS payload types keyed by operation name
     * @param sessionType the transaction type
     * @param handlers        binding handlers to be engaged for the service
     */
    public JmsWireSource(URI uri,
                         JmsBindingMetadata metadata,
                         List<OperationPayloadTypes> payloadTypes,
                         SessionType sessionType,
                         List<PhysicalBindingHandler> handlers) {
        this.metadata = metadata;
        this.sessionType = sessionType;
        this.payloadTypes = payloadTypes;
        this.handlers = handlers;
        setUri(uri);
    }

    /**
     * Constructor that defines an alternative set of supported data types.
     *
     * @param uri             the service URI
     * @param metadata        metadata used to create a JMS message consumer.
     * @param payloadTypes    the JMS payload types keyed by operation name
     * @param sessionType the transaction type
     * @param handlers        binding handlers to be engaged for the service
     * @param types           the allowable datatypes. For example, this may be used to constrain a source type to string XML
     */
    public JmsWireSource(URI uri,
                         JmsBindingMetadata metadata,
                         List<OperationPayloadTypes> payloadTypes,
                         SessionType sessionType,
                         List<PhysicalBindingHandler> handlers,
                         DataType... types) {
        super(types);
        setUri(uri);
        this.metadata = metadata;
        this.sessionType = sessionType;
        this.payloadTypes = payloadTypes;
        this.handlers = handlers;
    }


    public JmsBindingMetadata getMetadata() {
        return metadata;
    }

    public List<OperationPayloadTypes> getPayloadTypes() {
        return payloadTypes;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public List<PhysicalBindingHandler> getHandlers() {
        return handlers;
    }
}
