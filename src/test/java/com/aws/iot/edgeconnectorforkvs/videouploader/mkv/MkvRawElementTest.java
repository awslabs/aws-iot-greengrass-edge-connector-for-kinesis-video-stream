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

public class MkvRawElementTest {

    @Test
    public void getMkvSize_validInput_returnMkvSize() {
        byte[] zeroSizedArray = new byte[]{(byte) 0x88};
        byte[] result = MkvDataRawElement.getMkvSize(8L);
        Assertions.assertArrayEquals(zeroSizedArray, result);
    }

    @Test
    public void getMkvSize_exceedMaxLimit_returnZeroSizedArray() {
        byte[] zeroSizedArray = new byte[0];
        byte[] result = MkvDataRawElement.getMkvSize(0x01000000_00000000L);
        Assertions.assertArrayEquals(zeroSizedArray, result);
    }
}
