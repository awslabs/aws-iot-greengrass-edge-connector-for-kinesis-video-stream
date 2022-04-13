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

import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import static org.mockito.BDDMockito.*;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.PadProbeInfo;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Pad.PROBE;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecorderBranchBaseUnitTest {
    @Mock
    private GstDao mockGst;
    @Mock
    private Pipeline mockPipeline;
    @Mock
    private Element mockMuxer;
    @Mock
    private Pad mockAudioPad;
    @Mock
    private Pad mockVideoPad;
    @Mock
    private Element mockTee;
    @Mock
    private Element mockTeeDup;
    @Mock
    private Pad mockTeePad;
    @Mock
    private Pad mockTeePadDup;
    @Mock
    private Pad mockQuePad;
    @Mock
    private PadProbeInfo mockProbeInfo;
    @Mock
    private InterruptedException mockInterruptExcept;
    @Mock
    private PadLinkException mockPadLinkExcept;

    private class RecorderBranchTest extends RecorderBranchBase {
        public RecorderBranchTest(RecorderCapability cap) {
            super(cap, mockGst, mockPipeline);
        }

        @Override
        public Pad getEntryAudioPad() {
            return mockAudioPad;
        }

        @Override
        public Pad getEntryVideoPad() {
            return mockVideoPad;
        }

        @Override
        public void relEntryAudioPad(Pad pad) {
            // do nothing
        }

        @Override
        public void relEntryVideoPad(Pad pad) {
            // do nothing
        }

        public GstDao getGstDao() {
            return this.getGstCore();
        }

        public Pipeline getPipe() {
            return this.getPipeline();
        }

        public Element getMuxer(ContainerType type, boolean isFilePath) {
            return this.getMuxerFromType(type, isFilePath);
        }

        public String getExtension(ContainerType type) {
            return this.getFileExtensionFromType(type);
        }

        public PROBE getProbeTee() {
            return this.getTeeBlockProbe();
        }
    }

    @Test
    public void getMembersTest_invokeGetter_nonNullValue() {
        RecorderCapability cap = RecorderCapability.VIDEO_AUDIO;
        RecorderBranchTest testBranch = new RecorderBranchTest(cap);

        Assertions.assertEquals(cap, testBranch.getCapability());
        Assertions.assertNotNull(testBranch.getGstDao());
        Assertions.assertNotNull(testBranch.getPipe());
        testBranch.setAutoBind(true);
        Assertions.assertTrue(testBranch.isAutoBind());
    }

    @Test
    public void getMuxerTest_getMuxerByType_nonNullValue() {
        willReturn(this.mockMuxer).given(this.mockGst).newElement(anyString());

        RecorderCapability cap = RecorderCapability.VIDEO_AUDIO;
        ContainerType type = ContainerType.MATROSKA;
        RecorderBranchTest testBranch = new RecorderBranchTest(cap);

        Assertions.assertNotNull(testBranch.getMuxer(type, true));
        Assertions.assertNotNull(testBranch.getMuxer(type, false));
    }

    @Test
    public void getMuxerTest_getMuxerByInvalidType_throwException() {
        RecorderCapability cap = RecorderCapability.VIDEO_AUDIO;
        ContainerType type = ContainerType.UNSUPPORTED;
        RecorderBranchTest testBranch = new RecorderBranchTest(cap);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> testBranch.getMuxer(type, true));
    }

    @Test
    public void getExtensionTest_getExtensionByType_mkv() {
        RecorderCapability cap = RecorderCapability.VIDEO_AUDIO;
        ContainerType type = ContainerType.MATROSKA;
        RecorderBranchTest testBranch = new RecorderBranchTest(cap);

        Assertions.assertEquals("mkv", testBranch.getExtension(type));
    }

    @Test
    public void getExtensionTest_getExtensionByInvalidType_throwException() {
        RecorderCapability cap = RecorderCapability.VIDEO_AUDIO;
        ContainerType type = ContainerType.UNSUPPORTED;
        RecorderBranchTest testBranch = new RecorderBranchTest(cap);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> testBranch.getExtension(type));
    }

    @Test
    public void bindBranchTest_bind_noExceptionThrow() {
        RecorderBranchTest testBranchFull1 = new RecorderBranchTest(RecorderCapability.VIDEO_AUDIO);
        RecorderBranchTest testBranchFull2 = new RecorderBranchTest(RecorderCapability.VIDEO_AUDIO);
        RecorderBranchTest testBranchVideo = new RecorderBranchTest(RecorderCapability.VIDEO_ONLY);
        RecorderBranchTest testBranchAudio = new RecorderBranchTest(RecorderCapability.AUDIO_ONLY);
        ArrayList<Element> teeVideos = new ArrayList<>();
        ArrayList<Element> teeAudios = new ArrayList<>();

        teeVideos.add(mockTee);
        teeAudios.add(mockTee);

        Assertions.assertDoesNotThrow(() -> testBranchFull1.bind(null, null));
        Assertions.assertDoesNotThrow(() -> testBranchFull1.bind(null, null)); // duplicated bind
        Assertions.assertDoesNotThrow(() -> testBranchFull2.bind(teeVideos, teeAudios));
        Assertions.assertDoesNotThrow(() -> testBranchVideo.bind(teeVideos, teeAudios));
        Assertions.assertDoesNotThrow(() -> testBranchAudio.bind(teeVideos, teeAudios));
    }

    @Test
    public void bind_unbindAfterBind_noException() {
        willReturn(mockQuePad).given(mockGst).getElementStaticPad(any(), anyString());
        willReturn(mockTeePad).given(mockGst).getElementRequestPad(eq(mockTee), anyString());
        willReturn(mockTeePadDup).given(mockGst).getElementRequestPad(eq(mockTeeDup), anyString());

        RecorderBranchTest testBranch = new RecorderBranchTest(RecorderCapability.VIDEO_AUDIO);
        ArrayList<Element> teeVideos = new ArrayList<>();
        ArrayList<Element> teeAudios = new ArrayList<>();
        Thread th = null;

        teeVideos.add(mockTee);
        teeAudios.add(mockTeeDup);
        testBranch.bind(teeVideos, teeAudios);

        Runnable padIdle = () -> {
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
            testBranch.getProbeTee().probeCallback(mockTeePad, mockProbeInfo);
            testBranch.getProbeTee().probeCallback(mockTeePadDup, mockProbeInfo);
        };
        willReturn(true).given(mockGst).unlinkPad(any(), any());
        th = new Thread(padIdle);
        th.start();
        testBranch.unbind();
        testBranch.unbind(); // duplicated unbind
        try {
            th.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // link failed
        willThrow(mockPadLinkExcept).given(mockGst).linkPad(any(), any());
        testBranch.bind(teeVideos, teeAudios);
        // unlink fail
        willReturn(false).given(mockGst).unlinkPad(any(), any());
        th = new Thread(padIdle);
        th.start();
        testBranch.unbind();
        try {
            th.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void unbind_waitForDeattach_awaitException() {
        try (MockedConstruction<CountDownLatch> mockLatch =
                mockConstruction(CountDownLatch.class, (mock, context) -> {
                    willThrow(mockInterruptExcept).given(mock).await();
                })) {
            willReturn(mockQuePad).given(mockGst).getElementStaticPad(any(), anyString());

            RecorderBranchTest testBranch = new RecorderBranchTest(RecorderCapability.VIDEO_AUDIO);
            ArrayList<Element> teeVideos = new ArrayList<>();

            teeVideos.add(mockTee);

            testBranch.bind(teeVideos, null);

            // The exception of deattachCnt.await is handle in BranchBase
            Assertions.assertDoesNotThrow(() -> testBranch.unbind());
        }
    }
}
