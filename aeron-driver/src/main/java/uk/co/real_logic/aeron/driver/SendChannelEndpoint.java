/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron.driver;

import uk.co.real_logic.aeron.common.collections.BiInt2ObjectMap;
import uk.co.real_logic.aeron.common.concurrent.AtomicBuffer;
import uk.co.real_logic.aeron.common.event.EventLogger;
import uk.co.real_logic.aeron.common.protocol.NakFlyweight;
import uk.co.real_logic.aeron.common.protocol.StatusMessageFlyweight;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Aggregator of multiple {@link DriverPublication}s onto a single transport session for processing of control frames.
 */
public class SendChannelEndpoint implements AutoCloseable
{
    private final UdpTransport transport;
    private final UdpChannel udpChannel;
    private final BiInt2ObjectMap<PublicationAssembly> assemblyByStreamAndSessionIdMap = new BiInt2ObjectMap<>();
    private final SystemCounters systemCounters;

    public SendChannelEndpoint(final UdpChannel udpChannel,
                               final NioSelector nioSelector,
                               final EventLogger logger,
                               final LossGenerator lossGenerator,
                               final SystemCounters systemCounters)
    {
        this.systemCounters = systemCounters;
        this.transport = new UdpTransport(udpChannel, this::onStatusMessageFrame, this::onNakFrame, logger, lossGenerator);
        this.transport.registerForRead(nioSelector);
        this.udpChannel = udpChannel;
    }

    public int send(final ByteBuffer buffer) throws Exception
    {
        return transport.sendTo(buffer, udpChannel.remoteData());
    }

    public int sendTo(final ByteBuffer buffer, final InetSocketAddress address)
    {
        return transport.sendTo(buffer, address);
    }

    public void close()
    {
        transport.close();
    }

    public UdpChannel udpChannel()
    {
        return udpChannel;
    }

    public DriverPublication getPublication(final int sessionId, final int streamId)
    {
        final PublicationAssembly assembly = assemblyByStreamAndSessionIdMap.get(sessionId, streamId);

        if (null != assembly)
        {
            return assembly.publication;
        }

        return null;
    }

    public void addPublication(final DriverPublication publication,
                               final RetransmitHandler retransmitHandler,
                               final SenderControlStrategy senderControlStrategy)
    {
        assemblyByStreamAndSessionIdMap.put(
            publication.sessionId(), publication.streamId(),
            new PublicationAssembly(publication, retransmitHandler, senderControlStrategy));
    }

    public DriverPublication removePublication(final int sessionId, final int streamId)
    {
        final PublicationAssembly assembly = assemblyByStreamAndSessionIdMap.remove(sessionId, streamId);

        if (null != assembly)
        {
            assembly.retransmitHandler.close();
            return assembly.publication;
        }

        return null;
    }

    public int sessionCount()
    {
        return assemblyByStreamAndSessionIdMap.size();
    }

    private void onStatusMessageFrame(final StatusMessageFlyweight header,
                                      final AtomicBuffer buffer,
                                      final int length,
                                      final InetSocketAddress srcAddress)
    {
        final PublicationAssembly assembly = assemblyByStreamAndSessionIdMap.get(header.sessionId(), header.streamId());

        if (null != assembly)
        {
            final long limit =
                assembly.flowControlStrategy.onStatusMessage(
                    header.termId(), header.highestContiguousTermOffset(), header.receiverWindowSize(), srcAddress);

            assembly.publication.updatePositionLimitFromStatusMessage(limit);
            systemCounters.statusMessagesReceived().orderedIncrement();
        }
    }

    private void onNakFrame(final NakFlyweight nak,
                            final AtomicBuffer buffer,
                            final int length,
                            final InetSocketAddress srcAddress)
    {
        final PublicationAssembly assembly = assemblyByStreamAndSessionIdMap.get(nak.sessionId(), nak.streamId());

        if (null != assembly)
        {
            assembly.retransmitHandler.onNak(nak.termId(), nak.termOffset(), nak.length());
            systemCounters.naksReceived().orderedIncrement();
        }
    }

    private static class PublicationAssembly
    {
        final DriverPublication publication;
        final RetransmitHandler retransmitHandler;
        final SenderControlStrategy flowControlStrategy;

        public PublicationAssembly(final DriverPublication publication,
                                   final RetransmitHandler retransmitHandler,
                                   final SenderControlStrategy flowControlStrategy)
        {
            this.publication = publication;
            this.retransmitHandler = retransmitHandler;
            this.flowControlStrategy = flowControlStrategy;
        }
    }
}
