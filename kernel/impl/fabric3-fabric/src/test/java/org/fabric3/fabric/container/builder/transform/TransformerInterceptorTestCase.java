/*
 * Fabric3
 * Copyright (c) 2009-2013 Metaform Systems
 *
 * Fabric3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version, with the
 * following exception:
 *
 * Linking this software statically or dynamically with other
 * modules is making a combined work based on this software.
 * Thus, the terms and conditions of the GNU General Public
 * License cover the whole combination.
 *
 * As a special exception, the copyright holders of this software
 * give you permission to link this software with independent
 * modules to produce an executable, regardless of the license
 * terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided
 * that you also meet, for each linked independent module, the
 * terms and conditions of the license of that module. An
 * independent module is a module which is not derived from or
 * based on this software. If you modify this software, you may
 * extend this exception to your version of the software, but
 * you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version.
 *
 * Fabric3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the
 * GNU General Public License along with Fabric3.
 * If not, see <http://www.gnu.org/licenses/>.
*/
package org.fabric3.fabric.container.builder.transform;

import junit.framework.TestCase;
import org.easymock.EasyMock;

import org.fabric3.fabric.container.interceptor.TransformerInterceptor;
import org.fabric3.spi.container.invocation.Message;
import org.fabric3.spi.container.invocation.MessageImpl;
import org.fabric3.spi.transform.Transformer;
import org.fabric3.spi.container.wire.Interceptor;

/**
 *
 */
public class TransformerInterceptorTestCase extends TestCase {

    @SuppressWarnings({"unchecked"})
    public void testInvoke() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        MessageImpl message = new MessageImpl();
        message.setBody(new Object[]{"test"});

        Transformer<Object, Object> in = EasyMock.createMock(Transformer.class);
        Transformer<Object, Object> out = EasyMock.createMock(Transformer.class);
        EasyMock.expect(in.transform(EasyMock.notNull(), EasyMock.eq(loader))).andReturn("in");
        EasyMock.expect(out.transform(EasyMock.notNull(), EasyMock.eq(loader))).andReturn("out");
        Interceptor next = EasyMock.createMock(Interceptor.class);
        EasyMock.expect(next.invoke(EasyMock.isA(Message.class))).andReturn(message);
        EasyMock.replay(in, out, next);

        TransformerInterceptor interceptor = new TransformerInterceptor(in, out, loader, loader);
        interceptor.setNext(next);
        interceptor.invoke(message);

        EasyMock.verify(in, out, next);
    }
}