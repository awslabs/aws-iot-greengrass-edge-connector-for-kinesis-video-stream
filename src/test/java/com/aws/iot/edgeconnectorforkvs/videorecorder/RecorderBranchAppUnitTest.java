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

import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.AppSink.NEW_SAMPLE;
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

    @Test
    void createBranchAppTest_toggleEmitSignal_noException() {
        RecorderBranchApp branch =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);
        NEW_SAMPLE listener = sink -> {
            return FlowReturn.OK;
        };

        branch.registerNewSample(listener);
        Assertions.assertFalse(branch.isEmitEnabled());

        // Already disabled
        Assertions.assertFalse(branch.toggleEmit(false));
        Assertions.assertFalse(branch.isEmitEnabled());

        // From disabled to enabled
        Assertions.assertTrue(branch.toggleEmit(true));
        Assertions.assertTrue(branch.isEmitEnabled());

        // Already enabled
        Assertions.assertFalse(branch.toggleEmit(true));
        Assertions.assertTrue(branch.isEmitEnabled());

        // From enabled to disabled
        Assertions.assertTrue(branch.toggleEmit(false));
        Assertions.assertFalse(branch.isEmitEnabled());
    }

    @Test
    void createBranchAppTest_bindAndToggleEmitSignal_noException() {
        RecorderBranchApp branch1 =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);
        RecorderBranchApp branch2 =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);
        NEW_SAMPLE listener = sink -> {
            return FlowReturn.OK;
        };

        branch1.registerNewSample(listener);
        branch2.registerNewSample(listener);

        // bind then attach
        branch1.bindPaths(null, null);
        Assertions.assertTrue(branch1.toggleEmit(true));
        Assertions.assertTrue(branch1.isEmitEnabled());

        // attach then bind
        Assertions.assertTrue(branch2.toggleEmit(true));
        branch2.bindPaths(null, null);
        Assertions.assertTrue(branch2.isEmitEnabled());

        // reattach
        Assertions.assertTrue(branch2.toggleEmit(false));
        Assertions.assertTrue(branch2.toggleEmit(true));
        Assertions.assertTrue(branch2.isEmitEnabled());
    }

    @Test
    void getPadTest_invokeMethod_noException() {
        RecorderBranchApp branch =
                new RecorderBranchApp(ContainerType.MATROSKA, this.mockGst, this.mockPipeline);

        Assertions.assertDoesNotThrow(() -> branch.getEntryAudioPad());
        Assertions.assertDoesNotThrow(() -> branch.getEntryVideoPad());
    }
}
