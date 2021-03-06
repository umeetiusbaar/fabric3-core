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
package org.fabric3.introspection.java.annotation;

import org.fabric3.api.model.type.java.InjectingComponentType;
import org.fabric3.spi.introspection.IntrospectionContext;
import org.fabric3.spi.introspection.java.annotation.AbstractAnnotationProcessor;
import org.oasisopen.sca.annotation.Scope;
import static org.fabric3.api.model.type.component.Scope.COMPOSITE;
import static org.fabric3.api.model.type.component.Scope.DOMAIN;
import static org.fabric3.api.model.type.component.Scope.STATELESS;

/**
 *
 */
public class OASISScopeProcessor extends AbstractAnnotationProcessor<Scope> {

    public OASISScopeProcessor() {
        super(Scope.class);
    }

    public void visitType(Scope annotation, Class<?> type, InjectingComponentType componentType, IntrospectionContext context) {
        String scopeName = annotation.value();
        if (!COMPOSITE.getScope().equals(scopeName) && !STATELESS.getScope().equals(scopeName) && !DOMAIN.getScope().equals(scopeName)) {
            InvalidScope failure = new InvalidScope(type, scopeName, componentType);
            context.addError(failure);
            return;
        }
        org.fabric3.api.model.type.component.Scope scope = org.fabric3.api.model.type.component.Scope.getScope(scopeName);
        componentType.setScope(scope);
    }
}