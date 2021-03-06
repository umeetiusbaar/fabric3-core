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
package org.fabric3.introspection.java;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.fabric3.api.model.type.component.Component;
import org.fabric3.api.model.type.java.JavaImplementation;
import org.fabric3.spi.introspection.DefaultIntrospectionContext;
import org.fabric3.spi.introspection.IntrospectionContext;
import org.fabric3.spi.introspection.java.ImplementationProcessor;

/**
 *
 */
public class ComponentProcessorImplTestCase extends TestCase {

    private ComponentProcessorImpl processor;
    private DefaultIntrospectionContext context;
    private ImplementationProcessor<?> implementationProcessor;

    @SuppressWarnings("unchecked")
    public void testProcessDefinitionNoImplementation() throws Exception {
        Component definition = new Component("test");

        implementationProcessor.process(EasyMock.isA(Component.class), EasyMock.isA(Class.class), EasyMock.isA(IntrospectionContext.class));

        EasyMock.replay(implementationProcessor);

        processor.process(definition, TestComponent.class, context);

        EasyMock.verify(implementationProcessor);
    }

    @SuppressWarnings("unchecked")
    public void testProcessDefinition() throws Exception {
        Component definition = new Component("test");
        JavaImplementation implementation = new JavaImplementation();
        definition.setImplementation(implementation);

        implementationProcessor.process(EasyMock.isA(Component.class), EasyMock.isA(IntrospectionContext.class));

        EasyMock.replay(implementationProcessor);

        processor.process(definition, context);

        EasyMock.verify(implementationProcessor);
    }

    public void setUp() throws Exception {
        super.setUp();

        implementationProcessor = EasyMock.createMock(ImplementationProcessor.class);

        processor = new ComponentProcessorImpl();
        Map<String, ImplementationProcessor<?>> map = Collections.<String, ImplementationProcessor<?>>singletonMap("java", implementationProcessor);
        processor.setImplementationProcessors(map);

        ClassLoader classLoader = getClass().getClassLoader();
        context = new DefaultIntrospectionContext(URI.create("test"), classLoader);
    }

    @org.fabric3.api.annotation.model.Component
    private class TestComponent {

    }
}
