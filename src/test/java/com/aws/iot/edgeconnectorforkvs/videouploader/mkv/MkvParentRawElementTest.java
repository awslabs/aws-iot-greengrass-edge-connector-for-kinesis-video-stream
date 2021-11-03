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

public class MkvParentRawElementTest {

    @Test
    public void equals_sameSettings_returnTrue() {
        MkvParentRawElement parentElement = new MkvParentRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xAE, (byte) 0x81})
        );
        MkvParentRawElement sameIdParentElement = new MkvParentRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xAE, (byte) 0x81})
        );

        Assertions.assertEquals(parentElement, sameIdParentElement);
    }

    @Test
    public void equals_differentObject_returnFalse() {
        MkvParentRawElement parentElement = new MkvParentRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xAE, (byte) 0x81})
        );
        MkvParentRawElement differentIdParentElement = new MkvParentRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xD7, (byte) 0x81})
        );

        Assertions.assertNotEquals(parentElement, null);
        Assertions.assertNotEquals(parentElement, "other");
        Assertions.assertNotEquals(parentElement, differentIdParentElement);
    }

    @Test
    public void hashCode_differentObject_returnFalse() {
        MkvParentRawElement parentElement = new MkvParentRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xAE, (byte) 0x81})
        );
        MkvParentRawElement differentIdParentElement = new MkvParentRawElement(
                ByteBuffer.wrap(new byte[]{(byte) 0xD7, (byte) 0x81})
        );

        Assertions.assertNotEquals(parentElement.hashCode(), differentIdParentElement.hashCode());
    }
}
