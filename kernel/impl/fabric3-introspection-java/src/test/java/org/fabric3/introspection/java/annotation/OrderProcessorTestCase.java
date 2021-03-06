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
package org.fabric3.introspection.java.annotation;

import junit.framework.TestCase;
import org.fabric3.api.annotation.wire.Order;
import org.fabric3.api.model.type.java.InjectingComponentType;
import org.fabric3.spi.introspection.DefaultIntrospectionContext;
import org.fabric3.spi.introspection.java.InvalidAnnotation;

/**
 *
 */
public class OrderProcessorTestCase extends TestCase {
    private OrderProcessor processor;
    private InjectingComponentType type;
    private DefaultIntrospectionContext context;

    public void testParseOrder() throws Exception {
        OrderAnnotated annotated = new OrderAnnotated();
        Order annotation = annotated.getClass().getAnnotation(Order.class);


        processor.visitType(annotation, annotated.getClass(), type, context);

        assertEquals(1, type.getOrder());
    }

    public void testInvalidKey() throws Exception {
        InvalidAnnotated annotated = new InvalidAnnotated();
        Order annotation = annotated.getClass().getAnnotation(Order.class);

        processor.visitType(annotation, annotated.getClass(), type, context);

        assertTrue(context.hasErrors());
        assertTrue(context.getErrors().get(0) instanceof InvalidAnnotation);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        processor = new OrderProcessor();
        type = new InjectingComponentType();
        context = new DefaultIntrospectionContext();
    }

    @Order(1)
    private static class OrderAnnotated {

    }

    @Order()
    private static class InvalidAnnotated {

    }
}
