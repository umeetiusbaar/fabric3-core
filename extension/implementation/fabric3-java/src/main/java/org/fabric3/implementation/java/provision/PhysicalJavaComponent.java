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
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.implementation.java.provision;

import org.fabric3.implementation.pojo.provision.PhysicalPojoComponent;

/**
 * Metadata for provisioning a Java component.
 */
public class PhysicalJavaComponent extends PhysicalPojoComponent {
    private transient Object instance;

    public PhysicalJavaComponent() {
    }

    /**
     * Constructor used to provision component instances that are not created by the runtime.
     *
     * @param instance the instance
     */
    public PhysicalJavaComponent(Object instance) {
        this.instance = instance;
    }

    public Object getInstance() {
        return instance;
    }
}