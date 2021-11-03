/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.aws.iot.edgeconnectorforkvs.videouploader.mkv;

import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class store and manage MKV Cluster element.
 */
@Slf4j
public class MkvCluster {

    @Getter
    private long absoluteTimecode = 0;

    // ID and Length field of the cluster
    private final byte[] idAndSizeBytes;

    // True if simple blocks are in time ascending order
    private boolean isSimpleBlocksSorted;

    // A list that holds all simple blocks (which may out of order)
    private final List<MkvSimpleBlock> simpleBlocks = new ArrayList<>();

    /**
     * Construct a MKV Cluster.
     *
     * @param idAndSizeRawBytes The MKV Cluster header include id and size
     */
    public MkvCluster(ByteBuffer idAndSizeRawBytes) {
        idAndSizeRawBytes.rewind();
        idAndSizeBytes = new byte[idAndSizeRawBytes.remaining()];
        try {
            idAndSizeRawBytes.get(idAndSizeBytes);
        } catch (BufferUnderflowException ex) {
            log.error("Buffer Underflow Exception: " + ex.getMessage());
        }

        isSimpleBlocksSorted = false;
    }

    /**
     * Write cluster to channel, it includes cluster's header, timecode, and simple blocks.
     *
     * @param outputChannel The output channel
     * @throws IOException It's thrown when it failed to write to the output channel
     */
    public void writeToChannel(WritableByteChannel outputChannel) throws IOException {
        if (!simpleBlocks.isEmpty()) {
            sort();
            writeHeaderToChannel(outputChannel);
            writeTimecodeToChannel(outputChannel);
            for (MkvSimpleBlock simpleBlock : simpleBlocks) {
                simpleBlock.writeToChannel(outputChannel);
            }
        }
    }

    /**
     * Check if there is any simple blocks in this cluster.
     *
     * @return True if there is no simple block in this cluster, false otherwise
     */
    public boolean isEmpty() {
        return simpleBlocks.isEmpty();
    }

    /**
     * Set new absolute timecode, and adjust timecode of its simple blocks.
     *
     * @param newAbsoluteTimecode The new absolute timecode
     */
    public void setAbsoluteTimecode(long newAbsoluteTimecode) {
        absoluteTimecode = newAbsoluteTimecode;
    }

    /**
     * Add a new simple block to this cluster.
     *
     * @param simpleBlock The simple block to be added
     */
    public void addSimpleBlock(MkvSimpleBlock simpleBlock) {
        log.debug("add simple block with timecode: " + simpleBlock.getRelativeTimecode());
        simpleBlocks.add(simpleBlock);
        isSimpleBlocksSorted = false;
    }

    /**
     * Sort simple blocks in time ascending order.
     */
    public void sort() {
        if (!isSimpleBlocksSorted) {
            Collections.sort(simpleBlocks);
            isSimpleBlocksSorted = true;
        }
    }

    /**
     * Get latest absolute timecode of simple blocks.
     *
     * @return The latest absolute timecode of simple blocks if simple blocks are not empty, cluster's timecode
     * otherwise
     */
    public long getLatestSimpleBlockTimecode() {
        sort();
        if (simpleBlocks.isEmpty()) {
            return absoluteTimecode;
        } else {
            return absoluteTimecode + simpleBlocks.get(simpleBlocks.size() - 1).getRelativeTimecode();
        }
    }

    /**
     * Get the count of simple blocks with specific track number in this cluster.
     *
     * @param trackNumber The track number
     * @return The count of simple blocks with specific track number
     */
    public int getSimpleBlockCountInTrack(long trackNumber) {
        int count = 0;
        for (MkvSimpleBlock simpleBlock : simpleBlocks) {
            if (simpleBlock.getTrackNumber() == trackNumber) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get the earliest absolute timecode of simple block in this cluster.
     *
     * @return The earliest absolute timecode of simple blocks if simple blocks are not empty, cluster's timecode
     * otherwise
     */
    public long getEarliestSimpleBlockTimecode() {
        sort();
        if (simpleBlocks.isEmpty()) {
            return absoluteTimecode;
        } else {
            return absoluteTimecode + simpleBlocks.get(0).getRelativeTimecode();
        }
    }

    /**
     * Remove the latest simple block.  It's used when we move this simple block to another cluster
     *
     * @return The removed simple block.
     */
    public MkvSimpleBlock removeLatestSimpleBlock() {
        sort();
        if (simpleBlocks.isEmpty()) {
            return null;
        } else {
            final int lastIndex = simpleBlocks.size() - 1;
            MkvSimpleBlock simpleBlock = simpleBlocks.get(lastIndex);
            simpleBlocks.remove(lastIndex);
            return simpleBlock;
        }
    }

    /**
     * Get expected next timecode.  It's a dummy guess by add one to latest timecode
     *
     * @return The expected timecode
     */
    public long getExpectedNextTimeCode() {
        return getLatestSimpleBlockTimecode() + 1;
    }

    private void writeHeaderToChannel(WritableByteChannel outputChannel) throws IOException {
        outputChannel.write(ByteBuffer.wrap(idAndSizeBytes));
    }

    private void writeTimecodeToChannel(WritableByteChannel outputChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        try {
            buffer.put(0, (byte) MkvTypeInfos.TIMECODE.getId());
            buffer.put(1, (byte) 0x88);
            buffer.putLong(2, absoluteTimecode);
            buffer.rewind();

            outputChannel.write(buffer);
        } catch (IndexOutOfBoundsException ex) {
            log.error("Index Out of Bounds Exception: " + ex.getMessage());
        }
    }

    @Override
    public String toString() {
        StringBuilder clusterInfo = new StringBuilder("cluster timecode: " + absoluteTimecode);
        if (!simpleBlocks.isEmpty()) {
            clusterInfo.append(", simple blocks timecode:");
            for (MkvSimpleBlock simpleBlock : simpleBlocks) {
                clusterInfo.append(" ")
                        .append("(" + simpleBlock.getTrackNumber() + ")")
                        .append(simpleBlock.getRelativeTimecode());
            }
        }
        return clusterInfo.toString();
    }
}
