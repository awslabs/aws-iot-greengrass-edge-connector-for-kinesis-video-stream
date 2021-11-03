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

import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
    private RecorderCameraBase.CapabilityListener capListener;
    private RecorderCameraBase.NewPadListener padListener;
    private RecorderCameraBase.ErrorListener errListener;

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
            recorder.startRecording();
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
        }).given(mockCamera).registerListener(any(RecorderCameraBase.CapabilityListener.class),
                any(RecorderCameraBase.NewPadListener.class), any(RecorderCameraBase.ErrorListener.class));
    }

    @Test
    public void signalBus_invokeListener_exceptionDoesNotThrow() {
        willReturn("mockGstElement").given(mockElement).toString();

        RecorderTest recorder = new RecorderTest();

        // Get dao and pipeline
        Assertions.assertEquals(mockGst, recorder.getGstCore());
        Assertions.assertEquals(mockPipeline, recorder.getPipeline());

        recorder.registerCamera(mockCamera);
        // Bus warn
        Assertions.assertDoesNotThrow(
                () -> busWarnListener.warningMessage(mockElement, 0, "UT bus warn"));
        // Bus error
        Assertions.assertDoesNotThrow(
                () -> busErrorListener.errorMessage(mockElement, 0, "UT bus error"));
        Assertions.assertDoesNotThrow(
                () -> busErrorListener.errorMessage(mockElement, 1, "UT bus error again"));
        // Bus Eos
        Assertions.assertDoesNotThrow(() -> busEosListener.endOfStream(mockElement));
    }

    @Test
    public void runRecorder_runAndStop_statusChanged() {
        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        recorder.registerCamera(mockCamera);
        Assertions.assertEquals(RecorderStatus.STOPPED, recorder.getStatus());

        // Test: start recording
        recordThread.start();
        try {
            while (recorder.getStatus() == RecorderStatus.STOPPED) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            Assertions.fail();
        }
        Assertions.assertEquals(RecorderStatus.STARTED, recorder.getStatus());
        recorder.startRecording(); // do nothing if already started

        // Test: stop recording
        recorder.stopRecording();
        busEosListener.endOfStream(mockElement);
        try {
            while (recorder.getStatus() != RecorderStatus.STOPPED) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            Assertions.fail();
        }
        recorder.stopRecording(); // do nothing if already stopped
        Assertions.assertEquals(RecorderStatus.STOPPED, recorder.getStatus());
    }

    @Test
    public void runRecorder_willStop_statusChanged() {
        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        recorder.registerCamera(mockCamera);

        // Test: start recording
        recordThread.start();
        try {
            while (recorder.getStatus() == RecorderStatus.STOPPED) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // Test: trigger bus error
        busErrorListener.errorMessage(mockElement, 0, "UT bus error");
        try {
            while (recorder.getStatus() != RecorderStatus.FAILED) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            Assertions.fail();
        }
        Assertions.assertEquals(RecorderStatus.FAILED, recorder.getStatus());

        // Test: stop recording after failed
        recorder.stopRecording();
        busEosListener.endOfStream(mockElement);
        Assertions.assertEquals(RecorderStatus.STOPPED, recorder.getStatus());
    }

    @Test
    public void runRecorder_startRecording_awaitException() {
        try (MockedConstruction<ReentrantLock> mockCondLock =
                mockConstruction(ReentrantLock.class)) {
            RecorderTest recorder = new RecorderTest();
            Thread recordThread = new Thread(new RunnableRecorder(recorder));

            recorder.registerCamera(mockCamera);
            // Use NullPointerException instead of InterruptException because of JUnit
            // The exception of Condition.await is handle in recorder
            Assertions.assertDoesNotThrow(() -> recordThread.start());
            try {
                while (recorder.getStatus() != RecorderStatus.FAILED) {
                    TimeUnit.SECONDS.sleep(1);
                }
            } catch (InterruptedException e) {
                Assertions.fail();
            }
            Assertions.assertEquals(RecorderStatus.FAILED, recorder.getStatus());
            // Test: start after failed
            recorder.startRecording();
        }
    }

    @Test
    public void setStatus_setSameStatus_doNothing() {
        RecorderTest recorder = new RecorderTest();
        Thread recordThread = new Thread(new RunnableRecorder(recorder));

        // we just want to make the recorder status to STARTED, then terminate the thread
        recorder.registerCamera(mockCamera);
        recordThread.start();
        try {
            while (recorder.getStatus() != RecorderStatus.STARTED) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (InterruptedException e) {
            Assertions.fail();
        }
        recordThread.interrupt();

        // Test: trigger STARTED -> FAILED -> FAILED
        busErrorListener.errorMessage(mockElement, 0, "UT bus error");
        busErrorListener.errorMessage(mockElement, 0, "UT bus error");
    }

    @Test
    public void registerCamera_registerMockCam_exceptionDoesNotThrow() {
        willThrow(new IllegalArgumentException()).given(mockCamera)
                .setProperty(eq("invalid_property"), any());

        RecorderTest recorder = new RecorderTest();

        // set property when camera is not registered
        Assertions.assertFalse(recorder.setCameraProperty("any", true));
        // register camera
        Assertions.assertThrows(NullPointerException.class, () -> recorder.registerCamera(null));
        Assertions.assertTrue(recorder.registerCamera(mockCamera));
        Assertions.assertFalse(recorder.registerCamera(mockCamera));
        // set property to camera
        Assertions.assertFalse(recorder.setCameraProperty("invalid_property", true));
        Assertions.assertTrue(recorder.setCameraProperty("valid_property", true));
        Assertions.assertDoesNotThrow(() -> errListener.onError("UT listener err"));
    }

    @Test
    public void registerBranch_registerMockBranch_exceptionDoesNotThrow() {
        RecorderTest recorder = new RecorderTest();

        recorder.registerCamera(mockCamera);
        // Get invalid branch
        Assertions.assertNull(recorder.getBranch("MockBranch"));
        // Register branch
        Assertions.assertTrue(recorder.registerBranch(mockBranch, "MockBranch"));
        Assertions.assertFalse(recorder.registerBranch(mockBranch, "MockBranch"));
        // Get valid branch
        Assertions.assertEquals(mockBranch, recorder.getBranch("MockBranch"));
    }

    @Test
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void bindBranch_onCapListener_exceptionDoesNotThrow() {
        // bind video and audio pads
        willReturn(RecorderCapability.VIDEO_AUDIO).given(mockBranch).getCapability();
        RecorderTest recorder1 = new RecorderTest();
        recorder1.registerCamera(mockCamera);
        recorder1.registerBranch(mockBranch, "MockBranch");
        Assertions.assertDoesNotThrow(() -> capListener.onNotify(1, 1));
        Assertions.assertDoesNotThrow(() -> capListener.onNotify(1, 1)); // do nothing

        // bind video pads
        willReturn(RecorderCapability.VIDEO_ONLY).given(mockBranch).getCapability();
        RecorderTest recorder2 = new RecorderTest();
        recorder2.registerCamera(mockCamera);
        recorder2.registerBranch(mockBranch, "MockBranch");
        Assertions.assertDoesNotThrow(() -> capListener.onNotify(1, 1));

        // bind audio pads
        willReturn(RecorderCapability.AUDIO_ONLY).given(mockBranch).getCapability();
        RecorderTest recorder3 = new RecorderTest();
        recorder3.registerCamera(mockCamera);
        recorder3.registerBranch(mockBranch, "MockBranch");
        Assertions.assertDoesNotThrow(() -> capListener.onNotify(1, 1));
    }

    @Test
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    public void bindCamera_onPadListener_exceptionDoesNotThrow() {
        willReturn(RecorderCapability.VIDEO_AUDIO).given(mockBranch).getCapability();
        willReturn(false).given(mockGst).isPadLinked(any());

        // bind invalid camera
        RecorderTest recorder1 = new RecorderTest();
        recorder1.registerCamera(mockCamera);
        recorder1.registerBranch(mockBranch, "MockBranch");
        capListener.onNotify(1, 1);
        Assertions.assertThrows(NullPointerException.class,
                () -> padListener.onNotify(null, mockPad));
        Assertions.assertThrows(RejectedExecutionException.class,
                () -> padListener.onNotify(RecorderCapability.VIDEO_AUDIO, mockPad));

        // bind video camera
        RecorderTest recorder2 = new RecorderTest();
        recorder2.registerCamera(mockCamera);
        recorder2.registerBranch(mockBranch, "MockBranch");
        capListener.onNotify(1, 1);
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockPad));
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockPad)); // out of range


        // bind audio camera
        RecorderTest recorder3 = new RecorderTest();
        recorder3.registerCamera(mockCamera);
        recorder3.registerBranch(mockBranch, "MockBranch");
        capListener.onNotify(1, 1);
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.AUDIO_ONLY, mockPad));
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.AUDIO_ONLY, mockPad)); // out of range

        // rebind
        willReturn(true).given(mockGst).isPadLinked(any());

        RecorderTest recorder4 = new RecorderTest();
        recorder4.registerCamera(mockCamera);
        recorder4.registerBranch(mockBranch, "MockBranch");
        capListener.onNotify(1, 1);
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockPad));
        // link failed and PadLinkException is handled in recorder
        willThrow(mockPadExcept).given(mockGst).linkPad(any(), any());
        Assertions.assertDoesNotThrow(
                () -> padListener.onNotify(RecorderCapability.AUDIO_ONLY, mockPad));
    }
}
