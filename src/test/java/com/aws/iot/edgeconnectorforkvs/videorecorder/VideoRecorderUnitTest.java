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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.*;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import com.aws.iot.edgeconnectorforkvs.monitor.Monitor;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder.RecorderBranchAppMonitor;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder.RecorderBranchFileMonitor;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.CapabilityListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.ErrorListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.NewPadListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.CameraType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch.RecorderBranchFile;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch.RecorderBranchFile.LocCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.camera.RecorderCameraRtsp;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeInfo;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class VideoRecorderUnitTest {
    private static final String SRC_URL = "rtsp://dummy";
    private static final CameraType REC_TYPE = CameraType.RTSP;
    private static final ContainerType CON_TYPE = ContainerType.MATROSKA;
    private static final String INVALID_ELEMENT_PROPERTY = "_invalid_property";

    private OutputStream testOutputStream;
    private final StatusCallback STATE_CALLBACK = (rec, st, desc) -> {
    };

    @Mock
    private GstDao mockGst;
    @Mock
    private Element mockGstElement;
    @Mock
    private Pipeline mockGstPipeline;
    @Mock
    private Bus mockGstBus;
    @Mock
    private Pad mockGstPad;
    @Mock
    private AppSink mockGstAppSink;
    @Mock
    private RecorderCameraRtsp mockCamera;

    private AppSink.NEW_SAMPLE appsNewSampleListener;
    private CapabilityListener capListener;
    private NewPadListener padListener;
    private volatile Pad.PROBE branchIdleProbe;
    private LocCallback locCallback;

    private static class RunnableRecorder implements Runnable {
        private VideoRecorder recorder;

        public RunnableRecorder(VideoRecorder r) {
            recorder = r;
        }

        @Override
        public void run() {
            recorder.start();
        }
    }

    private void waitRecorderUntil(VideoRecorder recorder, RecorderStatus st) {
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
        testOutputStream = null;
        appsNewSampleListener = null;

        willReturn(mockGstPipeline).given(mockGst).newPipeline();
        willReturn(mockGstBus).given(mockGst).getPipelineBus(any(Pipeline.class));
    }

    @AfterEach
    public void unSetupTests() {
        if (testOutputStream != null) {
            try {
                testOutputStream.close();
            } catch (IOException e) {
                Assertions.fail();
            }
        }
    }

    @Test
    public void setPropertyTest_updateProperty_returnTrue() {
        // Test: new GstDao
        try (MockedConstruction<GstDao> mockGstDao = mockConstruction(GstDao.class)) {

            Assertions.assertDoesNotThrow(() -> {
                VideoRecorderBuilder builderTest = new VideoRecorderBuilder(STATE_CALLBACK);
                Assertions.assertNotNull(builderTest);
            });
        }

        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        Assertions.assertTrue(() -> builder.addCameraSource(REC_TYPE, SRC_URL));
        Assertions.assertTrue(builder.registerFileSink(CON_TYPE, "./record"));

        // Set valid source and stored property should succeed
        VideoRecorder recorder = builder.construct();
        Assertions.assertTrue(recorder.setCameraProperty("location", SRC_URL));
        Assertions.assertTrue(recorder.setFilePathProperty("max-size-time", 0));
    }

    @Test
    public void setPropertyTest_invalidUsage_returnFalse() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        // Unsupported camera type
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.addCameraSource(CameraType.UNSUPPORTED, SRC_URL));

        // Construct a recorder without a camera should retrieve exceptions
        Assertions.assertThrows(RejectedExecutionException.class, () -> builder.construct());

        // Construct a recorder without a branch should retrieve exceptions
        Assertions.assertTrue(() -> builder.addCameraSource(REC_TYPE, SRC_URL));
        Assertions.assertFalse(() -> builder.addCameraSource(REC_TYPE, SRC_URL));
        Assertions.assertThrows(RejectedExecutionException.class, () -> builder.construct());

        // Set property to invalid target should return false
        Assertions.assertTrue(
                builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
                }));
        VideoRecorder recorder = builder.construct();
        Assertions.assertFalse(recorder.setFilePathProperty(INVALID_ELEMENT_PROPERTY, 0));
    }

    @Test
    public void registerCustomized_customizedModule_exceptionDoesNotThrow() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        GstDao dao = builder.getGstDao();
        Pipeline pipe = builder.getPipeline();
        Assertions.assertNotNull(builder.getPipeline());
        RecorderCameraRtsp cameraSrc = new RecorderCameraRtsp(dao, pipe, SRC_URL);
        RecorderBranchFile filePath =
                new RecorderBranchFile(ContainerType.MATROSKA, dao, pipe, "./record");

        // Add cam
        Assertions.assertTrue(builder.registerCustomCamera(cameraSrc));
        Assertions.assertFalse(builder.registerCustomCamera(cameraSrc));

        // Add branch
        Assertions.assertTrue(builder.registerCustomBranch(filePath, "customBranch", true));
        Assertions.assertFalse(builder.registerCustomBranch(filePath, "customBranch", true));

        // Get branch
        Assertions.assertNotNull(builder.construct());
    }

    @Test
    public void registerFileSinkTest_alreadyAdded_returnFalse() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        Assertions.assertTrue(() -> builder.addCameraSource(REC_TYPE, SRC_URL));

        // add file sink should succeed
        Assertions.assertTrue(builder.registerFileSink(CON_TYPE, "./record"));
        // add file sink again should be failed
        Assertions.assertFalse(builder.registerFileSink(CON_TYPE, "./record"));
    }

    @Test
    public void toggleAppDataAnyTest_notRegistered_returnFalse() {
        VideoRecorder recorder = null;
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        try {
            testOutputStream = new FileOutputStream("/dev/null");
        } catch (FileNotFoundException e) {
            Assertions.fail();
        }

        builder.addCameraSource(REC_TYPE, SRC_URL);
        builder.registerFileSink(CON_TYPE, "./record");
        recorder = builder.construct();

        // enable/disable invalid pipeline branches should get FALSE
        Assertions.assertFalse(recorder.toggleAppDataCallback(true));
        Assertions.assertFalse(recorder.toggleAppDataOutputStream(true));

        Assertions.assertFalse(recorder.setAppDataCallback((rec, buff) -> {
        }));
        Assertions.assertFalse(recorder.setAppDataOutputStream(testOutputStream));
    }

    @Test
    public void addAppCbTest_alreadyAdded_returnFalse() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        builder.addCameraSource(REC_TYPE, SRC_URL);
        // Register null callback should retrieve exceptions
        Assertions.assertThrows(NullPointerException.class,
                () -> builder.registerAppDataCallback(ContainerType.MATROSKA, null));
        // Register a new callback should succeed
        Assertions.assertTrue(
                builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
                }));
        // Register a new callback again should be failed
        Assertions.assertFalse(
                builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
                }));
    }

    @Test
    public void toggleAppCbTest_enableDisable_returnFalse() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        builder.addCameraSource(REC_TYPE, SRC_URL);
        // register app callback
        builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
        });

        VideoRecorder recorder = builder.construct();

        // Should be true when enabling callback
        Assertions.assertTrue(recorder.toggleAppDataCallback(true));
        Assertions.assertFalse(recorder.setAppDataCallback((rec, buff) -> {
        }));

        // Should be true when disabling callback
        Assertions.assertTrue(recorder.toggleAppDataCallback(false));
        Assertions.assertTrue(recorder.setAppDataCallback((rec, buff) -> {
        }));
        Assertions.assertThrows(NullPointerException.class,
                () -> recorder.setAppDataCallback(null));
    }

    @Test
    public void addAppCbTest_toggleRecording_callbackInvoked() {
        willReturn(mockGstElement).given(mockGst).newElement(anyString());
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));
        willAnswer(invocation -> {
            branchIdleProbe = invocation.getArgument(2);
            return null;
        }).given(mockGst).addPadProbe(any(), any(PadProbeType.class), any(Pad.PROBE.class));
        willAnswer(invocation -> {
            capListener = invocation.getArgument(0);
            padListener = invocation.getArgument(1);
            return null;
        }).given(mockCamera).registerListener(any(CapabilityListener.class),
                any(NewPadListener.class), any(ErrorListener.class));

        final byte[] testArray = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Sample mockGstSample = mock(Sample.class);
        Buffer mockGstBuffer = mock(Buffer.class);
        PadProbeInfo mockProbeInfo = mock(PadProbeInfo.class);
        ByteBuffer byteBuffer = ByteBuffer.wrap(testArray);
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        VideoRecorder recorder = null;
        Thread recordThread = null;

        // Mock AppSink connect interface
        willAnswer(invocation -> {
            appsNewSampleListener = invocation.getArgument(1);
            return null;
        }).given(mockGst).connectAppSink(any(AppSink.class), any(AppSink.NEW_SAMPLE.class));
        willReturn(mockGstSample).given(mockGstAppSink).pullSample();
        willReturn(mockGstBuffer).given(mockGstSample).getBuffer();
        willReturn(byteBuffer).given(mockGstBuffer).map(any(Boolean.class));
        willReturn(mockGstPad).given(mockGst).getElementRequestPad(any(Element.class), anyString());
        willReturn(true).given(mockGst).getElementProp(any(), eq("eos"));

        builder.registerCustomCamera(mockCamera);
        // register app callback
        builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
            // app callback should be invoked in newSample listener
            byte[] data = new byte[bBuff.remaining()];
            bBuff.get(data);
            Assertions.assertTrue(Arrays.equals(testArray, data));
        });
        recorder = builder.construct();
        recordThread = new Thread(new RunnableRecorder(recorder));

        recordThread.start();
        this.waitRecorderUntil(recorder, RecorderStatus.STARTED);

        // trigger callback manually
        capListener.onNotify(0, 1);
        padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockGstPad);
        recorder.toggleAppDataCallback(true);
        appsNewSampleListener.newSample(mockGstAppSink);

        // stop recording
        branchIdleProbe = null;
        recorder.stop();
        while (branchIdleProbe == null) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }
        branchIdleProbe.probeCallback(mockGstPad, mockProbeInfo);

        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void addAppOsTest_alreadyAdded_returnFalse() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        try {
            testOutputStream = new FileOutputStream("/dev/null");
        } catch (FileNotFoundException e) {
            Assertions.fail();
        }

        builder.addCameraSource(REC_TYPE, SRC_URL);
        // Register null OutputStream should retrieve exceptions
        Assertions.assertThrows(NullPointerException.class,
                () -> builder.registerAppDataOutputStream(ContainerType.MATROSKA, null));
        // Register a new OutputStream should succeed
        Assertions.assertTrue(
                builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream));
        // Register a new OutputStream again should be failed
        Assertions.assertFalse(
                builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream));
    }

    @Test
    public void toggleAppOsTest_enableDisable_returnFalse() {
        try {
            testOutputStream = new FileOutputStream("/dev/null");
        } catch (FileNotFoundException e) {
            Assertions.fail();
        }


        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        builder.addCameraSource(REC_TYPE, SRC_URL);
        // register app OutputStream
        builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream);

        VideoRecorder recorder = builder.construct();

        // Should be true when enabling OutputStream
        Assertions.assertTrue(recorder.toggleAppDataOutputStream(true));
        Assertions.assertFalse(recorder.setAppDataOutputStream(testOutputStream));;
        // Should be true when disabling OutputStream
        Assertions.assertTrue(recorder.toggleAppDataOutputStream(false));
        Assertions.assertTrue(recorder.setAppDataOutputStream(testOutputStream));
        Assertions.assertThrows(NullPointerException.class,
                () -> recorder.setAppDataOutputStream(null));
    }

    @Test
    public void addAppOsTest_toggleRecording_stateChanged() {
        willReturn(mockGstElement).given(mockGst).newElement(anyString());
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));
        willAnswer(invocation -> {
            branchIdleProbe = invocation.getArgument(2);
            return null;
        }).given(mockGst).addPadProbe(any(), any(PadProbeType.class), any(Pad.PROBE.class));
        willAnswer(invocation -> {
            capListener = invocation.getArgument(0);
            padListener = invocation.getArgument(1);
            return null;
        }).given(mockCamera).registerListener(any(CapabilityListener.class),
                any(NewPadListener.class), any(ErrorListener.class));

        final byte[] testArray = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Sample mockGstSample = mock(Sample.class);
        Buffer mockGstBuffer = mock(Buffer.class);
        PadProbeInfo mockProbeInfo = mock(PadProbeInfo.class);
        ByteBuffer byteBuffer = ByteBuffer.wrap(testArray);
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        VideoRecorder recorder = null;
        Thread recordThread = null;

        byteBuffer.rewind();
        // Mock AppSink connect interface
        willAnswer(invocation -> {
            appsNewSampleListener = invocation.getArgument(1);
            return null;
        }).given(mockGst).connectAppSink(any(AppSink.class), any(AppSink.NEW_SAMPLE.class));
        willReturn(mockGstSample).given(mockGstAppSink).pullSample();
        willReturn(mockGstBuffer).given(mockGstSample).getBuffer();
        willReturn(byteBuffer).given(mockGstBuffer).map(any(Boolean.class));
        willReturn(mockGstPad).given(mockGst).getElementRequestPad(any(Element.class), anyString());
        willReturn(true).given(mockGst).getElementProp(any(), eq("eos"));

        // register app OutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        testOutputStream = baos;
        builder.registerCustomCamera(mockCamera);
        builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream);

        recorder = builder.construct();
        recordThread = new Thread(new RunnableRecorder(recorder));

        recordThread.start();
        this.waitRecorderUntil(recorder, RecorderStatus.STARTED);

        // trigger to write OutputStream
        capListener.onNotify(0, 1);
        padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockGstPad);
        recorder.toggleAppDataOutputStream(true);
        appsNewSampleListener.newSample(mockGstAppSink);
        byte data[] = baos.toByteArray();
        Assertions.assertTrue(Arrays.equals(testArray, data));

        branchIdleProbe = null;
        recorder.stop();
        while (branchIdleProbe == null) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }
        branchIdleProbe.probeCallback(mockGstPad, mockProbeInfo);

        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void addAppOsTest_writeFailed_exceptionDoesNotThrow() {
        willReturn(mockGstElement).given(mockGst).newElement(anyString());
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));
        willAnswer(invocation -> {
            branchIdleProbe = invocation.getArgument(2);
            return null;
        }).given(mockGst).addPadProbe(any(), any(PadProbeType.class), any(Pad.PROBE.class));
        willAnswer(invocation -> {
            capListener = invocation.getArgument(0);
            padListener = invocation.getArgument(1);
            return null;
        }).given(mockCamera).registerListener(any(CapabilityListener.class),
                any(NewPadListener.class), any(ErrorListener.class));

        final byte[] testArray = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Sample mockGstSample = mock(Sample.class);
        Buffer mockGstBuffer = mock(Buffer.class);
        PadProbeInfo mockProbeInfo = mock(PadProbeInfo.class);
        ByteBuffer byteBuffer = ByteBuffer.wrap(testArray);
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        VideoRecorder recorder = null;
        Thread recordThread = null;

        byteBuffer.rewind();
        // Mock AppSink connect interface
        willAnswer(invocation -> {
            appsNewSampleListener = invocation.getArgument(1);
            return null;
        }).given(mockGst).connectAppSink(any(AppSink.class), any(AppSink.NEW_SAMPLE.class));
        willReturn(mockGstSample).given(mockGstAppSink).pullSample();
        willReturn(mockGstBuffer).given(mockGstSample).getBuffer();
        willReturn(byteBuffer).given(mockGstBuffer).map(any(Boolean.class));
        willReturn(mockGstPad).given(mockGst).getElementRequestPad(any(Element.class), anyString());
        willReturn(true).given(mockGst).getElementProp(any(), eq("eos"));

        // register app OutputStream
        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        testOutputStream = pipedOutputStream;
        builder.registerCustomCamera(mockCamera);
        builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream);

        recorder = builder.construct();
        recordThread = new Thread(new RunnableRecorder(recorder));

        recordThread.start();
        this.waitRecorderUntil(recorder, RecorderStatus.STARTED);

        try {
            pipedOutputStream.close();
            testOutputStream = null;
        } catch (IOException e) {
            Assertions.fail();
        }

        // exception is handled in the recorder
        capListener.onNotify(0, 1);
        padListener.onNotify(RecorderCapability.VIDEO_ONLY, mockGstPad);
        recorder.toggleAppDataOutputStream(true);
        Assertions.assertDoesNotThrow(() -> appsNewSampleListener.newSample(mockGstAppSink));

        branchIdleProbe = null;
        recorder.stop();
        while (branchIdleProbe == null) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        }
        branchIdleProbe.probeCallback(mockGstPad, mockProbeInfo);

        try {
            recordThread.join();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    @Test
    public void monitorBranch_bindUnbind_noException() {
        willAnswer(invocation -> {
            locCallback = invocation.getArgument(2);
            return null;
        }).given(this.mockGst).connectElement(any(), eq("format-location"), any(LocCallback.class));

        Monitor mockMonitor = mock(Monitor.class);
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        VideoRecorder recorder;

        builder.registerCustomCamera(mockCamera);
        builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
        });
        recorder = builder.construct();

        RecorderBranchFileMonitor branchFile =
                recorder.new RecorderBranchFileMonitor(ContainerType.MATROSKA, this.mockGst,
                        this.mockGstPipeline, "./recorder", "monitorFile");

        branchFile.onBindBegin();
        branchFile.onBindEnd();
        locCallback.callback(null, 0, null);
        branchFile.getMonitorCheck().check(mockMonitor, "monitorFile", null);
        branchFile.getMonitorCheck().check(mockMonitor, "monitorFile", null);
        branchFile.onUnbindBegin();

        RecorderBranchAppMonitor branchApp = recorder.new RecorderBranchAppMonitor(
                ContainerType.MATROSKA, this.mockGst, this.mockGstPipeline, "monitorApp");

        branchApp.onBindBegin();
        branchApp.onBindEnd();
        branchApp.getMonitorCheck().check(mockMonitor, "monitorApp", null);
        branchApp.increaseAppCallbackDataCnt();
        branchApp.getMonitorCheck().check(mockMonitor, "monitorApp", null);
        willReturn(true).given(mockGst).getElementProp(any(), eq("eos"));
        branchApp.onUnbindBegin();
    }
}
