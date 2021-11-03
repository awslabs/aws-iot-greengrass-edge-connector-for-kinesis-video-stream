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

import com.aws.iot.edgeconnectorforkvs.videouploader.TestUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

public class MkvClusterTest {

    MkvCluster mkvCluster;

    @Test
    public void writeToChannel_emptySimpleBlocks_zeroOutput() {
        // Setup test
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final WritableByteChannel outputChannel = Channels.newChannel(outputStream);;

        // Do test
        mkvCluster = new MkvCluster(ByteBuffer.wrap(TestUtil.createClusterHeader(10)));
        mkvCluster.setAbsoluteTimecode(10);
        Assertions.assertDoesNotThrow(() -> {
            mkvCluster.writeToChannel(outputChannel);
            outputChannel.close();
        });

        int outputLength = outputStream.toByteArray().length;
        Assertions.assertEquals(0, outputLength);

        Assertions.assertEquals(mkvCluster.getLatestSimpleBlockTimecode(), 10);
        Assertions.assertEquals(mkvCluster.getEarliestSimpleBlockTimecode(), 10);
        Assertions.assertNull(mkvCluster.removeLatestSimpleBlock());
        Assertions.assertEquals(11, mkvCluster.getExpectedNextTimeCode());
    }

    @Test
    public void getLatestSimpleBlockTimecode_emptySimpleBlocks_sameTimecodeAsCluster() {
        // Do test
        mkvCluster = new MkvCluster(ByteBuffer.wrap(TestUtil.createClusterHeader(10)));
        mkvCluster.setAbsoluteTimecode(10);

        Assertions.assertEquals(mkvCluster.getLatestSimpleBlockTimecode(), 10);
    }

    @Test
    public void getEarliestSimpleBlockTimecode_emptySimpleBlocks_sameTimecodeAsCluster() {
        // Do test
        mkvCluster = new MkvCluster(ByteBuffer.wrap(TestUtil.createClusterHeader(10)));
        mkvCluster.setAbsoluteTimecode(10);

        Assertions.assertEquals(mkvCluster.getEarliestSimpleBlockTimecode(), 10);
    }
    public static byte[] SIMPLE_BLOCK_WITHOUT_TIMECODE_AND_TRACK_NUMBER = new byte[]{
            (byte) 0xA3, (byte) 0x85, (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00
    };
    @Test
    public void getEarliestSimpleBlockTimecode_nonEmptySimpleBlocks_earliestTimecode() {
        ByteBuffer idAndSizeRawBytes = ByteBuffer.wrap(new byte[] {
                (byte) 0xA3, (byte) 0x85
        });
        ByteBuffer dataBuffer = ByteBuffer.wrap(new byte[] {
                (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00
        });
        MkvSimpleBlock simpleBlock1 = new MkvSimpleBlock((short)10, 1, idAndSizeRawBytes, dataBuffer);
        MkvSimpleBlock simpleBlock2 = new MkvSimpleBlock((short)10, 2, idAndSizeRawBytes, dataBuffer);
        MkvSimpleBlock simpleBlock3 = new MkvSimpleBlock((short)20, 2, idAndSizeRawBytes, dataBuffer);
        MkvSimpleBlock simpleBlock4 = new MkvSimpleBlock((short)20, 1, idAndSizeRawBytes, dataBuffer);
        MkvSimpleBlock simpleBlock5 = new MkvSimpleBlock((short)20, 1, idAndSizeRawBytes, dataBuffer);

        // Do test
        mkvCluster = new MkvCluster(ByteBuffer.wrap(TestUtil.createClusterHeader(10)));
        mkvCluster.setAbsoluteTimecode(10);
        mkvCluster.addSimpleBlock(simpleBlock2);
        mkvCluster.addSimpleBlock(simpleBlock1);
        mkvCluster.addSimpleBlock(simpleBlock4);
        mkvCluster.addSimpleBlock(simpleBlock5);
        mkvCluster.addSimpleBlock(simpleBlock3);

        Assertions.assertEquals(mkvCluster.getEarliestSimpleBlockTimecode(), 20);
    }

    @Test
    public void removeLatestSimpleBlock_emptySimpleBlocks_returnNull() {
        // Do test
        final long absoluteTimecode = 10;
        mkvCluster = new MkvCluster(ByteBuffer.wrap(TestUtil.createClusterHeader(10)));
        mkvCluster.setAbsoluteTimecode(absoluteTimecode);

        Assertions.assertNull(mkvCluster.removeLatestSimpleBlock());
        Assertions.assertTrue(mkvCluster.toString().equals("cluster timecode: " + absoluteTimecode));
    }

    @Test
    public void getExpectedNextTimeCode_emptySimpleBlocks_returnClusterTimecodePlusOne() {
        // Do test
        mkvCluster = new MkvCluster(ByteBuffer.wrap(TestUtil.createClusterHeader(10)));
        mkvCluster.setAbsoluteTimecode(10);

        Assertions.assertEquals(11, mkvCluster.getExpectedNextTimeCode());
    }
}
