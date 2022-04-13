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

package com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch;

import static org.mockito.BDDMockito.*;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch.RecorderBranchFile.LocCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeInfo;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.freedesktop.gstreamer.event.FlushStartEvent;
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
    @Mock
    private Element mockElm;

    @Test
    void createBranchAppTest_setProperty_noException() {
        willDoNothing().given(this.mockGst).setElement(any(), anyString(), any());
        willReturn(mockElm).given(mockGst).newElement(anyString());
        willThrow(new IllegalArgumentException()).given(this.mockGst).setElement(any(),
                eq("invalid_property"), any());

        RecorderBranchFile branch = new RecorderBranchFile(ContainerType.MATROSKA, this.mockGst,
                this.mockPipeline, PATH_URI);

        Assertions.assertTrue(branch.setProperty("invalid_property", 0));
        branch.bind(null, null);
        Assertions.assertTrue(branch.setProperty("max-size-time", 0));
        Assertions.assertFalse(branch.setProperty("invalid_property", 0));
        branch.unbind();
    }

    @Test
    void getPadTest_invokeMethod_noException() {
        Pad mockPad = mock(Pad.class);
        RecorderBranchFile branch = new RecorderBranchFile(ContainerType.MATROSKA, this.mockGst,
                this.mockPipeline, PATH_URI);

        Assertions.assertDoesNotThrow(() -> branch.getEntryAudioPad());
        Assertions.assertDoesNotThrow(() -> branch.getEntryVideoPad());
        Assertions.assertDoesNotThrow(() -> branch.relEntryAudioPad(mockPad));
        Assertions.assertDoesNotThrow(() -> branch.relEntryVideoPad(mockPad));
    }

    @Test
    void locationFormatSignal_invokeListener_noException() {
        Pad mockPad = mock(Pad.class);
        PadProbeInfo mockProbeInfo = mock(PadProbeInfo.class);
        EOSEvent mockEosEvent = mock(EOSEvent.class);
        FlushStartEvent mockFlushStartEvent = mock(FlushStartEvent.class);

        willAnswer(invocation -> {
            locCallback = invocation.getArgument(2);
            return null;
        }).given(this.mockGst).connectElement(any(), eq("format-location"), any(LocCallback.class));

        RecorderBranchFile branch = new RecorderBranchFile(ContainerType.MATROSKA, this.mockGst,
                this.mockPipeline, PATH_URI);

        branch.setProperty("max-size-time", 10000000L);
        branch.bind(null, null);
        Assertions.assertDoesNotThrow(() -> locCallback.callback(null, 0, null));
        willReturn(mockFlushStartEvent).given(mockProbeInfo).getEvent();
        branch.getPadEosProbe().probeCallback(mockPad, mockProbeInfo);
        willReturn(mockEosEvent).given(mockProbeInfo).getEvent();
        branch.getPadEosProbe().probeCallback(mockPad, mockProbeInfo);
        branch.unbind();

        // Test for exception
        branch.onUnbindBegin();
    }
}
