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
package org.fabric3.databinding.json.transform;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.spi.transform.Transformer;

/**
 * Transforms a serialized XML String received as an input stream to a Java object using JSON.
 */
public class Stream2ObjectJsonTransformer implements Transformer<InputStream, Object> {
    private ObjectMapper mapper;
    private Class<?> type;

    public Stream2ObjectJsonTransformer(Class<?> type, ObjectMapper mapper) {
        this.type = type;
        this.mapper = mapper;
    }

    public Object transform(InputStream source, ClassLoader loader) throws Fabric3Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);

            JsonParser jp = mapper.getFactory().createParser(source);
            // do not to close the underlying stream after mapping
            jp.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            return mapper.readValue(jp, type);
        } catch (IOException e) {
            throw new Fabric3Exception(e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

}