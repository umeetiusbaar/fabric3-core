/*
 * Fabric3
 * Copyright (c) 2009-2011 Metaform Systems
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
package org.fabric3.host.runtime;

/**
 * Tracks default packages visible in the system classloader that must be masked from the runtime boot classloader. This is required when the runtime
 * uses a version of a Java API that is different from the one provided by the JVM.
 *
 * @version $Rev$ $Date$
 */
public final class HiddenPackages {
    private static final String[] PACKAGES = new String[]{
            "javax.xml.bind.",
            "javax.xml.ws.",
            "com.sun.xml.jaxws.",
            "com.sun.xml.messaging.",
            "com.sun.xml.registry.",
            "com.sun.xml.rpc.",
            "com.sun.xml.security.",
            "com.sun.xml.stream.",
            "com.sun.xml.ws.",
            "com.sun.xml.wss.",
            "com.sun.xml.xwss.",
            "com.sun.xml.bind.",
            "org.springframework."
    };

    public static String[] getPackages() {
        return PACKAGES;
    }

    private HiddenPackages() {
    }
}