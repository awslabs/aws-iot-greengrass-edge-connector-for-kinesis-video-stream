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

import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.aws.iot.edgeconnectorforkvs.videouploader.TestUtil;
import com.aws.iot.edgeconnectorforkvs.videouploader.visitors.MergeFragmentVisitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class MkvInputStreamTest {

    private MkvInputStream mkvInputStream;

    @Mock
    private MergeFragmentVisitor mockMergeFragmentVisitor;

    @Mock
    private StreamingMkvReader mockStreamingMkvReader;

    @Mock
    private ByteArrayInputStream mockByteArrayInputStream;

    @Test
    public void constructor_nullInput_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> mkvInputStream = new MkvInputStream(null));
    }

    @Test
    public void wantMoreData_MkvElementVisitException_noExceptionIsThrown()
            throws Exception {
        // Setup test
        byte[] sampleVideo = TestUtil.createSampleVideo(false);
        Assumptions.assumeTrue(sampleVideo != null);
        mkvInputStream = new MkvInputStream(new ByteArrayInputStream(sampleVideo));

        // Mock it
        Field field = MkvInputStream.class.getDeclaredField("streamingMkvReader");
        field.setAccessible(true);
        field.set(mkvInputStream, mockStreamingMkvReader);

        doThrow(MkvElementVisitException.class).when(mockStreamingMkvReader).apply(any(MkvElementVisitor.class));

        // Do test
        Assertions.assertDoesNotThrow(() -> mkvInputStream.available());
    }

    @Test
    public void close_visitorThrowException_noExceptionIsThrown()
            throws Exception {
        // Setup test
        byte[] sampleVideo = TestUtil.createSampleVideo(false);
        Assumptions.assumeTrue(sampleVideo != null);
        mkvInputStream = new MkvInputStream(new ByteArrayInputStream(sampleVideo));

        // Mock it
        Field field = MkvInputStream.class.getDeclaredField("mergeFragmentVisitor");
        field.setAccessible(true);
        field.set(mkvInputStream, mockMergeFragmentVisitor);

        doThrow(IOException.class).when(mockMergeFragmentVisitor).flush();

        // Do test
        Assertions.assertDoesNotThrow(() -> mkvInputStream.close());
    }

    @Test
    public void closeMkvInputStream_throwException_noExceptionIsThrown()
            throws Exception {
        // Setup test
        byte[] sampleVideo = TestUtil.createSampleVideo(false);
        Assumptions.assumeTrue(sampleVideo != null);
        mkvInputStream = new MkvInputStream(new ByteArrayInputStream(sampleVideo));

        // Mock it
        Field field = MkvInputStream.class.getDeclaredField("byteArrayInputStream");
        field.setAccessible(true);
        field.set(mkvInputStream, mockByteArrayInputStream);

        doThrow(IOException.class).when(mockByteArrayInputStream).close();

        // Do test
        Assertions.assertDoesNotThrow(() -> mkvInputStream.close());
    }

    @Test
    public void readBuffer_videoSamples_sameOutputVideoMerged() {
        final byte[] buffer = new byte[1024];
        int readLen;
        byte[] sampleVideo = new byte[0];
        byte[] expectedResult = new byte[0];

        // Setup test
        try {
            // Create a Video as following
            //      cluster: absolute timecode 0
            //          simple block: relative timecode 0, track 1
            //      cluster: absolute timecode 10
            //          simple block: relative timecode 0, track 1
            final ByteArrayOutputStream sampleOutputStream = new ByteArrayOutputStream();
            sampleOutputStream.write(TestUtil.createTracksHeader(false));
            sampleOutputStream.write(TestUtil.createClusterHeader(0));
            sampleOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            sampleOutputStream.write(TestUtil.createClusterHeader(10));
            sampleOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            sampleOutputStream.close();
            sampleVideo = sampleOutputStream.toByteArray();

            // Expected result:
            //      cluster: absolute timecode 0
            //          simple block: relative timecode 0, track 1
            // NOTE: MkvInputStream would buffer 1 cluster to avoid frames out of order.  It results the last cluster can
            // never be read.
            final ByteArrayOutputStream answerOutputStream = new ByteArrayOutputStream();
            answerOutputStream.write(TestUtil.createTracksHeader(false));
            answerOutputStream.write(TestUtil.createClusterHeader(0));
            answerOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            answerOutputStream.close();
            expectedResult = answerOutputStream.toByteArray();
        } catch (IOException exception) {
            System.out.println("Unable to setup test");
        }
        Assumptions.assumeTrue(sampleVideo.length > 0);
        Assumptions.assumeTrue(expectedResult.length > 0);

        // Do test
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(sampleVideo);
        Assumptions.assumeTrue(byteArrayInputStream != null);
        mkvInputStream = new MkvInputStream(byteArrayInputStream);

        while ((readLen = mkvInputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, readLen);
        }
        mkvInputStream.close();
        final byte[] result = outputStream.toByteArray();

        // Verify answer
        Assertions.assertArrayEquals(expectedResult, result);
    }

    @Test
    public void readOneByte_videoSamples_sameOutputVideoMerged() {
        int b;
        byte[] sampleVideo = new byte[0];
        byte[] expectedResult = new byte[0];

        // Setup test
        try {
            // Create a Video as following
            //      cluster: absolute timecode 0
            //          simple block: relative timecode 0, track 1
            //      cluster: absolute timecode 10
            //          simple block: relative timecode 0, track 1
            final ByteArrayOutputStream sampleOutputStream = new ByteArrayOutputStream();
            sampleOutputStream.write(TestUtil.createTracksHeader(false));
            sampleOutputStream.write(TestUtil.createTagHeader());
            sampleOutputStream.write(TestUtil.createClusterHeader(0));
            sampleOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            sampleOutputStream.write(TestUtil.createClusterHeader(10));
            sampleOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            sampleOutputStream.close();
            sampleVideo = sampleOutputStream.toByteArray();

            // Expected result:
            //      cluster: absolute timecode 0
            //          simple block: relative timecode 0, track 1
            // NOTE: MkvInputStream would buffer 1 cluster to avoid frames out of order.  It results the last cluster can
            // never be read.
            final ByteArrayOutputStream answerOutputStream = new ByteArrayOutputStream();
            answerOutputStream.write(TestUtil.createTracksHeader(false));
            answerOutputStream.write(TestUtil.createClusterHeader(0));
            answerOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            answerOutputStream.close();
            expectedResult = answerOutputStream.toByteArray();
        } catch (IOException exception) {
            System.out.println("Unable to setup test");
        }
        Assumptions.assumeTrue(sampleVideo.length > 0);
        Assumptions.assumeTrue(expectedResult.length > 0);

        // Do test
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(sampleVideo);
        Assumptions.assumeTrue(byteArrayInputStream != null);
        mkvInputStream = new MkvInputStream(byteArrayInputStream);

        while ((b = mkvInputStream.read()) != -1) {
            outputStream.write(b);
        }
        mkvInputStream.close();
        final byte[] result = outputStream.toByteArray();

        // Verify answer
        Assertions.assertArrayEquals(expectedResult, result);
    }

    @Test
    public void available_closeStream_negativeByteCount() {
        // Setup test
        byte[] sampleVideo = TestUtil.createSampleVideo(false);
        Assumptions.assumeTrue(sampleVideo != null);

        // Do test
        mkvInputStream = new MkvInputStream(new ByteArrayInputStream(sampleVideo));

        mkvInputStream.close();
        Assertions.assertEquals(-1, mkvInputStream.available());
    }

    @Test
    public void markSupported_validSampleVideo_returnFalse() {
        // Setup test
        byte[] sampleVideo = TestUtil.createSampleVideo(false);
        Assumptions.assumeTrue(sampleVideo != null);

        // Do test
        mkvInputStream = new MkvInputStream(new ByteArrayInputStream(sampleVideo));

        Assertions.assertFalse(mkvInputStream.markSupported());
    }
}
