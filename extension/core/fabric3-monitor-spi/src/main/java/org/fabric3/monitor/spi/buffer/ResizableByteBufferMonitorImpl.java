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
package org.fabric3.monitor.spi.buffer;

/**
 * Reports a buffer resize once.
 */
public class ResizableByteBufferMonitorImpl implements ResizableByteBufferMonitor {
    private boolean fired;

    public ResizableByteBufferMonitorImpl() {
    }

    public void bufferResize() {
        if (fired) {
            return;
        }
        System.err.println("WARNING: Initial capacity for the monitor buffer was too small and forced a resize. Increase the buffer capacity in "
                           + "systemConfig/monitor/@capacity for optimal performance.");
        fired = true;
    }
}

