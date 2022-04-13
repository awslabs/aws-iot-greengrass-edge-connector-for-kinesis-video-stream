/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aws.iot.edgeconnectorforkvs.videouploader.visotors;

import com.amazonaws.kinesisvideo.parser.ebml.EBMLElementMetaData;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.MkvDataElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvEndMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvStartMasterElement;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.MkvTracksException;
import com.aws.iot.edgeconnectorforkvs.videouploader.visitors.MkvTracksVisitor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;

@Slf4j
public class MkvTracksVisitorTest {

    private static final byte[] TRACKS_HEADER = new byte[]{
            (byte) 0x16, (byte) 0x54, (byte) 0xAE, (byte) 0x6B, (byte) 0xA1
    };

    private static final byte[] TRACK_ENTRY_HEADER = new byte[]{
            (byte) 0xAE, (byte) 0x9F
    };

    private static final byte[] TRACK_NUMBER_HEADER = new byte[]{
            (byte) 0xD7, (byte) 0x81
    };

    private static final byte[] TRACK_NUMBER_DATA = new byte[]{
            (byte) 0x01
    };

    private static final byte[] TRACK_UID_HEADER = new byte[]{
            (byte) 0x73, (byte) 0xC5, (byte) 0x88
    };

    private static final byte[] TRACK_UID_DATA = new byte[]{
            (byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07
    };

    private static final byte[] CODEC_ID_HEADER = new byte[]{
            (byte) 0x86, (byte) 0x8F
    };

    private static final byte[] CODEC_ID_DATA = new byte[]{
            (byte) 0x56, (byte) 0x5F, (byte) 0x4D, (byte) 0x50, (byte) 0x45, (byte) 0x47, (byte) 0x34, (byte) 0x2F,
            (byte) 0x49, (byte) 0x53, (byte) 0x4F, (byte) 0x2F, (byte) 0x41, (byte) 0x56, (byte) 0x43
    };

    private static final byte[] CRC_32_HEADER = new byte[]{
            (byte) 0xBF, (byte) 0x81
    };

    private static final byte[] CRC_32_DATA = new byte[]{
            (byte) 0x01
    };

    private static final byte[] VOID_HEADER = new byte[]{
            (byte) 0xBF, (byte) 0x81
    };

    private static final byte[] VOID_DATA = new byte[]{
            (byte) 0xFF
    };

    private EBMLElementMetaData tracksMetaData;
    private EBMLElementMetaData trackEntryMetaData;
    private EBMLElementMetaData trackNumberMetaData;
    private EBMLElementMetaData trackUidMetaData;
    private EBMLElementMetaData codecIdMetaData;

    private EBMLElementMetaData crc32MetaData;
    private EBMLElementMetaData voidMetaData;

    private MkvStartMasterElement tracksStartMasterElement;
    private MkvStartMasterElement trackEntryStartMasterElement;
    private MkvDataElement trackNumberDataElement;
    private MkvDataElement trackUidDataElement;
    private MkvDataElement codecIdDataElement;
    private MkvEndMasterElement trackEntryEndMasterElement;
    private MkvEndMasterElement tracksEndMasterElement;

    private MkvDataElement crc32DataElement;
    private MkvDataElement voidDataElement;

    private MkvTracksVisitor visitor;

    @BeforeEach
    public void setupForEach() {
        tracksMetaData = EBMLElementMetaData.builder()
                .typeInfo(MkvTypeInfos.TRACKS)
                .elementNumber(0L)
                .build();
        trackEntryMetaData = EBMLElementMetaData.builder()
                .typeInfo(MkvTypeInfos.TRACKENTRY)
                .elementNumber(1L)
                .build();
        trackNumberMetaData = EBMLElementMetaData.builder()
                .typeInfo(MkvTypeInfos.TRACKNUMBER)
                .elementNumber(2L)
                .build();
        trackUidMetaData = EBMLElementMetaData.builder()
                .typeInfo(MkvTypeInfos.TRACKUID)
                .elementNumber(3L)
                .build();
        codecIdMetaData = EBMLElementMetaData.builder()
                .typeInfo(MkvTypeInfos.CODECID)
                .elementNumber(4L)
                .build();

        crc32MetaData = EBMLElementMetaData.builder()
                .typeInfo(MkvTypeInfos.CRC_32)
                .elementNumber(5L)
                .build();
        voidMetaData = EBMLElementMetaData.builder()
                .typeInfo(MkvTypeInfos.VOID)
                .elementNumber(6L)
                .build();

        ArrayList<EBMLElementMetaData> elementPath = new ArrayList<>();

        tracksStartMasterElement = MkvStartMasterElement.builder()
                .elementMetaData(tracksMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(33L)
                .idAndSizeRawBytes(ByteBuffer.wrap(TRACKS_HEADER))
                .build();
        elementPath.add(tracksMetaData);

        trackEntryStartMasterElement = MkvStartMasterElement.builder()
                .elementMetaData(trackEntryMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(31L)
                .idAndSizeRawBytes(ByteBuffer.wrap(TRACK_ENTRY_HEADER))
                .build();
        elementPath.add(trackEntryMetaData);

        crc32DataElement = MkvDataElement.builder()
                .elementMetaData(crc32MetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(1L)
                .idAndSizeRawBytes(ByteBuffer.wrap(CRC_32_HEADER))
                .dataBuffer(ByteBuffer.wrap(CRC_32_DATA))
                .build();

        voidDataElement = MkvDataElement.builder()
                .elementMetaData(voidMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(1L)
                .idAndSizeRawBytes(ByteBuffer.wrap(VOID_HEADER))
                .dataBuffer(ByteBuffer.wrap(VOID_DATA))
                .build();

        trackNumberDataElement = MkvDataElement.builder()
                .elementMetaData(trackNumberMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(1L)
                .idAndSizeRawBytes(ByteBuffer.wrap(TRACK_NUMBER_HEADER))
                .dataBuffer(ByteBuffer.wrap(TRACK_NUMBER_DATA))
                .build();

        trackUidDataElement = MkvDataElement.builder()
                .elementMetaData(trackUidMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(8L)
                .idAndSizeRawBytes(ByteBuffer.wrap(TRACK_UID_HEADER))
                .dataBuffer(ByteBuffer.wrap(TRACK_UID_DATA))
                .build();

        codecIdDataElement = MkvDataElement.builder()
                .elementMetaData(codecIdMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .dataSize(15L)
                .idAndSizeRawBytes(ByteBuffer.wrap(CODEC_ID_HEADER))
                .dataBuffer(ByteBuffer.wrap(CODEC_ID_DATA))
                .build();

        elementPath.remove(elementPath.size() - 1);
        trackEntryEndMasterElement = MkvEndMasterElement.builder()
                .elementMetaData(trackEntryMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .build();

        elementPath.remove(elementPath.size() - 1);
        tracksEndMasterElement = MkvEndMasterElement.builder()
                .elementMetaData(tracksMetaData)
                .elementPath((ArrayList) elementPath.clone())
                .build();

        visitor = new MkvTracksVisitor();
    }

    private boolean setPrivateMember(MkvTracksVisitor instance, String fieldName, Object value) {
        boolean result = false;
        try {
            Field field = MkvTracksVisitor.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
            result = true;
        } catch (NoSuchFieldException exception) {
            log.error("Failed to mock " + fieldName + ", NoSuchFieldException");
        } catch (IllegalAccessException exception) {
            log.error("Failed to mock " + fieldName + ", IllegalAccessException");
        }
        return result;
    }

    @Test
    public void visit_validInputs_validStates() {
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
        visitor.visit(tracksStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(crc32DataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(voidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackNumberDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(codecIdDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(tracksEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
    }

    @Test
    public void visit_invalidStateInStartMasterElement_throwException() {
        Assumptions.assumeTrue(setPrivateMember(visitor, "state", MkvTracksVisitor.State.INVALID));
        Assertions.assertThrows(MkvTracksException.class, () -> visitor.visit(tracksStartMasterElement));
    }

    @Test
    public void visit_invalidStateInDataElement_throwException() {
        Assumptions.assumeTrue(setPrivateMember(visitor, "state", MkvTracksVisitor.State.INVALID));
        Assertions.assertThrows(MkvTracksException.class, () -> visitor.visit(trackNumberDataElement));
    }

    @Test
    public void visit_invalidStateInEndMasterElement_throwException()
            throws NoSuchFieldException, IllegalAccessException {
        Assumptions.assumeTrue(setPrivateMember(visitor, "state", MkvTracksVisitor.State.INVALID));
        Assertions.assertThrows(MkvTracksException.class, () -> visitor.visit(tracksEndMasterElement));
    }

    @Test
    public void isTracksEquivalent_differentTracksContent_returnFalse() {
        // First Tracks
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
        visitor.visit(tracksStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackNumberDataElement);       // this is the different one
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(codecIdDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(tracksEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());

        // Second Tracks
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
        visitor.visit(tracksStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);         // this is the different one
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(codecIdDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(tracksEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());

        Assertions.assertFalse(visitor.isTracksEquivalent());
    }

    @Test
    public void isTracksEquivalent_differentTracksType_returnFalse() {
        // First Tracks
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
        visitor.visit(tracksStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryStartMasterElement);    // this is the different one
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackNumberDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(codecIdDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(tracksEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());

        // Second Tracks
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
        visitor.visit(tracksStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);             // this is the different one
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackNumberDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(tracksEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());

        Assertions.assertFalse(visitor.isTracksEquivalent());
    }

    @Test
    public void isTracksEquivalent_differentTracksContentInUid_returnFalse() {
        // First Tracks
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
        visitor.visit(tracksStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);       // this is the different one
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(codecIdDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(tracksEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());

        // Second Tracks
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());
        visitor.visit(tracksStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryStartMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackNumberDataElement);         // this is the different one
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackUidDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(codecIdDataElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(trackEntryEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.BUFFERING_TRACKS, visitor.getState());
        visitor.visit(tracksEndMasterElement);
        Assertions.assertEquals(MkvTracksVisitor.State.NEW, visitor.getState());

        Assertions.assertFalse(visitor.isTracksEquivalent());
    }

    @Test
    public void toMkvRawData_nullPreviousTracks_throwExcpetion() {
        Assertions.assertThrows(IOException.class, () -> visitor.toMkvRawData());
    }
}
