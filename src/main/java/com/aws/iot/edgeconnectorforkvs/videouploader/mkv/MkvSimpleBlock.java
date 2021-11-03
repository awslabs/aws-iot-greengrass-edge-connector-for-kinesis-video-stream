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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * This class store and manage MKV Simple Block element.
 */
@Slf4j
public class MkvSimpleBlock implements Comparable<MkvSimpleBlock> {

    // Relative timecode of simple block.  The absolute timecode would be this one adding to its cluster's timecode.
    @Getter
    private int relativeTimecode;

    // Track number.  One is usually for video, and two is usually for audio.
    @Getter
    private final long trackNumber;

    // EBML ID and Length field
    private final byte[] idAndSizeBytes;

    // EBML data field
    private final byte[] dataBytes;

    /**
     * Constructor of MKV simple block.
     *
     * @param relativeTimecode  The timecode of this simple block and its size is 2 bytes
     * @param trackNumber       The track number of this simple block
     * @param idAndSizeRawBytes The element ID and size field
     * @param dataBuffer        The data of this element
     */
    public MkvSimpleBlock(int relativeTimecode, long trackNumber,
                          ByteBuffer idAndSizeRawBytes, ByteBuffer dataBuffer) {
        this.relativeTimecode = relativeTimecode;
        this.trackNumber = trackNumber;

        idAndSizeRawBytes.rewind();
        idAndSizeBytes = new byte[idAndSizeRawBytes.remaining()];
        idAndSizeRawBytes.get(idAndSizeBytes);

        dataBuffer.rewind();
        dataBytes = new byte[dataBuffer.remaining()];
        dataBuffer.get(dataBytes);
    }

    /**
     * Adjust timecode of simple block.  This is needed when moving this simple block to other cluster.
     *
     * @param deltaTimecode The difference of new timecode.  This can be calculated by subtraction of 2 clusters.
     */
    public void updateTimecode(short deltaTimecode) {
        relativeTimecode += deltaTimecode;
        dataBytes[1] = (byte) ((relativeTimecode >> 8) & 0xFF);
        dataBytes[2] = (byte) (relativeTimecode & 0xFF);
    }

    /**
     * Write this MKV simple block to channel.
     *
     * @param outputChannel The output channel that we write to
     * @throws IOException It's thrown when something wrong in writing data to channel
     */
    public void writeToChannel(WritableByteChannel outputChannel) throws IOException {
        outputChannel.write(ByteBuffer.wrap(idAndSizeBytes));
        outputChannel.write(ByteBuffer.wrap(dataBytes));
    }

    /**
     * Compare two simple block by (1) their timecode, and (2) their track number.
     *
     * @param otherSimpleBlock The other simple block to be compared
     * @return 1 if current one is larger than the other, 0 if they are equal, -1 otherwise.
     */
    @Override
    public int compareTo(MkvSimpleBlock otherSimpleBlock) {
        if (relativeTimecode > otherSimpleBlock.getRelativeTimecode()) {
            return 1;
        } else if (relativeTimecode < otherSimpleBlock.getRelativeTimecode()) {
            return -1;
        } else if (trackNumber < otherSimpleBlock.getTrackNumber()) {
            return -1;
        } else if (trackNumber > otherSimpleBlock.getTrackNumber()) {
            return 1;
        }
        return 0;
    }

    /**
     * Compare two simple block by (1) their timecode, and (2) their track number.
     *
     * @param otherObj The other simple block to be compared
     * @return True if they are equal, false otherwise
     */
    @Override
    public boolean equals(Object otherObj) {
        if (otherObj == null) {
            return false;
        }
        if (!otherObj.getClass().equals(this.getClass())) {
            return false;
        }
        MkvSimpleBlock other = (MkvSimpleBlock) otherObj;
        return new EqualsBuilder()
                .append(relativeTimecode, other.getRelativeTimecode())
                .append(trackNumber, other.getTrackNumber())
                .isEquals();
    }

    /**
     * Return hash code of this simpleblock.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(relativeTimecode)
                .append(trackNumber)
                .toHashCode();
    }
}
