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
package org.fabric3.contribution.processor;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.fabric3.api.host.failure.ValidationFailure;

/**
 *
 */
public class InvalidProviderMethod extends ValidationFailure {
    private String message;

    public InvalidProviderMethod(String message) {
        this.message = message;
    }

    public InvalidProviderMethod(String message, Throwable e) {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        e.printStackTrace(pw);
        this.message = message + ":\n" + writer.toString();
    }

    public String getMessage() {
        return message;
    }
}
