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
package org.fabric3.fabric.node.nonmanaged;

import org.fabric3.spi.model.physical.PhysicalWireSource;

/**
 * Used to attach a wire to non-managed code.
 */
public class NonManagedWireSource extends PhysicalWireSource {
    private Class<?> interfaze;
    private transient Object proxy;

    public NonManagedWireSource(Class<?> interfaze) {
        this.interfaze = interfaze;
    }

    public Class<?> getInterface() {
        return interfaze;
    }

    public Object getProxy() {
        return proxy;
    }

    public void setProxy(Object proxy) {
        this.proxy = proxy;
    }
}
