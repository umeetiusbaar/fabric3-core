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
package org.fabric3.monitor.proxy;

import org.fabric3.host.monitor.MonitorCreationException;
import org.fabric3.host.monitor.MonitorProxyService;
import org.fabric3.host.monitor.MonitorProxyServiceExtension;
import org.fabric3.host.monitor.Monitorable;
import org.fabric3.spi.monitor.MonitorLocator;
import org.fabric3.spi.monitor.MonitorProxy;
import org.oasisopen.sca.annotation.Reference;

/**
 * Implementation that delegates to a default extension, which can be overridden via a wired extension service.
 */
public class MonitorProxyServiceImpl implements MonitorProxyService {
    private MonitorProxyServiceExtension extension;

    public MonitorProxyServiceImpl(JDKMonitorProxyService extension) {
        this.extension = extension;
        try {
            MonitorLocator.setMonitorProxy(extension.createMonitor(MonitorProxy.class));
        } catch (MonitorCreationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Allows the default extension to overridden.
     *
     * @param extension the overriding extension
     */
    @Reference(required = false)
    public void setExtension(MonitorProxyServiceExtension extension) {
        this.extension = extension;
    }

    public <T> T createMonitor(Class<T> type) throws MonitorCreationException {
        return extension.createMonitor(type);
    }

    public <T> T createMonitor(Class<T> type, Monitorable monitorable, String destination) throws MonitorCreationException {
        return extension.createMonitor(type, monitorable, destination);
    }
}
