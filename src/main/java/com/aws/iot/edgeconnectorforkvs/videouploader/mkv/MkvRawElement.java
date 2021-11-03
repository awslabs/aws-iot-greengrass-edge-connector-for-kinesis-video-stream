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

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The super class of {@link MkvParentRawElement} and {@link MkvDataRawElement}.
 */
@Slf4j
public abstract class MkvRawElement {

    private final byte[] id;

    @Setter
    @Getter
    private long dataLen = 0;

    @Setter
    @Getter
    private MkvParentRawElement parent = null;

    /**
     * The size of MKV size field is determined by adding one to the zero counts before the first '1' bit. And the
     * rest of bits can be used to representing the data size.  If all bits are 1, then it indicates the length is
     * undetermined.
     *      Ex. 0x85 = B'1000_1001. The zero counts before the first '1' bit is 0. So the length of size field is 1.
     *      The data size is represented by remaining bits, which is B'0000_1001 = 0x05.
     *      Ex. 0x40005 = B'0100_0000, B'0000_1001, The zero counts before the first '1' bit is 1, So the length of
     *      size field is 2, The remaining bits are B'0000_0000, B'0000_1001, so the data size is 0x0005;
     *      Ex. 0xFF = B'1111_1111, It shows the data size is undetermined.
     *      Ex. 0x1FFFFFFF, It shows the data size is undetermined.
     */
    private static final long[] SIZE_LIMITS = {
            0x80L - 1,                  // 1 bytes for size field, data size range 1 ~ 0x7F
            0x4000 - 1,                 // 2 bytes for size field, data size range 1 ~ 0x3FFE
            0x200000L - 1,              // 3 bytes for size field, data size range 1 ~ 0x1FFFFE
            0x10000000L - 1,            // 4 bytes for size field, data size range 1 ~ 0x0FFFFFFE
            0x08000000_00L - 1,         // 5 bytes for size field, data size range 1 ~ 0x07FFFFFF_FE
            0x04000000_0000L - 1,       // 6 bytes for size field, data size range 1 ~ 0x03FFFFFF_FFFE
            0x02000000_000000L - 1,     // 7 bytes for size field, data size range 1 ~ 0x01FFFFFF_FFFFFE
            0x01000000_00000000L - 1    // 8 bytes for size field, data size range 1 ~ 0x00FFFFFF_FFFFFFFE
    };

    /**
     * The constructor.
     *
     * @param idAndSizeByteBuffer The buffer that stores ID of this element
     */
    public MkvRawElement(@NonNull ByteBuffer idAndSizeByteBuffer) {
        idAndSizeByteBuffer.rewind();
        final byte[] idAndSize = new byte[idAndSizeByteBuffer.remaining()];
        try {
            idAndSizeByteBuffer.get(idAndSize);
        } catch (BufferUnderflowException ex) {
            log.error("Buffer Underflow Exception: " + ex.getMessage());
        }

        id = getMkvId(idAndSize);
    }

    /**
     * Provide the first byte of MKV ID field, return the length of ID field.
     *
     * @param b First byte of MKV ID field
     * @return Length of the ID field.
     */
    private int getMkvIdLen(byte b) {
        /**
         * The size of ID field is determined by adding one to the count of zeros before first '1' bit.  The max size
         * of ID field specified by MKV is 4.
         */
        if ((b & 0x80) != 0) {          // No zero before first '1' bit, the ID size is 1.
            return 1;
        } else if ((b & 0x40) != 0) {   // 1 zero before first '1' bit, the ID size is 2.
            return 2;
        } else if ((b & 0x20) != 0) {   // 2 zeros before first '1' bit, the ID size is 3.
            return 3;
        } else if ((b & 0x10) != 0) {   // 3 zeros before first '1' bit, the ID size is 4.
            return 4;
        }
        return 0;
    }

    /**
     * Return a copy of id.
     *
     * @return id
     */
    public byte[] getIdCopy() {
        return id.clone();
    }

    /**
     * Get MKV ID field from raw data.
     *
     * @param rawData The raw data that contains ID field
     * @return ID field if succeed, or zero-sized byte array otherwise
     */
    protected byte[] getMkvId(byte[] rawData) {
        if (rawData.length == 0) {
            return new byte[0];
        }
        final int idLen = getMkvIdLen(rawData[0]);
        if (rawData.length < idLen) {
            return new byte[0];
        }
        return Arrays.copyOf(rawData, idLen);
    }

    /**
     * Get MKV size field from data length.
     *
     * @param len The length of data.
     * @return Size field if succeed, or zero-sized byte array otherwise
     */
    public static byte[] getMkvSize(long len) {
        int sizeLen = 0;
        for (int i = 0; i < SIZE_LIMITS.length; i++) {
            if (len < SIZE_LIMITS[i]) {
                sizeLen = i + 1;
                break;
            }
        }

        if (sizeLen == 0) {
            return new byte[0];
        }

        byte[] rawData = new byte[sizeLen];
        int remainingLen = sizeLen;
        while (remainingLen > 1) {
            rawData[remainingLen - 1] = (byte) len;
            len = len >> 8;
            remainingLen--;
        }
        byte leadingByte = (byte) (1 << (8 - sizeLen));
        rawData[0] = (byte) ((byte) len & (byte) (leadingByte - 1));
        rawData[0] |= leadingByte;
        return rawData;
    }

    /**
     * Convert this MKV raw element into byte array.
     *
     * @return The MKV element in byte array
     */
    public abstract byte[] toMkvRawData();

    /**
     * Compare MKV element with their ID, length, and data (if available).
     *
     * @param otherObj The other MKV element to be compared
     * @return True if they are equal, false otherwise
     */
    @Override
    public abstract boolean equals(Object otherObj);

    /**
     * Return hash code of this element.
     *
     * @return The hash code
     */
    @Override
    public abstract int hashCode();
}
