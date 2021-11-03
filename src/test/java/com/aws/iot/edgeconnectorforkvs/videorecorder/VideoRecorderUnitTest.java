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
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.CameraType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
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

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
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

    private AppSink.NEW_SAMPLE appsNewSampleListener;

    private static class RunnableRecorder implements Runnable {
        private VideoRecorder recorder;

        public RunnableRecorder(VideoRecorder r) {
            recorder = r;
        }

        @Override
        public void run() {
            recorder.startRecording();
        }
    }

    @BeforeEach
    public void setupTests() {
        testOutputStream = null;
        appsNewSampleListener = null;

        willReturn(mockGstPipeline).given(mockGst).newPipeline();
        willReturn(mockGstBus).given(mockGst).getPipelineBus(any(Pipeline.class));
        willReturn(mockGstElement).given(mockGst).newElement(anyString());
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

        Assertions.assertTrue(() -> builder.registerCamera(REC_TYPE, SRC_URL));
        Assertions.assertTrue(builder.registerFileSink(CON_TYPE, "./record"));

        // Set valid source and stored property should succeed
        Assertions.assertTrue(builder.setCameraProperty("location", SRC_URL));
        Assertions.assertTrue(builder.setFilePathProperty("max-size-time", 0));
    }

    @Test
    public void setPropertyTest_invalidUsage_returnFalse() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        // Unsupported camera type
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> builder.registerCamera(CameraType.UNSUPPORTED, SRC_URL));

        // Set property to invalid target should return false
        Assertions.assertFalse(builder.setCameraProperty(INVALID_ELEMENT_PROPERTY, 0));
        Assertions.assertFalse(builder.setFilePathProperty(INVALID_ELEMENT_PROPERTY, 0));

        // Construct a recorder with incomplete pipeline should retrieve exceptions
        Assertions.assertThrows(RejectedExecutionException.class, () -> builder.construct());

        Assertions.assertTrue(() -> builder.registerCamera(REC_TYPE, SRC_URL));
    }

    @Test
    public void registerCustomized_customizedModule_exceptionDoesNotThrow() {
        VideoRecorder recorder = null;
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        GstDao dao = builder.getGstDao();
        Pipeline pipe = builder.getPipeline();
        Assertions.assertNotNull(builder.getPipeline());
        RecorderCameraRtsp cameraSrc = new RecorderCameraRtsp(dao, pipe, SRC_URL);
        RecorderBranchFile filePath =
                new RecorderBranchFile(ContainerType.MATROSKA, dao, pipe, "./record");

        // Add cam
        Assertions.assertThrows(RejectedExecutionException.class, () -> builder.construct());
        Assertions.assertTrue(builder.registerCustomCamera(cameraSrc));
        Assertions.assertFalse(builder.registerCustomCamera(cameraSrc));

        // Add branch
        Assertions.assertThrows(RejectedExecutionException.class, () -> builder.construct());
        Assertions.assertTrue(builder.registerCustomBranch(filePath, "customBranch"));
        Assertions.assertFalse(builder.registerCustomBranch(filePath, "customBranch"));

        // Get branch
        recorder = builder.construct();
        Assertions.assertNotNull(recorder.getBranch("customBranch"));
        Assertions.assertTrue(null == recorder.getBranch("NoSuchBranch"));
    }

    @Test
    public void registerFileSinkTest_alreadyAdded_returnFalse() {
        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        Assertions.assertTrue(() -> builder.registerCamera(REC_TYPE, SRC_URL));
        Assertions.assertFalse(() -> builder.registerCamera(REC_TYPE, SRC_URL));
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

        builder.registerCamera(REC_TYPE, SRC_URL);
        builder.registerFileSink(CON_TYPE, "./record");
        recorder = builder.construct();

        // enable/disable invalid pipeline branches should get FALSE
        Assertions.assertFalse(recorder.toggleAppDataCallback(true));
        Assertions.assertFalse(recorder.toggleAppDataCallback(false));
        Assertions.assertFalse(recorder.toggleAppDataOutputStream(true));
        Assertions.assertFalse(recorder.toggleAppDataOutputStream(false));

        Assertions.assertFalse(recorder.setAppDataCallback((rec, buff) -> {
        }));
        Assertions.assertFalse(recorder.setAppDataOutputStream(testOutputStream));
    }

    @Test
    public void addAppCbTest_alreadyAdded_returnFalse() {
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));

        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        builder.registerCamera(REC_TYPE, SRC_URL);
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
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));

        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        builder.registerCamera(REC_TYPE, SRC_URL);
        // register app callback
        builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
        });

        VideoRecorder recorder = builder.construct();

        // Should be true when enabling callback
        Assertions.assertTrue(recorder.toggleAppDataCallback(true));
        Assertions.assertFalse(recorder.setAppDataCallback((rec, buff) -> {
        }));
        // Should be false if callback is already enabled
        Assertions.assertFalse(recorder.toggleAppDataCallback(true));
        // Should be true when disabling callback
        Assertions.assertTrue(recorder.toggleAppDataCallback(false));
        // Should be false when callback is already disabled
        Assertions.assertFalse(recorder.toggleAppDataCallback(false));
        Assertions.assertTrue(recorder.setAppDataCallback((rec, buff) -> {
        }));
        Assertions.assertThrows(NullPointerException.class,
                () -> recorder.setAppDataCallback(null));
    }

    @Test
    public void addAppCbTest_toggleRecording_callbackInvoked() {
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));

        final byte[] testArray = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Sample mockGstSample = mock(Sample.class);
        Buffer mockGstBuffer = mock(Buffer.class);
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

        builder.registerCamera(REC_TYPE, SRC_URL);
        // register app callback
        builder.registerAppDataCallback(ContainerType.MATROSKA, (rec, bBuff) -> {
            // app callback should be invoked in newSample listener
            byte[] data = new byte[bBuff.remaining()];
            bBuff.get(data);
            Assertions.assertTrue(Arrays.equals(testArray, data));
        });
        recorder = builder.construct();
        recordThread = new Thread(new RunnableRecorder(recorder));
        recorder.toggleAppDataCallback(true);
        recordThread.start();

        // trigger callback manually
        appsNewSampleListener.newSample(mockGstAppSink);

        // stop recording
        recorder.stopRecording();
    }

    @Test
    public void addAppOsTest_alreadyAdded_returnFalse() {
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));

        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);

        try {
            testOutputStream = new FileOutputStream("/dev/null");
        } catch (FileNotFoundException e) {
            Assertions.fail();
        }

        builder.registerCamera(REC_TYPE, SRC_URL);
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
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));
        try {
            testOutputStream = new FileOutputStream("/dev/null");
        } catch (FileNotFoundException e) {
            Assertions.fail();
        }


        VideoRecorderBuilder builder = new VideoRecorderBuilder(mockGst, STATE_CALLBACK);
        builder.registerCamera(REC_TYPE, SRC_URL);
        // register app OutputStream
        builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream);

        VideoRecorder recorder = builder.construct();

        // Should be true when enabling OutputStream
        Assertions.assertTrue(recorder.toggleAppDataOutputStream(true));
        Assertions.assertFalse(recorder.setAppDataOutputStream(testOutputStream));
        // Should be false if OutputStream is already enabled
        Assertions.assertFalse(recorder.toggleAppDataOutputStream(true));
        // Should be true when disabling OutputStream
        Assertions.assertTrue(recorder.toggleAppDataOutputStream(false));
        // Should be false when OutputStream is already disabled
        Assertions.assertFalse(recorder.toggleAppDataOutputStream(false));
        Assertions.assertTrue(recorder.setAppDataOutputStream(testOutputStream));
        Assertions.assertThrows(NullPointerException.class,
                () -> recorder.setAppDataOutputStream(null));
    }

    @Test
    public void addAppOsTest_toggleRecording_stateChanged() {
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));

        final byte[] testArray = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Sample mockGstSample = mock(Sample.class);
        Buffer mockGstBuffer = mock(Buffer.class);
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

        // register app OutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        testOutputStream = baos;
        builder.registerCamera(REC_TYPE, SRC_URL);
        builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream);

        recorder = builder.construct();
        recordThread = new Thread(new RunnableRecorder(recorder));
        recorder.toggleAppDataOutputStream(true);
        recordThread.start();

        // trigger to write OutputStream
        appsNewSampleListener.newSample(mockGstAppSink);
        byte data[] = baos.toByteArray();
        Assertions.assertTrue(Arrays.equals(testArray, data));

        recorder.stopRecording();
    }

    @Test
    public void addAppOsTest_writeFailed_exceptionDoesNotThrow() {
        willReturn(mockGstAppSink).given(mockGst).newElement(eq("appsink"));
        willReturn(true).given(mockGst).linkManyElement(any(Element.class));

        final byte[] testArray = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Sample mockGstSample = mock(Sample.class);
        Buffer mockGstBuffer = mock(Buffer.class);
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

        // register app OutputStream
        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        testOutputStream = pipedOutputStream;
        builder.registerCamera(REC_TYPE, SRC_URL);
        builder.registerAppDataOutputStream(ContainerType.MATROSKA, testOutputStream);

        recorder = builder.construct();
        recordThread = new Thread(new RunnableRecorder(recorder));
        recorder.toggleAppDataOutputStream(true);
        recordThread.start();

        try {
            pipedOutputStream.close();
            testOutputStream = null;
        } catch (IOException e) {
            Assertions.fail();
        }
        // exception is handled in the recorder
        Assertions.assertDoesNotThrow(() -> appsNewSampleListener.newSample(mockGstAppSink));

        recorder.stopRecording();
    }
}
