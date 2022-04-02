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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.CapabilityListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.ErrorListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.NewPadListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@ExtendWith(MockitoExtension.class)
public class RecorderBaseUnitTest {
    @Mock
    private GstDao mockGst;
    @Mock
    private Pipeline mockPipeline;
    @Mock
    private Bus mockBus;
    @Mock
    private Element mockElement;
    @Mock
    private Pad mockPad;
    @Mock
    private PadLinkException mockPadExcept;
    @Mock
    private RecorderBranchBase mockBranch;
    @Mock
    private RecorderCameraBase mockCamera;

    private Bus.ERROR busErrorListener;
    private Bus.WARNING busWarnListener;
    private Bus.EOS busEosListener;
    private CapabilityListener capListener;
    private NewPadListener padListener;
    private ErrorListener errListener;

    private class RecorderTest extends VideoRecorderBase {
        public RecorderTest() {
            super(mockGst, (rec, st, desc) -> {
            });
        }
    }

    private static class RunnableRecorder implements Runnable {
        private RecorderTest recorder;

        public RunnableRecorder(RecorderTest r) {
            recorder = r;
        }

        @Override
        public void run() {
            recorder.start();
        }
    }

    private void waitRecorderUntilNot(VideoRecorderBase recorder, RecorderStatus st) {
        while (recorder.getStatus() == st) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }
    }

    private void waitRecorderUntil(VideoRecorderBase recorder, RecorderStatus st) {
        while (recorder.getStatus() != st) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }
    }

    @BeforeEach
    public void setupTests() {
        busErrorListener = null;
        busWarnListener = null;
        busEosListener = null;
        capListener = null;
        padListener = null;
        errListener = null;

        /* Gst context interface */
        willReturn(mockPipeline).given(mockGst).newPipeline();
        willReturn(mockBus).given(mockGst).getPipelineBus(any(Pipeline.class));
        // Mock Gst Bus connect interface
        willAnswer(invocation -> {
            busErrorListener = invocation.getArgument(1);
            return null;
        }).given(mockGst).connectBus(any(Bus.class), any(Bus.ERROR.class));
        willAnswer(invocation -> {
            busWarnListener = invocation.getArgument(1);
            return null;
        }).given(mockGst).connectBus(any(Bus.class), any(Bus.WARNING.class));
        willAnswer(invocation -> {
            busEosListener = invocation.getArgument(1);
            return null;
        }).given(mockGst).connectBus(any(Bus.class), any(Bus.EOS.class));
        // Mock camera
        willAnswer(invocation -> {
            capListener = invocation.getArgument(0);
            padListener = invocation.getArgument(1);
            errListener = invocation.getArgument(2);
            return null;
        }).given(mockCamera).registerListener(any(CapabilityListener.class),
                any(NewPadListener.class), any(ErrorListener.class));
    }

    @Test
    public void signalBus_invokeListener_exceptionDoesNotThrow() {
        willReturn("mockGstElement").given(mockElement).toString();

        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        // Get dao and pipeline
        Assertions.assertEquals(mockGst, recorder.getGstCore());
        Assertions.assertEquals(mockPipeline, recorder.getPipeline());

        recorder.addCameraSource(mockCamera);
        recordThread.start();
        this.waitRecorderUntilNot(recorder, RecorderStatus.STOPPED);

        // Bus warn: STARTED -> STARTED
        Assertions.assertDoesNotThrow(
                () -> busWarnListener.warningMessage(mockElement, 0, "UT bus warn"));
        // Bus error: STARTED -> FAILED
        Assertions.assertDoesNotThrow(
                () -> busErrorListener.errorMessage(mockElement, 0, "UT bus error"));
        this.waitRecorderUntil(recorder, RecorderStatus.FAILED);
        // Bus error: FAILED -> FAILED
        Assertions.assertDoesNotThrow(
                () -> busErrorListener.errorMessage(mockElement, 1, "UT bus error again"));
        // Bus Eos: FAILED -> STOPPED
        Assertions.assertDoesNotThrow(() -> busEosListener.endOfStream(mockElement));

        recorder.stop();
        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void runRecorder_runAndStop_statusChanged() {
        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        recorder.addCameraSource(mockCamera);
        Assertions.assertEquals(RecorderStatus.STOPPED, recorder.getStatus());

        // Test: start recording
        recordThread.start();
        this.waitRecorderUntil(recorder, RecorderStatus.STARTED);
        recorder.start(); // do nothing if already started

        // Test: stop recording
        recorder.stop();
        this.waitRecorderUntil(recorder, RecorderStatus.STOPPED);
        recorder.stop(); // do nothing if already stopped
        Assertions.assertEquals(RecorderStatus.STOPPED, recorder.getStatus());

        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void runRecorder_willStop_statusChanged() {
        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        recorder.addCameraSource(mockCamera);

        // Test: start recording
        recordThread.start();
        this.waitRecorderUntilNot(recorder, RecorderStatus.STOPPED);

        // Test: trigger bus error
        busErrorListener.errorMessage(mockElement, 0, "UT bus error");
        this.waitRecorderUntil(recorder, RecorderStatus.FAILED);

        // Test: stop recording after failed
        recorder.stop();
        this.waitRecorderUntil(recorder, RecorderStatus.STOPPED);

        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void runRecorder_start_awaitException() {
        try (MockedConstruction<ReentrantLock> mockCondLock =
                mockConstruction(ReentrantLock.class)) {
            RecorderTest recorder = new RecorderTest();
            Thread recordThread = new Thread(new RunnableRecorder(recorder));

            recorder.addCameraSource(mockCamera);
            // Use NullPointerException instead of InterruptException because of JUnit
            // The exception of Condition.await is handle in recorder
            Assertions.assertDoesNotThrow(() -> recordThread.start());
            this.waitRecorderUntil(recorder, RecorderStatus.FAILED);

            try {
                recordThread.join();
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }
    }

    @Test
    public void addCameraSource_registerMockCam_exceptionDoesNotThrow() {
        willThrow(new IllegalArgumentException()).given(mockCamera)
                .setProperty(eq("invalid_property"), any());

        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        // set property when camera is not registered
        Assertions.assertFalse(recorder.setCameraProperty("any", true));
        // register camera
        Assertions.assertThrows(NullPointerException.class, () -> recorder.addCameraSource(null));
        Assertions.assertTrue(recorder.addCameraSource(mockCamera));
        Assertions.assertFalse(recorder.addCameraSource(mockCamera));
        // set property to camera
        Assertions.assertFalse(recorder.setCameraProperty("invalid_property", true));
        Assertions.assertTrue(recorder.setCameraProperty("valid_property", true));
        // Assertions.assertDoesNotThrow(() -> errListener.onError("UT listener err"));

        recordThread.start();
        this.waitRecorderUntilNot(recorder, RecorderStatus.STOPPED);
        recorder.stop();

        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void addBranch_registerMockBranch_exceptionDoesNotThrow() {
        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        recorder.addCameraSource(mockCamera);
        // Get invalid branch
        Assertions.assertNull(recorder.getBranch("MockBranch"));
        // Register branch
        Assertions.assertTrue(recorder.addBranch(mockBranch, "MockBranch", false));
        Assertions.assertFalse(recorder.addBranch(mockBranch, "MockBranch", false));
        Assertions.assertTrue(recorder.bindBranch("MockBranch"));
        // Get valid branch
        Assertions.assertEquals(mockBranch, recorder.getBranch("MockBranch"));

        recordThread.start();
        this.waitRecorderUntilNot(recorder, RecorderStatus.STOPPED);
        recorder.stop();
        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void bindBranch_onCapListener_exceptionDoesNotThrow() {
        // bind video and audio pads
        willReturn(RecorderCapability.VIDEO_AUDIO).given(mockBranch).getCapability();
        willReturn(true).given(mockBranch).isAutoBind();
        RecorderTest recorder1 = new RecorderTest();
        recorder1.addCameraSource(mockCamera);
        recorder1.addBranch(mockBranch, "MockBranch1", true);
        Assertions.assertDoesNotThrow(() -> capListener.onNotify(1, 1));

        // bind video pads
        willReturn(RecorderCapability.VIDEO_ONLY).given(mockBranch).getCapability();
        RecorderTest recorder2 = new RecorderTest();
        recorder2.addCameraSource(mockCamera);
        recorder2.addBranch(mockBranch, "MockBranch2", true);
        Assertions.assertDoesNotThrow(() -> capListener.onNotify(1, 1));
        recorder2.unbindBranch("MockBranch2");
        recorder2.bindBranch("MockBranch2");

        // bind audio pads
        willReturn(RecorderCapability.AUDIO_ONLY).given(mockBranch).getCapability();
        willReturn(false).given(mockBranch).isAutoBind();
        RecorderTest recorder3 = new RecorderTest();
        Thread recordThread3 = new Thread(new RunnableRecorder(recorder3));
        recorder3.addCameraSource(mockCamera);
        recorder3.addBranch(mockBranch, "MockBranch3", false);
        recordThread3.start();
        this.waitRecorderUntil(recorder3, RecorderStatus.STARTED);
        Assertions.assertDoesNotThrow(() -> capListener.onNotify(1, 1));
        recorder3.bindBranch("InvalidBranch");
        recorder3.unbindBranch("InvalidBranch");
        recorder3.bindBranch("MockBranch3");
        recorder3.unbindBranch("MockBranch3");
        recorder3.stop();
        try {
            recordThread3.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void bindCamera_onPadListener_exceptionDoesNotThrow() {
        // willReturn(RecorderCapability.VIDEO_AUDIO).given(mockBranch).getCapability();
        willReturn(false).given(mockGst).isPadLinked(any());

        // bind invalid camera
        RecorderTest recorder1 = new RecorderTest();
        Thread recordThread1 = new Thread(new RunnableRecorder(recorder1));

        recorder1.addCameraSource(mockCamera);
        recorder1.addBranch(mockBranch, "MockBranch", true);
        recordThread1.start();
        this.waitRecorderUntil(recorder1, RecorderStatus.STARTED);
        capListener.onNotify(1, 1);
        Assertions.assertThrows(NullPointerException.class,
                () -> padListener.onNotify(null, mockPad));
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.VIDEO_AUDIO, mockPad));
        this.waitRecorderUntil(recorder1, RecorderStatus.FAILED);
        try {
            recordThread1.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // bind video camera
        RecorderTest recorder2 = new RecorderTest();
        Thread recordThread2 = new Thread(new RunnableRecorder(recorder2));

        recorder2.addCameraSource(mockCamera);
        recorder2.addBranch(mockBranch, "MockBranch", true);
        recordThread2.start();
        this.waitRecorderUntil(recorder2, RecorderStatus.STARTED);
        capListener.onNotify(1, 1);
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockPad));
        willReturn(true).given(mockGst).unlinkPad(any(), any());
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockPad)); // out of range
        this.waitRecorderUntil(recorder2, RecorderStatus.FAILED);
        try {
            recordThread2.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // bind audio camera
        willReturn(true).given(mockGst).isPadLinked(any());
        RecorderTest recorder3 = new RecorderTest();
        Thread recordThread3 = new Thread(new RunnableRecorder(recorder3));

        recorder3.addCameraSource(mockCamera);
        recorder3.addBranch(mockBranch, "MockBranch", true);
        recordThread3.start();
        this.waitRecorderUntil(recorder3, RecorderStatus.STARTED);
        capListener.onNotify(1, 1);
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.AUDIO_ONLY, mockPad));
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.AUDIO_ONLY, mockPad)); // out of range
        this.waitRecorderUntil(recorder3, RecorderStatus.FAILED);
        try {
            recordThread3.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // rebind
        RecorderTest recorder4 = new RecorderTest();
        Thread recordThread4 = new Thread(new RunnableRecorder(recorder4));

        recorder4.addCameraSource(mockCamera);
        recorder4.addBranch(mockBranch, "MockBranch", true);
        recordThread4.start();
        this.waitRecorderUntil(recorder4, RecorderStatus.STARTED);
        capListener.onNotify(1, 1);
        willReturn(false).given(mockGst).unlinkPad(any(), any());
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockPad));
        // link failed and PadLinkException is handled in the recorder
        willThrow(mockPadExcept).given(mockGst).linkPad(any(), any());
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.AUDIO_ONLY, mockPad));
        this.waitRecorderUntil(recorder4, RecorderStatus.FAILED);
        try {
            recordThread4.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // no camera
        RecorderTest recorder5 = new RecorderTest();
        Thread recordThread5 = new Thread(new RunnableRecorder(recorder5));

        recordThread5.start();
        this.waitRecorderUntil(recorder5, RecorderStatus.STARTED);
        recorder5.stop();
        try {
            recordThread5.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // camera error
        RecorderTest recorder6 = new RecorderTest();
        Thread recordThread6 = new Thread(new RunnableRecorder(recorder6));

        recorder6.addCameraSource(mockCamera);
        recordThread6.start();
        this.waitRecorderUntil(recorder6, RecorderStatus.STARTED);
        errListener.onError("test cam error");
        this.waitRecorderUntil(recorder6, RecorderStatus.FAILED);
        try {
            recordThread6.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }
}
