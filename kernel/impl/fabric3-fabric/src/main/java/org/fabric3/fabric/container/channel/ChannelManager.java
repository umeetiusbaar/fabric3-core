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
package org.fabric3.fabric.container.channel;

import javax.xml.namespace.QName;
import java.net.URI;

import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.spi.container.channel.Channel;
import org.fabric3.spi.model.physical.ChannelSide;

/**
 * Manages channels on a runtime.
 */
public interface ChannelManager {

    /**
     * Returns the channel or null if one does not exist.
     *
     * @param uri the channel URI
     * @param channelSide the channel side
     * @return the channel or null
     */
    Channel getChannel(URI uri, ChannelSide channelSide);

    /**
     * Returns the channel and increments its use count or null if one does not exist.
     *
     * @param uri the channel URI
     * @param channelSide the channel side
     * @return the channel or null
     */
    Channel getAndIncrementChannel(URI uri, ChannelSide channelSide);

    /**
     * Returns the channel and decrements its use count or null if one does not exist.
     *
     * @param uri the channel URI
     * @param channelSide the channel side
     * @return the channel or null
     */
    Channel getAndDecrementChannel(URI uri, ChannelSide channelSide);

    /**
     * Returns the use count for the channel or -1 if the channel is not registered.
     *
     * @param uri the channel uri
     * @param channelSide the channel side
     * @return the use count
     */
    int getCount(URI uri, ChannelSide channelSide);

    /**
     * Registers a channel.
     *
     * @param channel the channel
     * @throws Fabric3Exception if there is an error registering the channel
     */
    void register(Channel channel) throws Fabric3Exception;

    /**
     * Removes a channel for the given URI.
     *
     * @param uri         the uri
     * @param channelSide the channel side
     * @return the channel or null
     * @throws Fabric3Exception if there is an error removing the channel
     */
    Channel unregister(URI uri, ChannelSide channelSide) throws Fabric3Exception;

    /**
     * Starts channels contained in the given deployable composite.
     *
     * @param deployable the composite
     */
    void startContext(QName deployable);

    /**
     * Stops channels contained in the given deployable composite.
     *
     * @param deployable the composite
     */
    void stopContext(QName deployable);

}
