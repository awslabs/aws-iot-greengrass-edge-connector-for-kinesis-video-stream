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

package com.aws.iot.edgeconnectorforkvs.videouploader.mkv;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MkvDataRawElementTest {

    @Test
    public void constructor_nullInputs_throwException() {
        final byte[] idOneByte = new byte[] {(byte)0xEC, (byte)0x81};
        final byte[] data = new byte[]{(byte) 0x00};
        Assertions.assertThrows(NullPointerException.class,
                ()->new MkvDataRawElement(null, ByteBuffer.wrap(data)));

        Assertions.assertThrows(NullPointerException.class,
                ()->new MkvDataRawElement(ByteBuffer.wrap(idOneByte), null));
    }

    @Test
    public void constructor_validInputs_validDataLenAndContent() {
        final byte[] data = new byte[]{(byte) 0x00};
        MkvDataRawElement dataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xEC, (byte) 0x81}),
                ByteBuffer.wrap(data)
        );

        Assertions.assertTrue(Arrays.equals(data, dataElement.getData()));
    }

    @Test
    public void constructor_validId_parseIdCorrectly() {
        final byte[] data = new byte[]{(byte) 0x00};

        final byte[] idOneByte = new byte[] {(byte)0xEC, (byte)0x81};
        final byte[] idOneByteParsed = new byte[] {(byte)0xEC};
        final MkvDataRawElement dataElementIdOneByte = new MkvDataRawElement(
                ByteBuffer.wrap(idOneByte),
                ByteBuffer.wrap(data)
        );
        Assertions.assertTrue(Arrays.equals(idOneByteParsed, dataElementIdOneByte.getIdCopy()));

        final byte[] idTwoBytes = new byte[] {(byte)0x73, (byte)0xA4, (byte)0x81};
        final byte[] idTwoBytesParsed = new byte[] {(byte)0x73, (byte)0xA4};
        final MkvDataRawElement dataElementIdTwoBytes = new MkvDataRawElement(
                ByteBuffer.wrap(idTwoBytes),
                ByteBuffer.wrap(data)
        );
        Assertions.assertTrue(Arrays.equals(idTwoBytesParsed, dataElementIdTwoBytes.getIdCopy()));

        final byte[] idThreeBytes = new byte[] {(byte)0x2A, (byte)0xD7, (byte)0x81, (byte)0x81};
        final byte[] idThreeBytesParsed = new byte[] {(byte)0x2A, (byte)0xD7, (byte)0x81};
        final MkvDataRawElement dataElementIdThreeBytes = new MkvDataRawElement(
                ByteBuffer.wrap(idThreeBytes),
                ByteBuffer.wrap(data)
        );
        Assertions.assertTrue(Arrays.equals(idThreeBytesParsed, dataElementIdThreeBytes.getIdCopy()));

        final byte[] idFourBytes = new byte[] {(byte)0x15, (byte)0x49, (byte)0xA9, (byte)0x66, (byte)0x81};
        final byte[] idFourBytesParsed = new byte[] {(byte)0x15, (byte)0x49, (byte)0xA9, (byte)0x66};
        final MkvDataRawElement dataElementIdFourBytes = new MkvDataRawElement(
                ByteBuffer.wrap(idFourBytes),
                ByteBuffer.wrap(data)
        );
        Assertions.assertTrue(Arrays.equals(idFourBytesParsed, dataElementIdFourBytes.getIdCopy()));
    }

    @Test
    public void constructor_invalidId_zeroSizedId() {
        final byte[] data = new byte[]{(byte) 0x00};
        final byte[] invalidIdParsed = new byte[] {};

        final MkvDataRawElement dataElementEmptyId = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[0]),
                ByteBuffer.wrap(data)
        );
        Assertions.assertTrue(Arrays.equals(invalidIdParsed, dataElementEmptyId.getIdCopy()));

        final MkvDataRawElement dataElementInvalidId = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[] {(byte)0x01, (byte)0x81}),
                ByteBuffer.wrap(data)
        );
        Assertions.assertTrue(Arrays.equals(invalidIdParsed, dataElementInvalidId.getIdCopy()));

        final MkvDataRawElement dataElementNotEnoughLengthId = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[] {(byte)0x15, (byte)0x81}),
                ByteBuffer.wrap(data)
        );
        Assertions.assertTrue(Arrays.equals(invalidIdParsed, dataElementNotEnoughLengthId.getIdCopy()));
    }

    @Test
    public void toMkvRawData_validInputs_validMkvRawElement() {
        final byte[] id = new byte[] {(byte)0xEC, (byte)0x40, (byte)0xFF};
        final byte[] data = new byte[255];
        Arrays.fill(data, (byte)0);

        byte[] answer = new byte[id.length + data.length];
        ByteBuffer buffer = ByteBuffer.wrap(answer);
        buffer.put(id);
        buffer.put(data);

        MkvDataRawElement dataElement = new MkvDataRawElement(
                ByteBuffer.wrap(id),
                ByteBuffer.wrap(data)
        );
        byte[] result = dataElement.toMkvRawData();

        Assertions.assertTrue(Arrays.equals(answer, result));
    }

    @Test
    public void constructor_codecIdWithTrailingZero_codecIdTrimmed() {
        final byte[] codecIdAac = new byte[]{(byte)0x41, (byte)0x5F, (byte)0x41, (byte)0x41, (byte)0x43, (byte)0x00};
        final byte[] codecIdAacTrimmed = new byte[]{(byte)0x41, (byte)0x5F, (byte)0x41, (byte)0x41, (byte)0x43};
        MkvDataRawElement dataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0x86, (byte) 0x86}),
                ByteBuffer.wrap(codecIdAac)
        );

        Assertions.assertTrue(Arrays.equals(codecIdAacTrimmed, dataElement.getData()));
    }

    @Test
    public void constructor_invalidCodecId_dataLenUnchanged() {
        final byte[] codecId = new byte[0];
        MkvDataRawElement dataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0x86, (byte) 0x86}),
                ByteBuffer.wrap(codecId)
        );

        Assertions.assertEquals(codecId.length, dataElement.getDataLen());
    }

    @Test
    public void equals_differentObject_returnFalse() {
        MkvDataRawElement dataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xEC, (byte) 0x81}),
                ByteBuffer.wrap(new byte[]{(byte) 0x00})
        );

        MkvDataRawElement differentIdDataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xBF, (byte) 0x81}),
                ByteBuffer.wrap(new byte[]{(byte) 0x00})
        );
        MkvDataRawElement differentLenDataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xEC, (byte) 0x82}),
                ByteBuffer.wrap(new byte[]{(byte) 0x00, (byte) 0x00})
        );
        MkvDataRawElement differentContentDataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xEC, (byte) 0x81}),
                ByteBuffer.wrap(new byte[]{(byte) 0x01})
        );

        Assertions.assertNotEquals(dataElement, null);
        Assertions.assertNotEquals(dataElement, "other");
        Assertions.assertNotEquals(dataElement, differentIdDataElement);
        Assertions.assertNotEquals(dataElement, differentLenDataElement);
        Assertions.assertNotEquals(dataElement, differentContentDataElement);
    }

    @Test
    public void hashCode_differentObject_returnFalse() {
        MkvDataRawElement dataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xEC, (byte) 0x81}),
                ByteBuffer.wrap(new byte[]{(byte) 0x00})
        );

        MkvDataRawElement differentIdDataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xBF, (byte) 0x81}),
                ByteBuffer.wrap(new byte[]{(byte) 0x00})
        );
        MkvDataRawElement differentLenDataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xEC, (byte) 0x82}),
                ByteBuffer.wrap(new byte[]{(byte) 0x00, (byte) 0x00})
        );
        MkvDataRawElement differentContentDataElement = new MkvDataRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xEC, (byte) 0x81}),
                ByteBuffer.wrap(new byte[]{(byte) 0x01})
        );

        Assertions.assertNotEquals(dataElement.hashCode(), differentIdDataElement.hashCode());
        Assertions.assertNotEquals(dataElement.hashCode(), differentLenDataElement.hashCode());
        Assertions.assertNotEquals(dataElement.hashCode(), differentContentDataElement.hashCode());
    }
}
