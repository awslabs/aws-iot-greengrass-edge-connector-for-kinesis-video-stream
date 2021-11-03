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

package com.aws.iot.edgeconnectorforkvs.videorecorder.base;

import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecorderCameraBaseUnitTest {
    @Mock
    private GstDao mockGst;
    @Mock
    private Pipeline mockPipeline;

    private RecorderCameraBase.CapabilityListener capListener;
    private RecorderCameraBase.NewPadListener padListener;
    private RecorderCameraBase.ErrorListener errListener;

    private class RecorderCameraTest extends RecorderCameraBase {
        public RecorderCameraTest() {
            super(mockGst, mockPipeline);
        }

        @Override
        public void setProperty(String property, Object val) {

        }
    }

    @BeforeEach
    void setupTest() {
        this.capListener = (audioCnt, videoCnt) -> {
        };

        this.padListener = (cap, newPad) -> {
        };

        this.errListener = desc -> {
        };
    }

    @Test
    void registerListener_nullListener_throwException() {
        RecorderCameraTest camera = new RecorderCameraTest();

        Assertions.assertThrows(NullPointerException.class,
                () -> camera.registerListener(null, null, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> camera.registerListener(this.capListener, null, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> camera.registerListener(null, this.padListener, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> camera.registerListener(this.capListener, this.padListener, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> camera.registerListener(null, null, this.errListener));
        Assertions.assertThrows(NullPointerException.class,
                () -> camera.registerListener(this.capListener, null, this.errListener));
        Assertions.assertThrows(NullPointerException.class,
                () -> camera.registerListener(null, this.padListener, this.errListener));
    }
}
