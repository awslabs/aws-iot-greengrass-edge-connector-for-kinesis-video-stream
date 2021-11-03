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

package com.aws.iot.edgeconnectorforkvs.videouploader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TestUtil {
    private static final byte[] EBML_HEADER = new byte[]{
            (byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x94, (byte) 0x42, (byte) 0x82, (byte) 0x89,
            (byte) 0x6D, (byte) 0x61, (byte) 0x74, (byte) 0x72, (byte) 0x6F, (byte) 0x73, (byte) 0x6B, (byte) 0x61,
            (byte) 0x00, (byte) 0x42, (byte) 0x87, (byte) 0x81, (byte) 0x02, (byte) 0x42, (byte) 0x85, (byte) 0x81,
            (byte) 0x02
    };

    private static final byte[] SEGMENT_HEADER_WITHOUT_TRACKS = new byte[]{
            (byte) 0x18, (byte) 0x53, (byte) 0x80, (byte) 0x67, (byte) 0xFF, (byte) 0x15, (byte) 0x49, (byte) 0xA9,
            (byte) 0x66, (byte) 0xA6, (byte) 0x73, (byte) 0xA4, (byte) 0x90, (byte) 0x00, (byte) 0x01, (byte) 0x02,
            (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0A,
            (byte) 0x0B, (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F, (byte) 0x2A, (byte) 0xD7, (byte) 0xB1,
            (byte) 0x83, (byte) 0x0F, (byte) 0x42, (byte) 0x40, (byte) 0x4D, (byte) 0x80, (byte) 0x83, (byte) 0x41,
            (byte) 0x50, (byte) 0x50, (byte) 0x57, (byte) 0x41, (byte) 0x83, (byte) 0x41, (byte) 0x50, (byte) 0x50,
            (byte) 0x16, (byte) 0x54, (byte) 0xAE, (byte) 0x6B
    };

    private static final byte[] TRACK_VIDEO = new byte[]{
            (byte) 0xAE, (byte) 0xAE, (byte) 0xD7, (byte) 0x81, (byte) 0x01, (byte) 0x83, (byte) 0x81, (byte) 0x01,
            (byte) 0x73, (byte) 0xC5, (byte) 0x88, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x53, (byte) 0x6E, (byte) 0x81, (byte) 0x56, (byte) 0xE0,
            (byte) 0x86, (byte) 0xB0, (byte) 0x81, (byte) 0xF0, (byte) 0xBA, (byte) 0x81, (byte) 0xA0, (byte) 0x86,
            (byte) 0x8F, (byte) 0x56, (byte) 0x5F, (byte) 0x4D, (byte) 0x50, (byte) 0x45, (byte) 0x47, (byte) 0x34,
            (byte) 0x2F, (byte) 0x49, (byte) 0x53, (byte) 0x4F, (byte) 0x2F, (byte) 0x41, (byte) 0x56, (byte) 0x43
    };

    private static final byte[] TRACK_AUDIO = new byte[]{
            (byte) 0xAE, (byte) 0xAB, (byte) 0xD7, (byte) 0x81, (byte) 0x02, (byte) 0x83, (byte) 0x81, (byte) 0x02,
            (byte) 0x73, (byte) 0xC5, (byte) 0x88, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x53, (byte) 0x6E, (byte) 0x81, (byte) 0x41, (byte) 0xE1,
            (byte) 0x8D, (byte) 0xB5, (byte) 0x88, (byte) 0x40, (byte) 0xC7, (byte) 0x70, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x9F, (byte) 0x81, (byte) 0x02, (byte) 0x86, (byte) 0x85,
            (byte) 0x41, (byte) 0x5F, (byte) 0x41, (byte) 0x41, (byte) 0x43
    };

    private static final byte[] TAG_HEADER = new byte[]{
            (byte) 0x12, (byte) 0x54, (byte) 0xC3, (byte) 0x67, (byte) 0xFF, (byte) 0x73, (byte) 0x73, (byte) 0xFF,
            (byte) 0x67, (byte) 0xC8, (byte) 0xFF, (byte) 0x45, (byte) 0xA3, (byte) 0x81, (byte) 0x00
    };

    private static final byte[] CLUSTER_HEADER_WITHOUT_TIMECODE = new byte[]{
            (byte) 0x1F, (byte) 0x43, (byte) 0xB6, (byte) 0x75, (byte) 0xFF, (byte) 0xE7, (byte) 0x88, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    private static final byte[] SILENT_TRACKS = new byte[] {
            (byte) 0x58, (byte) 0x54, (byte) 0x84, (byte) 0x58, (byte) 0xD7, (byte) 0x81, (byte) 0x03
    };

    private static final byte[] SIMPLE_BLOCK_WITHOUT_TIMECODE_AND_TRACK_NUMBER = new byte[]{
            (byte) 0xA3, (byte) 0x85, (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00
    };

    private static final byte[] SEGMENT_HEADER_ONLY = new byte[]{
            (byte) 0x18, (byte) 0x53, (byte) 0x80, (byte) 0x67, (byte) 0xFF
    };

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    /**
     * Create a sample video stored in byte array.  It has one video track, and one audio track if {@code hasAudio} is
     * true.  It has one cluster with one video simple block at timecode 0, and one audio simple block at timecode 0 if
     * {@code hasAudio} is set.
     *
     * @param hasAudio Set to true for video and audio track, false for video only.
     * @return Sample video stored in byte array
     */
    public static byte[] createSampleVideo(boolean hasAudio) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(TestUtil.createTracksHeader(hasAudio));
            byteArrayOutputStream.write(TestUtil.createClusterHeader(0));
            byteArrayOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            if (hasAudio) {
                byteArrayOutputStream.write(TestUtil.createSimpleBlock((short) 0, 2));
            }
            byteArrayOutputStream.close();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException exception) {
            System.out.println("Unable to create sample video");
        }
        return null;
    }

    public static byte[] createTracksHeader(boolean hasAudio) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(EBML_HEADER);
        byteArrayOutputStream.write(SEGMENT_HEADER_WITHOUT_TRACKS);
        if (hasAudio) {
            byte traksSize = (byte) (0x80 | (TRACK_VIDEO.length + TRACK_AUDIO.length));
            byteArrayOutputStream.write(traksSize);
            byteArrayOutputStream.write(TRACK_VIDEO);
            byteArrayOutputStream.write(TRACK_AUDIO);
        } else {
            byte traksSize = (byte) (0x80 | TRACK_VIDEO.length);
            byteArrayOutputStream.write(traksSize);
            byteArrayOutputStream.write(TRACK_VIDEO);
        }
        byteArrayOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    public static byte[] createTagHeader() {
        return TAG_HEADER.clone();
    }

    public static byte[] createClusterHeader(long timecode) {
        ByteBuffer clusterHeader = ByteBuffer.wrap(CLUSTER_HEADER_WITHOUT_TIMECODE.clone());
        clusterHeader.putLong(7, timecode);
        clusterHeader.rewind();

        return clusterHeader.array();
    }

    public static byte[] createSilentTracks() {
        ByteBuffer silentTracks = ByteBuffer.wrap(SILENT_TRACKS.clone());
        silentTracks.rewind();

        return silentTracks.array();
    }

    public static byte[] createSimpleBlock(short timecode, int track) {
        ByteBuffer simpleBlock = ByteBuffer.wrap(SIMPLE_BLOCK_WITHOUT_TIMECODE_AND_TRACK_NUMBER.clone());
        simpleBlock.putShort(3, timecode);
        simpleBlock.put(2, (byte) (track | (byte) 0x80));
        simpleBlock.rewind();

        return simpleBlock.array();
    }

    public static byte[] createNoTracksHeader() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write(EBML_HEADER);
        byteArrayOutputStream.write(SEGMENT_HEADER_ONLY);
        byteArrayOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            buffer.append(HEX_ARRAY[b >>> 4]);
            buffer.append(HEX_ARRAY[b & 0x0F]);
            buffer.append(' ');
            if ((i + 1) % 8 == 0) {
                buffer.append("\r\n");
            }
        }
        buffer.append("\r\n");
        return buffer.toString();
    }
}
