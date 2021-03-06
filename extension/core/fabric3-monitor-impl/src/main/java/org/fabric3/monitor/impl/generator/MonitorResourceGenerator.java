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
package org.fabric3.monitor.impl.generator;

import java.util.Map;

import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.monitor.spi.destination.MonitorDestinationGenerator;
import org.fabric3.monitor.spi.model.physical.PhysicalMonitor;
import org.fabric3.monitor.spi.model.physical.PhysicalMonitorDestination;
import org.fabric3.monitor.spi.model.type.MonitorDestinationDefinition;
import org.fabric3.monitor.spi.model.type.MonitorResource;
import org.fabric3.spi.domain.generator.ResourceGenerator;
import org.fabric3.spi.model.instance.LogicalResource;
import org.oasisopen.sca.annotation.EagerInit;
import org.oasisopen.sca.annotation.Reference;

/**
 * Generates a {@link PhysicalMonitor} from a {@link MonitorResource}.
 */
@EagerInit
public class MonitorResourceGenerator implements ResourceGenerator<MonitorResource> {
    private Map<Class<?>, MonitorDestinationGenerator<?>> destinationGenerators;

    @Reference
    public void setDestinationGenerators(Map<Class<?>, MonitorDestinationGenerator<?>> destinationGenerators) {
        this.destinationGenerators = destinationGenerators;
    }

    @SuppressWarnings("unchecked")
    public PhysicalMonitor generateResource(LogicalResource<MonitorResource> logicalResource) throws Fabric3Exception {
        MonitorResource resourceDefinition = logicalResource.getDefinition();

        PhysicalMonitor physicalMonitor = new PhysicalMonitor(resourceDefinition.getName());

        MonitorDestinationDefinition destinationDefinition =  resourceDefinition.getDestinationDefinition();
        MonitorDestinationGenerator generator = getDestinationGenerator(destinationDefinition);
        PhysicalMonitorDestination destination = generator.generateResource(destinationDefinition);
        physicalMonitor.setDestination(destination);
        return physicalMonitor;
    }

    private MonitorDestinationGenerator getDestinationGenerator(MonitorDestinationDefinition definition) throws Fabric3Exception {
        MonitorDestinationGenerator generator = destinationGenerators.get(definition.getClass());
        if (generator == null) {
            throw new Fabric3Exception("Unknown monitor destination type: " + definition.getClass());
        }
        return generator;
    }

}

