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
package org.fabric3.implementation.web.introspection;

import org.fabric3.implementation.web.model.WebImplementation;
import org.fabric3.spi.contribution.Contribution;
import org.fabric3.spi.contribution.Resource;
import org.fabric3.spi.contribution.ResourceElement;
import org.fabric3.spi.contribution.ResourceState;

/**
 *
 */
public class IndexHelper {

    private IndexHelper() {
    }

    /**
     * Creates an index entry in the given contribution for the web implementation.
     *
     * Only one web implementation can exist per web contribution.
     *
     * @param implementation the implementation
     * @param contribution   the contribution
     */
    public static void indexImplementation(WebImplementation implementation, Contribution contribution) {
        WebImplementationSymbol symbol = new WebImplementationSymbol();
        ResourceElement<WebImplementationSymbol, WebImplementation> element = new ResourceElement<>(symbol, implementation);
        Resource resource = new Resource(contribution, null, "webimpl");
        resource.addResourceElement(element);
        contribution.addResource(resource);
        resource.setState(ResourceState.PROCESSED);
    }
}
