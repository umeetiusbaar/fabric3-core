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
package org.fabric3.api.host.stream;

import java.io.InputStream;
import java.net.URL;

import org.fabric3.api.host.Fabric3Exception;

/**
 * Provides an input stream for reading the contents of an artifact.
 */
public interface Source {

    /**
     * Returns the system ID of the stream or null if not defined.
     *
     * @return the system ID of the stream or null if not defined
     */
    String getSystemId();

    /**
     * Returns the base location of the stream or null if not defined.
     *
     * @return the base location of the stream or null if not defined
     */
    URL getBaseLocation();

    /**
     * Returns an input stream for reading the contents of the artifact. Clients are responsible for closing the returned stream.
     *
     * @return an input stream
     * @throws Fabric3Exception if there is an error opening the stream
     */
    InputStream openStream() throws Fabric3Exception;

}
