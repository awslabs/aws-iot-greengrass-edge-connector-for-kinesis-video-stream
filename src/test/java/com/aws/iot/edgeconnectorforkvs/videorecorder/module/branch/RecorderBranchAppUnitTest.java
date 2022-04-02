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
import java.nio.ByteBuffer;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch.RecorderBranchApp.BranchAppCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecorderBranchAppUnitTest {
    @Mock
    private GstDao mockGst;
    @Mock
    private Pipeline mockPipeline;

    private AppSink.NEW_SAMPLE appsNewSampleListener;

    @Test
    void createBranchAppTest_toggleEmitSignal_noException() {
        RecorderBranchApp branch =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);
        BranchAppCallback listener = bBuf -> {
        };

        branch.registerNewSample(listener);
    }

    @Test
    void createBranchAppTest_bindAndToggleEmitSignal_noException() {
        RecorderBranchApp branch1 =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);
        RecorderBranchApp branch2 =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);
        BranchAppCallback listener = bBuf -> {
        };

        branch1.registerNewSample(listener);
        branch2.registerNewSample(listener);

        // bind then attach
        AppSink mockGstAppSink = mock(AppSink.class);
        Sample mockGstSample = mock(Sample.class);
        Buffer mockGstBuffer = mock(Buffer.class);
        ByteBuffer byteBuffer = null;

        willAnswer(invocation -> {
            appsNewSampleListener = invocation.getArgument(1);
            return null;
        }).given(mockGst).connectAppSink(any(), any(AppSink.NEW_SAMPLE.class));
        willReturn(mockGstSample).given(mockGstAppSink).pullSample();
        willReturn(mockGstBuffer).given(mockGstSample).getBuffer();
        willReturn(byteBuffer).given(mockGstBuffer).map(any(Boolean.class));
        branch1.bind(null, null);
        appsNewSampleListener.newSample(mockGstAppSink);
        willReturn(null).given(mockGstAppSink).pullSample();
        appsNewSampleListener.newSample(mockGstAppSink);
        branch1.unbind();
        appsNewSampleListener.newSample(mockGstAppSink);
        branch1.bind(null, null);
        branch1.unbind();

        // attach then bind
        branch2.bind(null, null);
        branch2.unbind();

        // reattach
        // branch2.bind(null, null);
    }

    @Test
    void getPadTest_invokeMethod_noException() {
        Pad mockPad = mock(Pad.class);
        RecorderBranchApp branch =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);

        Assertions.assertDoesNotThrow(() -> branch.getEntryAudioPad());
        Assertions.assertDoesNotThrow(() -> branch.getEntryVideoPad());
        Assertions.assertDoesNotThrow(() -> branch.relEntryAudioPad(mockPad));
        Assertions.assertDoesNotThrow(() -> branch.relEntryVideoPad(mockPad));
    }
}
