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

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

/**
 * It's a subclass of {@link MkvRawElement}.  It's the leaf element of MKV.  It contains data field and has no children
 * elements.
 */
@Slf4j
public class MkvDataRawElement extends MkvRawElement {

    private byte[] data;

    /**
     * The constructor.
     *
     * @param idAndSizeByteBuffer The raw data to initialize ID field
     * @param dataByteBuffer      The raw data of data field
     */
    public MkvDataRawElement(ByteBuffer idAndSizeByteBuffer, @NonNull ByteBuffer dataByteBuffer) {
        super(idAndSizeByteBuffer);

        dataByteBuffer.rewind();
        data = new byte[dataByteBuffer.remaining()];
        try {
            dataByteBuffer.get(data);
        } catch (BufferUnderflowException ex) {
            log.error("Buffer Underflow Exception: " + ex.getMessage());
        }

        // MKV CodecID element's header is 0x86, so we check its length and its value here.
        if (this.getIdCopy().length == 1 && this.getIdCopy()[0] == (byte) 0x86) {
            // It's CodecID element. Remove any end of string character '\0' if any.
            int len = data.length;
            while (len > 0 && data[len - 1] == '\0') {
                len--;
            }
            if (len != data.length) {
                data = Arrays.copyOf(data, len);
            }
        }

        this.setDataLen(data.length);
    }

    /**
     * Return a copy of data.
     *
     * @return data
     */
    public byte[] getData() {
        return data.clone();
    }

    /**
     * Convert this MKV raw element into byte array.
     *
     * @return The MKV elements in byte array
     */
    @Override
    public byte[] toMkvRawData() {
        final byte[] size = getMkvSize(this.getDataLen());
        final byte[] raw = new byte[this.getIdCopy().length + size.length + data.length];
        final ByteBuffer buffer = ByteBuffer.wrap(raw);
        try {
            buffer.put(this.getIdCopy());
            buffer.put(size);
            buffer.put(data);
        } catch (BufferOverflowException ex) {
            log.error("Buffer Overflow Exception: " + ex.getMessage());
        } catch (ReadOnlyBufferException ex) {
            log.error("Readonly Buffer Exception: " + ex.getMessage());
        }
        return buffer.array();
    }

    /**
     * Compare MKV element with their ID, length, and data (if available).
     *
     * @param otherObj The other MKV element to be compared
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
        MkvDataRawElement other = (MkvDataRawElement) otherObj;
        return new EqualsBuilder()
                .append(this.getIdCopy(), other.getIdCopy())
                .append(this.getDataLen(), other.getDataLen())
                .append(this.data, other.getData())
                .isEquals();
    }

    /**
     * Return hash code of this element.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(this.getIdCopy())
                .append(this.getDataLen())
                .append(data)
                .toHashCode();
    }
}
