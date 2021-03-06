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
package org.fabric3.jpa.override;

import java.net.URI;
import java.util.Collections;

import junit.framework.TestCase;
import org.fabric3.jpa.api.PersistenceOverrides;
import org.fabric3.spi.contribution.Contribution;

/**
 *
 */
public class OverrideRegistryImplTestCase extends TestCase {
    private static final URI CONTRIBUTION_URI = URI.create("test");
    private OverrideRegistryImpl registry = new OverrideRegistryImpl();

    public void testRegisterUnregister() throws Exception {
        PersistenceOverrides overrides = new PersistenceOverrides("unit", Collections.<String, String>emptyMap());
        registry.register(CONTRIBUTION_URI, overrides);
        assertEquals(overrides, registry.resolve("unit"));
        Contribution contribution = new Contribution(CONTRIBUTION_URI);
        registry.onUninstall(contribution);
        assertNull(registry.resolve("unit"));
        registry.register(CONTRIBUTION_URI, overrides);
    }


    public void testDuplicateRegister() throws Exception {
        PersistenceOverrides overrides = new PersistenceOverrides("unit", Collections.<String, String>emptyMap());
        registry.register(CONTRIBUTION_URI, overrides);
        try {
            registry.register(CONTRIBUTION_URI, overrides);
            fail();
        } catch (DuplicateOverridesException e) {
            // expected
        }
    }

}