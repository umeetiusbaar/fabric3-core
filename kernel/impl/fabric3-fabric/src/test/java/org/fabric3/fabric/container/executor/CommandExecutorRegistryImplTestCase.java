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
package org.fabric3.fabric.container.executor;

import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.fabric3.fabric.container.command.CommandExecutorRegistryImpl;
import org.fabric3.spi.container.command.Command;
import org.fabric3.spi.container.command.CommandExecutor;

/**
 *
 */
public class CommandExecutorRegistryImplTestCase extends TestCase {

    @SuppressWarnings({"unchecked"})
    public void testDispatch() throws Exception {

        CommandExecutorRegistryImpl registry = new CommandExecutorRegistryImpl();

        CommandExecutor<TestCommand> executor = EasyMock.createMock(CommandExecutor.class);
        executor.execute(EasyMock.isA(TestCommand.class));
        EasyMock.replay(executor);

        registry.register(TestCommand.class, executor);
        registry.execute(new TestCommand());

        EasyMock.verify(executor);
    }

    private class TestCommand implements Command {
    }

}
