/*
 * Fabric3
 * Copyright (c) 2009-2013 Metaform Systems
 *
 * Fabric3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version, with the
 * following exception:
 *
 * Linking this software statically or dynamically with other
 * modules is making a combined work based on this software.
 * Thus, the terms and conditions of the GNU General Public
 * License cover the whole combination.
 *
 * As a special exception, the copyright holders of this software
 * give you permission to link this software with independent
 * modules to produce an executable, regardless of the license
 * terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided
 * that you also meet, for each linked independent module, the
 * terms and conditions of the license of that module. An
 * independent module is a module which is not derived from or
 * based on this software. If you modify this software, you may
 * extend this exception to your version of the software, but
 * you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version.
 *
 * Fabric3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the
 * GNU General Public License along with Fabric3.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 * ----------------------------------------------------
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 *
 */
package org.fabric3.binding.jms.generator;

import java.net.URI;
import java.util.List;

import org.fabric3.api.binding.jms.model.DeliveryMode;
import org.fabric3.api.binding.jms.model.DestinationType;
import org.fabric3.api.binding.jms.model.JmsBindingDefinition;
import org.fabric3.api.binding.jms.model.JmsBindingMetadata;
import org.fabric3.api.binding.jms.model.TransactionType;
import org.fabric3.api.model.type.contract.DataType;
import org.fabric3.binding.jms.spi.generator.JmsResourceProvisioner;
import org.fabric3.binding.jms.spi.provision.JmsChannelBindingDefinition;
import org.fabric3.binding.jms.spi.provision.JmsConnectionSourceDefinition;
import org.fabric3.binding.jms.spi.provision.JmsConnectionTargetDefinition;
import org.fabric3.spi.domain.generator.GenerationException;
import org.fabric3.spi.domain.generator.channel.ConnectionBindingGenerator;
import org.fabric3.spi.model.instance.LogicalBinding;
import org.fabric3.spi.model.instance.LogicalChannel;
import org.fabric3.spi.model.instance.LogicalConsumer;
import org.fabric3.spi.model.instance.LogicalProducer;
import org.fabric3.spi.model.physical.ChannelDeliveryType;
import org.fabric3.spi.model.physical.PhysicalChannelBindingDefinition;
import org.fabric3.spi.model.physical.PhysicalConnectionSourceDefinition;
import org.fabric3.spi.model.physical.PhysicalConnectionTargetDefinition;
import org.fabric3.spi.model.physical.PhysicalDataTypes;
import org.oasisopen.sca.annotation.EagerInit;
import org.oasisopen.sca.annotation.Reference;
import static org.fabric3.spi.model.physical.ChannelConstants.DURABLE_INTENT;
import static org.fabric3.spi.model.physical.ChannelConstants.NON_PERSISTENT_INTENT;

/**
 * Connection binding generator that creates source and target definitions for bound channels, producers, and consumers.
 */
@EagerInit
public class JmsConnectionBindingGenerator implements ConnectionBindingGenerator<JmsBindingDefinition> {
    private static final String JAXB = "JAXB";

    // optional provisioner for host runtimes to receive callbacks
    private JmsResourceProvisioner provisioner;

    @Reference(required = false)
    public void setProvisioner(JmsResourceProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    public PhysicalConnectionSourceDefinition generateConnectionSource(LogicalConsumer consumer,
                                                                       LogicalBinding<JmsBindingDefinition> binding,
                                                                       ChannelDeliveryType deliveryType) throws GenerationException {
        JmsBindingMetadata metadata = binding.getDefinition().getJmsMetadata().snapshot();

        generateIntents(binding, metadata);

        JmsGeneratorHelper.generateDefaultFactoryConfiguration(metadata.getConnectionFactory(), TransactionType.NONE);
        URI uri = consumer.getUri();

        // set the client id specifier
        if (metadata.getSubscriptionId() == null && metadata.isDurable()) {
            metadata.setSubscriptionId(JmsGeneratorHelper.getSubscriptionId(uri));
        }
        String specifier = metadata.getSubscriptionId();
        metadata.setSubscriptionId(specifier);

        metadata.getDestination().setType(DestinationType.TOPIC);  // only use topics for channels
        DataType type = isJAXB(consumer.getDefinition().getTypes()) ? PhysicalDataTypes.JAXB : PhysicalDataTypes.JAVA_TYPE;
        JmsConnectionSourceDefinition definition = new JmsConnectionSourceDefinition(uri, metadata, type);
        if (provisioner != null) {
            provisioner.generateConnectionSource(definition);
        }
        return definition;
    }

    public PhysicalConnectionTargetDefinition generateConnectionTarget(LogicalProducer producer,
                                                                       LogicalBinding<JmsBindingDefinition> binding,
                                                                       ChannelDeliveryType deliveryType) throws GenerationException {
        URI uri = binding.getDefinition().getTargetUri();
        JmsBindingMetadata metadata = binding.getDefinition().getJmsMetadata().snapshot();

        generateIntents(binding, metadata);

        JmsGeneratorHelper.generateDefaultFactoryConfiguration(metadata.getConnectionFactory(), TransactionType.NONE);

        DataType type = isJAXB(producer.getStreamOperation().getDefinition().getInputTypes()) ? PhysicalDataTypes.JAXB : PhysicalDataTypes.JAVA_TYPE;

        JmsConnectionTargetDefinition definition = new JmsConnectionTargetDefinition(uri, metadata, type);
        if (provisioner != null) {
            provisioner.generateConnectionTarget(definition);
        }
        return definition;
    }

    public PhysicalChannelBindingDefinition generateChannelBinding(LogicalBinding<JmsBindingDefinition> binding, ChannelDeliveryType deliveryType)
            throws GenerationException {
        // a binding definition needs to be created even though it is not used so the channel is treated as bound (e.g. its implementation will be sync)
        return new JmsChannelBindingDefinition();
    }

    /**
     * Generates intent metadata
     *
     * @param binding  the binding
     * @param metadata the JSM metadata
     */
    private void generateIntents(LogicalBinding<JmsBindingDefinition> binding, JmsBindingMetadata metadata) {
        LogicalChannel parent = (LogicalChannel) binding.getParent();
        if (binding.getDefinition().getIntents().contains(DURABLE_INTENT) || parent.getDefinition().getIntents().contains(DURABLE_INTENT)) {
            metadata.setDurable(true);
        }
        if (binding.getDefinition().getIntents().contains(NON_PERSISTENT_INTENT) || parent.getDefinition().getIntents().contains(NON_PERSISTENT_INTENT)) {
            metadata.getHeaders().setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        }

    }

    private boolean isJAXB(List<DataType> eventTypes) {
        for (DataType eventType : eventTypes) {
            if (JAXB.equals(eventType.getDatabinding())) {
                return true;
            }
        }
        return false;
    }

}