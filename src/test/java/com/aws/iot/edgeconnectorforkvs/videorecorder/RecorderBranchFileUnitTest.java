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

package com.aws.iot.edgeconnectorforkvs.videorecorder;

import static org.mockito.BDDMockito.*;
import com.aws.iot.edgeconnectorforkvs.videorecorder.RecorderBranchFile.LocCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecorderBranchFileUnitTest {
    private static final String PATH_URI = "/test";
    private LocCallback locCallback;

    @Mock
    private GstDao mockGst;
    @Mock
    private Pipeline mockPipeline;

    @Test
    void createBranchAppTest_setProperty_noException() {
        willDoNothing().given(this.mockGst).setElement(any(), anyString(), any());
        willThrow(new IllegalArgumentException()).given(this.mockGst).setElement(any(),
                eq("invalid_property"), any());

        RecorderBranchFile branch = new RecorderBranchFile(ContainerType.MATROSKA, this.mockGst,
                this.mockPipeline, PATH_URI);

        Assertions.assertTrue(branch.setProperty("max-size-time", 0));
        Assertions.assertFalse(branch.setProperty("invalid_property", 0));
    }

    @Test
    void getPadTest_invokeMethod_noException() {
        RecorderBranchFile branch = new RecorderBranchFile(ContainerType.MATROSKA, this.mockGst,
                this.mockPipeline, PATH_URI);

        Assertions.assertDoesNotThrow(() -> branch.getEntryAudioPad());
        Assertions.assertDoesNotThrow(() -> branch.getEntryVideoPad());
    }

    @Test
    void locationFormatSignal_invokeListener_noException() {
        willAnswer(invocation -> {
            locCallback = invocation.getArgument(2);
            return null;
        }).given(this.mockGst).connectElement(any(), eq("format-location"), any(LocCallback.class));

        RecorderBranchFile branch = new RecorderBranchFile(ContainerType.MATROSKA, this.mockGst,
                this.mockPipeline, PATH_URI);

        branch.setProperty("max-size-time", 10000000L);
        Assertions.assertDoesNotThrow(() -> locCallback.callback(null, 0, null));
    }
}
