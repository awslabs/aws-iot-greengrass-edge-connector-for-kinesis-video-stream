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

package com.aws.iot.edgeconnectorforkvs.videouploader.visotors;

import com.amazonaws.kinesisvideo.parser.ebml.EBMLElementMetaData;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.MkvDataElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvEndMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvStartMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.aws.iot.edgeconnectorforkvs.videouploader.TestUtil;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvCluster;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvSimpleBlock;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.MergeFragmentException;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.MkvTracksException;
import com.aws.iot.edgeconnectorforkvs.videouploader.visitors.MergeFragmentVisitor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class MergeFragmentVisitorTest {

    private static final byte[] TRACK_ENTRY_HEADER = new byte[]{
            (byte) 0xAE, (byte) 0x9F
    };

    private static final byte[] TRACK_NUMBER_HEADER = new byte[]{
            (byte) 0xD7, (byte) 0x81
    };

    private static final byte[] TRACK_NUMBER_DATA = new byte[]{
            (byte) 0x01
    };

    private static final byte[] CLUSTER_HEADER = new byte[]{
            (byte) 0x1F, (byte) 0x43, (byte) 0xB6, (byte) 0x75, (byte) 0xFF
    };

    private static final byte[] TIMECODE_HEADER = new byte[]{
            (byte) 0xE7, (byte) 0x88
    };

    private static final byte[] TIMECODE_DATA = new byte[]{
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
    };

    private static final byte[] POSITION_HEADER = new byte[]{
            (byte) 0xA7, (byte) 0x81
    };

    private static final byte[] POSITION_DATA = new byte[]{
            (byte) 0x00
    };

    // MetaData for Tracks
    private final EBMLElementMetaData trackEntryMetaData = EBMLElementMetaData.builder()
            .typeInfo(MkvTypeInfos.TRACKENTRY).elementNumber(1L).build();
    private final EBMLElementMetaData trackNumberMetaData = EBMLElementMetaData.builder()
            .typeInfo(MkvTypeInfos.TRACKNUMBER).elementNumber(2L).build();

    // MetaData for Cluster
    private final EBMLElementMetaData clusterMetaData = EBMLElementMetaData.builder()
            .typeInfo(MkvTypeInfos.CLUSTER).elementNumber(1L).build();
    private final EBMLElementMetaData timecodeMetaData = EBMLElementMetaData.builder()
            .typeInfo(MkvTypeInfos.TIMECODE).elementNumber(1L).build();

    // Tracks elements
    private MkvStartMasterElement trackEntryStartMasterElement;
    private MkvDataElement trackNumberDataElement;
    private MkvEndMasterElement trackEntryEndMasterElement;

    // Cluster elements
    private MkvStartMasterElement clusterStartMasterElement;
    private MkvDataElement timecodeDataElement;

    @Mock
    private WritableByteChannel mockBufferingSegmentChannel;

    @Mock
    private WritableByteChannel mockOutputChannel;

    @Mock
    private MkvCluster mockCluster;

    @BeforeEach
    public void setupForEach() {
        ArrayList<EBMLElementMetaData> elementPath = new ArrayList<>();

        // Tracks elements
        trackEntryStartMasterElement = MkvStartMasterElement.builder()
                .elementMetaData(trackEntryMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(31L)
                .idAndSizeRawBytes(ByteBuffer.wrap(TRACK_ENTRY_HEADER))
                .build();
        elementPath.add(trackEntryMetaData);

        trackNumberDataElement = MkvDataElement.builder()
                .elementMetaData(trackNumberMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(1L)
                .idAndSizeRawBytes(ByteBuffer.wrap(TRACK_NUMBER_HEADER))
                .dataBuffer(ByteBuffer.wrap(TRACK_NUMBER_DATA))
                .build();

        elementPath.remove(elementPath.size() - 1);
        trackEntryEndMasterElement = MkvEndMasterElement.builder()
                .elementMetaData(trackEntryMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .build();

        // Cluster elements
        clusterStartMasterElement = MkvStartMasterElement.builder()
                .elementMetaData(clusterMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(31L)
                .idAndSizeRawBytes(ByteBuffer.wrap(CLUSTER_HEADER))
                .build();
        elementPath.add(clusterMetaData);

        timecodeDataElement = MkvDataElement.builder()
                .elementMetaData(timecodeMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(8L)
                .idAndSizeRawBytes(ByteBuffer.wrap(TIMECODE_HEADER))
                .dataBuffer(ByteBuffer.wrap(TIMECODE_DATA))
                .build();
    }

    private boolean setPrivateMember(MergeFragmentVisitor instance, String fieldName, Object value) {
        boolean result = false;
        try {
            Field field = MergeFragmentVisitor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
            result = true;
        } catch (NoSuchFieldException exception) {
            System.out.println("Failed to mock " + fieldName + ", NoSuchFieldException");
        } catch (IllegalAccessException exception) {
            System.out.println("Failed to mock " + fieldName + ", IllegalAccessException");
        }
        return result;
    }

    @Test
    public void visit_invalidStartElementInStartMasterElement_throwException() {
        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Do test
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> mergeFragmentVisitor.visit(trackEntryStartMasterElement));
    }

    @Test
    public void visit_invalidStartElementInDataElement_throwException() {
        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Do test
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> mergeFragmentVisitor.visit(trackNumberDataElement));
    }

    @Test
    public void visit_invalidStartElementInEndMasterElement_throwException() {
        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Do test
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> mergeFragmentVisitor.visit(trackEntryEndMasterElement));
    }

    @Test
    public void visit_invalidStateInStartMasterElement_throwException() {
        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Mock it
        Assumptions.assumeTrue(setPrivateMember(visitor, "state", MergeFragmentVisitor.MergeState.INVALID));

        // Do test
        Assertions.assertThrows(MkvTracksException.class,
                () -> visitor.visit(trackEntryStartMasterElement));
    }

    @Test
    public void visit_invalidStateInDataElement_throwException() {
        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Mock it
        Assumptions.assumeTrue(setPrivateMember(visitor, "state", MergeFragmentVisitor.MergeState.INVALID));

        // Do test
        Assertions.assertThrows(MkvTracksException.class, () -> visitor.visit(trackNumberDataElement));
    }

    @Test
    public void visit_invalidStateInEndMasterElement_throwException() {
        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Mock it
        Assumptions.assumeTrue(setPrivateMember(visitor, "state", MergeFragmentVisitor.MergeState.INVALID));

        // Do test
        Assertions.assertThrows(MkvTracksException.class, () -> visitor.visit(trackEntryEndMasterElement));
    }

    @Test
    public void applyVisitor_twoOrderedVideo_videoMerged() throws MkvElementVisitException,
            IOException {

        // Create a Video as following
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0
        final ByteArrayOutputStream firstVideoOutputStream = new ByteArrayOutputStream();
        firstVideoOutputStream.write(TestUtil.createTracksHeader(false));
        firstVideoOutputStream.write(TestUtil.createClusterHeader(0));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        firstVideoOutputStream.close();
        final ByteArrayInputStream firstVideoInputStream =
                new ByteArrayInputStream(firstVideoOutputStream.toByteArray());

        // Create a Video as following
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0
        final long secondVideoFragmentTimecode = 10;
        final ByteArrayOutputStream secondVideoOutputStream = new ByteArrayOutputStream();
        secondVideoOutputStream.write(TestUtil.createTracksHeader(false));
        secondVideoOutputStream.write(TestUtil.createClusterHeader(0));
        secondVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        secondVideoOutputStream.close();
        final ByteArrayInputStream secondVideoInputStream =
                new ByteArrayInputStream(secondVideoOutputStream.toByteArray());

        // Expected result:
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0
        final ByteArrayOutputStream expectedResultOutputStream = new ByteArrayOutputStream();
        expectedResultOutputStream.write(TestUtil.createTracksHeader(false));
        expectedResultOutputStream.write(TestUtil.createClusterHeader(0));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        expectedResultOutputStream.write(TestUtil.createClusterHeader(10));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        expectedResultOutputStream.close();
        final byte[] expectedResult = expectedResultOutputStream.toByteArray();

        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Merge first video
        final StreamingMkvReader streamingMkvReaderForFirstVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(firstVideoInputStream));
        streamingMkvReaderForFirstVideo.apply(mergeFragmentVisitor);

        // Merge second video
        mergeFragmentVisitor.setNextFragmentTimecodeOffsetMs(secondVideoFragmentTimecode);
        final StreamingMkvReader streamingMkvReaderForSecondVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(secondVideoInputStream));
        streamingMkvReaderForSecondVideo.apply(mergeFragmentVisitor);

        mergeFragmentVisitor.flush();

        // Verify answer
        byteArrayOutputStream.close();
        final byte[] result = byteArrayOutputStream.toByteArray();
        Assertions.assertArrayEquals(result, expectedResult);
    }

    @Test
    public void applyVisitor_oneOrderedVideo_videoSorted() throws MkvElementVisitException,
            IOException {

        // Create a Video as following
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0
        //          simple block: relative timecode 20
        //          simple block: relative timecode 10
        final ByteArrayOutputStream videoOutOfOrderOutputStream = new ByteArrayOutputStream();
        videoOutOfOrderOutputStream.write(TestUtil.createTracksHeader(false));
        videoOutOfOrderOutputStream.write(TestUtil.createClusterHeader(0));
        videoOutOfOrderOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        videoOutOfOrderOutputStream.write(TestUtil.createSimpleBlock((short) 20, 1));
        videoOutOfOrderOutputStream.write(TestUtil.createSimpleBlock((short) 10, 1));
        videoOutOfOrderOutputStream.close();
        final ByteArrayInputStream videoOutOfOrderInputStream =
                new ByteArrayInputStream(videoOutOfOrderOutputStream.toByteArray());

        // Expected result:
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0
        //          simple block: relative timecode 10
        //          simple block: relative timecode 20
        final ByteArrayOutputStream expectedResultOutputStream = new ByteArrayOutputStream();
        expectedResultOutputStream.write(TestUtil.createTracksHeader(false));
        expectedResultOutputStream.write(TestUtil.createClusterHeader(0));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 10, 1));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 20, 1));
        expectedResultOutputStream.close();
        final byte[] expectedResult = expectedResultOutputStream.toByteArray();

        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Merge first video
        final StreamingMkvReader streamingMkvReader;
        streamingMkvReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(videoOutOfOrderInputStream));
        streamingMkvReader.apply(mergeFragmentVisitor);

        mergeFragmentVisitor.flush();

        // Verify answer
        byteArrayOutputStream.close();
        final byte[] result = byteArrayOutputStream.toByteArray();
        Assertions.assertArrayEquals(result, expectedResult);
    }

    @Test
    public void applyVisitor_noTrackInfo_throwException() throws IOException {
        // Create a Video without track header but has following
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0
        final ByteArrayOutputStream videoOutOfOrderOutputStream = new ByteArrayOutputStream();
        videoOutOfOrderOutputStream.write(TestUtil.createNoTracksHeader());
        videoOutOfOrderOutputStream.write(TestUtil.createClusterHeader(0));
        videoOutOfOrderOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        videoOutOfOrderOutputStream.close();
        final ByteArrayInputStream videoOutOfOrderInputStream =
                new ByteArrayInputStream(videoOutOfOrderOutputStream.toByteArray());

        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Merge first video
        final StreamingMkvReader streamingMkvReader;
        streamingMkvReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(videoOutOfOrderInputStream));
        Assertions.assertThrows(MergeFragmentException.class, () -> streamingMkvReader.apply(mergeFragmentVisitor));
    }

    @Test
    public void applyVisitor_twoOrderedAndOverlappedVideo_videoSortedAndMerged() throws MkvElementVisitException,
            IOException {

        // Create a Video as following
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0
        //          simple block: relative timecode 20 (this one overlapped with next cluster)
        final ByteArrayOutputStream firstVideoOutputStream = new ByteArrayOutputStream();
        firstVideoOutputStream.write(TestUtil.createTracksHeader(false));
        firstVideoOutputStream.write(TestUtil.createClusterHeader(0));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 20, 1));
        firstVideoOutputStream.close();
        final ByteArrayInputStream firstVideoInputStream =
                new ByteArrayInputStream(firstVideoOutputStream.toByteArray());

        // Create a Video as following
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0
        //          simple block: relative timecode 20
        final long secondVideoFragmentTimecode = 10;
        final ByteArrayOutputStream secondVideoOutputStream = new ByteArrayOutputStream();
        secondVideoOutputStream.write(TestUtil.createTracksHeader(false));
        secondVideoOutputStream.write(TestUtil.createClusterHeader(0));
        secondVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        secondVideoOutputStream.write(TestUtil.createSimpleBlock((short) 20, 1));
        secondVideoOutputStream.close();
        final ByteArrayInputStream secondVideoInputStream =
                new ByteArrayInputStream(secondVideoOutputStream.toByteArray());

        // Expected result:
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0
        //          simple block: relative timecode 10
        //          simple block: relative timecode 20
        final ByteArrayOutputStream expectedResultOutputStream = new ByteArrayOutputStream();
        expectedResultOutputStream.write(TestUtil.createTracksHeader(false));
        expectedResultOutputStream.write(TestUtil.createClusterHeader(0));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        expectedResultOutputStream.write(TestUtil.createClusterHeader(10));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 10, 1));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 20, 1));
        expectedResultOutputStream.close();
        final byte[] expectedResult = expectedResultOutputStream.toByteArray();

        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Merge first video
        final StreamingMkvReader streamingMkvReaderForFirstVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(firstVideoInputStream));
        streamingMkvReaderForFirstVideo.apply(mergeFragmentVisitor);

        // Merge second video
        mergeFragmentVisitor.setNextFragmentTimecodeOffsetMs(secondVideoFragmentTimecode);
        final StreamingMkvReader streamingMkvReaderForSecondVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(secondVideoInputStream));
        streamingMkvReaderForSecondVideo.apply(mergeFragmentVisitor);

        mergeFragmentVisitor.flush();

        // Verify answer
        byteArrayOutputStream.close();
        final byte[] result = byteArrayOutputStream.toByteArray();

        Assertions.assertArrayEquals(result, expectedResult);
    }

    @Test
    public void applyVisitor_twoOrderedAndOverlappedVideoAudio_videoAudioSortedAndMerged() throws MkvElementVisitException,
            IOException {

        // Create a Video/Audio as following
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 5, track 2
        //          simple block: relative timecode 10, track 2
        final ByteArrayOutputStream firstVideoOutputStream = new ByteArrayOutputStream();
        firstVideoOutputStream.write(TestUtil.createTracksHeader(true));
        firstVideoOutputStream.write(TestUtil.createClusterHeader(0));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 5, 2));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 10, 2));
        firstVideoOutputStream.close();
        final ByteArrayInputStream firstVideoInputStream =
                new ByteArrayInputStream(firstVideoOutputStream.toByteArray());

        // Create a Video/Audio as following
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 10, track 2
        final long secondVideoFragmentTimecode = 10;
        final ByteArrayOutputStream secondVideoOutputStream = new ByteArrayOutputStream();
        secondVideoOutputStream.write(TestUtil.createTracksHeader(true));
        secondVideoOutputStream.write(TestUtil.createClusterHeader(0));
        secondVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        secondVideoOutputStream.write(TestUtil.createSimpleBlock((short) 10, 2));
        secondVideoOutputStream.close();
        final ByteArrayInputStream secondVideoInputStream =
                new ByteArrayInputStream(secondVideoOutputStream.toByteArray());

        // Expected result:
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 5, track 2
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 0, track 2
        //          simple block: relative timecode 10, track 2
        final ByteArrayOutputStream expectedResultOutputStream = new ByteArrayOutputStream();
        expectedResultOutputStream.write(TestUtil.createTracksHeader(true));
        expectedResultOutputStream.write(TestUtil.createClusterHeader(0));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 5, 2));
        expectedResultOutputStream.write(TestUtil.createClusterHeader(10));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 0, 2));
        expectedResultOutputStream.write(TestUtil.createSimpleBlock((short) 10, 2));
        expectedResultOutputStream.close();
        final byte[] expectedResult = expectedResultOutputStream.toByteArray();

        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Merge first video
        final StreamingMkvReader streamingMkvReaderForFirstVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(firstVideoInputStream));
        streamingMkvReaderForFirstVideo.apply(mergeFragmentVisitor);

        // Merge second video
        mergeFragmentVisitor.setNextFragmentTimecodeOffsetMs(secondVideoFragmentTimecode);
        final StreamingMkvReader streamingMkvReaderForSecondVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(secondVideoInputStream));
        streamingMkvReaderForSecondVideo.apply(mergeFragmentVisitor);

        mergeFragmentVisitor.flush();

        // Verify answer
        byteArrayOutputStream.close();
        byte[] result = byteArrayOutputStream.toByteArray();

        Assertions.assertArrayEquals(expectedResult, result);
    }

    @Test
    public void applyVisitor_twoDifferentTrackMedia_throwException() throws MkvElementVisitException,
            IOException {
        // Create a Video/Audio as following
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 5, track 2
        final ByteArrayOutputStream firstVideoOutputStream = new ByteArrayOutputStream();
        firstVideoOutputStream.write(TestUtil.createTracksHeader(true));
        firstVideoOutputStream.write(TestUtil.createClusterHeader(0));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 5, 2));
        firstVideoOutputStream.close();
        final ByteArrayInputStream firstVideoInputStream =
                new ByteArrayInputStream(firstVideoOutputStream.toByteArray());

        // Create a Video as following
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0, track 1
        final long secondVideoFragmentTimecode = 10;
        final ByteArrayOutputStream secondVideoOutputStream = new ByteArrayOutputStream();
        secondVideoOutputStream.write(TestUtil.createTracksHeader(false));
        secondVideoOutputStream.write(TestUtil.createClusterHeader(0));
        secondVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        secondVideoOutputStream.close();
        final ByteArrayInputStream secondVideoInputStream =
                new ByteArrayInputStream(secondVideoOutputStream.toByteArray());

        // Expected result: throw Exception

        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Merge first video
        final StreamingMkvReader streamingMkvReaderForFirstVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(firstVideoInputStream));
        streamingMkvReaderForFirstVideo.apply(mergeFragmentVisitor);

        // Merge second video
        mergeFragmentVisitor.setNextFragmentTimecodeOffsetMs(secondVideoFragmentTimecode);
        final StreamingMkvReader streamingMkvReaderForSecondVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(secondVideoInputStream));

        Assertions.assertThrows(MergeFragmentException.class,
                () -> streamingMkvReaderForSecondVideo.apply(mergeFragmentVisitor));

        mergeFragmentVisitor.flush();

        // Verify answer
        byteArrayOutputStream.close();
        final byte[] result = byteArrayOutputStream.toByteArray();

        Assertions.assertArrayEquals(firstVideoOutputStream.toByteArray(), result);
    }

    @Test
    public void emitCluster_missingFrameForTrack_noClusterWriteToChannel()
            throws IOException, MkvElementVisitException {
        // Create a Video/Audio as following
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 5, track 2
        //          simple block: relative timecode 10, track 2
        final ByteArrayOutputStream firstVideoOutputStream = new ByteArrayOutputStream();
        firstVideoOutputStream.write(TestUtil.createTracksHeader(true));
        firstVideoOutputStream.write(TestUtil.createClusterHeader(0));
        firstVideoOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        firstVideoOutputStream.close();
        final ByteArrayInputStream firstVideoInputStream =
                new ByteArrayInputStream(firstVideoOutputStream.toByteArray());

        // Expected result:
        //      cluster: absolute timecode 0
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 5, track 2
        //      cluster: absolute timecode 10
        //          simple block: relative timecode 0, track 1
        //          simple block: relative timecode 0, track 2
        //          simple block: relative timecode 10, track 2
        final ByteArrayOutputStream expectedResultOutputStream = new ByteArrayOutputStream();
        expectedResultOutputStream.write(TestUtil.createTracksHeader(true));
        expectedResultOutputStream.close();
        final byte[] expectedResult = expectedResultOutputStream.toByteArray();

        // Setup output stream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Ready to test
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

        // Merge first video
        final StreamingMkvReader streamingMkvReaderForFirstVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(firstVideoInputStream));
        streamingMkvReaderForFirstVideo.apply(mergeFragmentVisitor);

        mergeFragmentVisitor.flush();

        // Verify answer
        byteArrayOutputStream.close();
        byte[] result = byteArrayOutputStream.toByteArray();

        Assertions.assertArrayEquals(expectedResult, result);
    }

    @Test
    public void visit_bufferNonClusterStartMasterElement_elementIgnored()
            throws IOException, MkvElementVisitException {
        // Setup feed
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(TestUtil.createTracksHeader(false));
        outputStream.write(TestUtil.createClusterHeader(0));
        outputStream.write(TestUtil.createSilentTracks());
        outputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        outputStream.close();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        // Setup answer
        final ByteArrayOutputStream answerOutputStream = new ByteArrayOutputStream();
        answerOutputStream.write(TestUtil.createTracksHeader(false));
        answerOutputStream.write(TestUtil.createClusterHeader(0));
        answerOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
        answerOutputStream.close();
        final byte[] answer = answerOutputStream.toByteArray();

        // Setup visitor
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(resultOutputStream);

        // Merge video
        final StreamingMkvReader streamingMkvReaderForFirstVideo =
                StreamingMkvReader.createDefault(new InputStreamParserByteSource(inputStream));
        streamingMkvReaderForFirstVideo.apply(mergeFragmentVisitor);

        mergeFragmentVisitor.flush();

        // Verify answer
        resultOutputStream.close();
        byte[] result = resultOutputStream.toByteArray();

        Assertions.assertArrayEquals(answer, result);
    }

    @Test
    public void bufferAndCollectCluster_nestedCluster_throwException()
            throws IllegalAccessException, NoSuchMethodException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        Assumptions.assumeTrue(setPrivateMember(visitor, "currentCluster",
                new MkvCluster(ByteBuffer.wrap(new byte[0]))));

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("bufferAndCollectCluster",
                MkvStartMasterElement.class);
        method.setAccessible(true);

        boolean isExceptionThrown = false;
        try {
            method.invoke(visitor, clusterStartMasterElement);
        } catch (InvocationTargetException exception) {
            Assertions.assertTrue(exception.getCause() instanceof MkvElementVisitException);
            isExceptionThrown = true;
        }
        Assertions.assertTrue(isExceptionThrown);
    }

    @Test
    public void bufferAndCollectCluster_nullClusterForDataElement_throwException()
            throws IllegalAccessException, NoSuchMethodException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        Assumptions.assumeTrue(setPrivateMember(visitor, "currentCluster", null));

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("bufferAndCollectCluster",
                MkvDataElement.class);
        method.setAccessible(true);

        boolean isExceptionThrown = false;
        try {
            method.invoke(visitor, timecodeDataElement);
        } catch (InvocationTargetException exception) {
            Assertions.assertTrue(exception.getCause() instanceof MkvElementVisitException);
            isExceptionThrown = true;
        }
        Assertions.assertTrue(isExceptionThrown);
    }

    @Test
    public void bufferAndCollectCluster_noNextFragmentTimecodeOffsetSpecified_timecodeUpdated()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        Assumptions.assumeTrue(setPrivateMember(visitor, "previousCluster", null));

        MkvCluster currentCluster = new MkvCluster(ByteBuffer.wrap(new byte[0]));
        Field fieldCurrentCluster = MergeFragmentVisitor.class.getDeclaredField("currentCluster");
        fieldCurrentCluster.setAccessible(true);
        fieldCurrentCluster.set(visitor, currentCluster);

        visitor.setNextFragmentTimecodeOffsetMs(-1);

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("bufferAndCollectCluster",
                MkvDataElement.class);
        method.setAccessible(true);

        method.invoke(visitor, timecodeDataElement);

        Assertions.assertEquals(0L, currentCluster.getAbsoluteTimecode());
    }

    @Test
    public void bufferAndCollectCluster_usePreviousClusterToUpdateNextFragmentTimecodeOffset_timecodeUpdated()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        MkvCluster previousCluster = new MkvCluster(ByteBuffer.wrap(new byte[0]));
        previousCluster.setAbsoluteTimecode(10);
        Assumptions.assumeTrue(setPrivateMember(visitor, "previousCluster", previousCluster));

        MkvCluster currentCluster = new MkvCluster(ByteBuffer.wrap(new byte[0]));
        Assumptions.assumeTrue(setPrivateMember(visitor, "currentCluster", currentCluster));

        visitor.setNextFragmentTimecodeOffsetMs(-1);

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("bufferAndCollectCluster",
                MkvDataElement.class);
        method.setAccessible(true);

        method.invoke(visitor, timecodeDataElement);

        Assertions.assertEquals(11L, currentCluster.getAbsoluteTimecode());
    }

    @Test
    public void bufferAndCollectSegment_segmentVerifiedAndTryToBufferStartMasterElement_throwException()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        Assumptions.assumeTrue(setPrivateMember(visitor, "isSegmentVerified", true));

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("bufferAndCollectSegment",
                MkvStartMasterElement.class);
        method.setAccessible(true);

        boolean isExceptionThrown = false;
        try {
            method.invoke(visitor, clusterStartMasterElement);
        } catch (InvocationTargetException exception) {
            Assertions.assertTrue(exception.getCause() instanceof MkvElementVisitException);
            isExceptionThrown = true;
        }
        Assertions.assertFalse(isExceptionThrown);
    }

    @Test
    public void bufferAndCollectSegment_segmentVerifiedAndTryToBufferDataElement_throwException()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        Assumptions.assumeTrue(setPrivateMember(visitor, "isSegmentVerified", true));

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("bufferAndCollectSegment",
                MkvDataElement.class);
        method.setAccessible(true);

        boolean isExceptionThrown = false;
        try {
            method.invoke(visitor, timecodeDataElement);
        } catch (InvocationTargetException exception) {
            Assertions.assertTrue(exception.getCause() instanceof MkvElementVisitException);
            isExceptionThrown = true;
        }
        Assertions.assertFalse(isExceptionThrown);
    }

    @Test
    public void sortClusters_validClusters_clusterSorted()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        MkvCluster previousCluster = new MkvCluster(ByteBuffer.wrap(new byte[0]));
        previousCluster.setAbsoluteTimecode(0);
        previousCluster.addSimpleBlock(new MkvSimpleBlock(
                (short) 20,
                1,
                ByteBuffer.wrap(new byte[]{(byte) 0xA3, (byte) 0x85}),
                ByteBuffer.wrap(new byte[]{(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00})
        ));
        Assumptions.assumeTrue(setPrivateMember(visitor, "previousCluster", previousCluster));

        MkvCluster currentCluster = new MkvCluster(ByteBuffer.wrap(new byte[0]));
        currentCluster.setAbsoluteTimecode(10);
        Assumptions.assumeTrue(setPrivateMember(visitor, "currentCluster", currentCluster));

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("sortClusters");
        method.setAccessible(true);

        method.invoke(visitor);

        Assertions.assertEquals(0, previousCluster.getLatestSimpleBlockTimecode());
        Assertions.assertEquals(20, currentCluster.getEarliestSimpleBlockTimecode());
    }

    @Test
    public void sortClusters_noCurrentClusters_clusterSorted()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        MergeFragmentVisitor visitor = MergeFragmentVisitor.create(resultOutputStream);

        MkvCluster previousCluster = new MkvCluster(ByteBuffer.wrap(new byte[0]));
        previousCluster.setAbsoluteTimecode(0);
        previousCluster.addSimpleBlock(new MkvSimpleBlock(
                (short) 20,
                1,
                ByteBuffer.wrap(new byte[]{(byte) 0xA3, (byte) 0x85}),
                ByteBuffer.wrap(new byte[]{(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00})
        ));
        previousCluster.addSimpleBlock(new MkvSimpleBlock(
                (short) 10,
                1,
                ByteBuffer.wrap(new byte[]{(byte) 0xA3, (byte) 0x85}),
                ByteBuffer.wrap(new byte[]{(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00})
        ));
        Assumptions.assumeTrue(setPrivateMember(visitor, "previousCluster", previousCluster));
        Assumptions.assumeTrue(setPrivateMember(visitor, "currentCluster", null));

        Method method = MergeFragmentVisitor.class.getDeclaredMethod("sortClusters");
        method.setAccessible(true);

        method.invoke(visitor);

        Assertions.assertEquals(10, previousCluster.getEarliestSimpleBlockTimecode());
        Assertions.assertEquals(20, previousCluster.getLatestSimpleBlockTimecode());
    }
}
