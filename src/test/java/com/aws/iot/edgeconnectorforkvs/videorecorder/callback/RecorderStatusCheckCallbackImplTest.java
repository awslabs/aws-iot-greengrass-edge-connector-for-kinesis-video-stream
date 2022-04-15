package com.aws.iot.edgeconnectorforkvs.videorecorder.callback;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.monitor.Monitor;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecorderStatusCheckCallbackImplTest {
    private static final String MOCK_SUBJECT = "mockSubject";
    @Test
    public void testCheck_Restart() throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        VideoRecorder videoRecorder = mock(VideoRecorder.class);
        doNothing().when(videoRecorder).stop();
        doNothing().when(videoRecorder).start();
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STOPPED);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .recordingRequestsCount(1)
                .videoRecorder(videoRecorder)
                .expectedRecorderStatus(RecorderStatus.STARTED)
                .build();

        RecorderStatusCheckCallbackImpl recorderStatusCheckCallback = new RecorderStatusCheckCallbackImpl(recorderService);
        RecorderStatusCheckCallbackImpl.setRESTART_SLEEP_TIME(0);
        //then
        recorderStatusCheckCallback.check(Monitor.getMonitor(), MOCK_SUBJECT, edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        //verify
        verify(videoRecorder, times(1)).stop();
    }

    @Test
    public void testCheck_Not_Need_Restart() throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        VideoRecorder videoRecorder = mock(VideoRecorder.class);
        doNothing().when(videoRecorder).stop();
        doNothing().when(videoRecorder).start();
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STOPPED);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .recordingRequestsCount(0)
                .videoRecorder(videoRecorder)
                .expectedRecorderStatus(RecorderStatus.STARTED)
                .build();

        RecorderStatusCheckCallbackImpl recorderStatusCheckCallback = new RecorderStatusCheckCallbackImpl(recorderService);
        RecorderStatusCheckCallbackImpl.setRESTART_SLEEP_TIME(0);
        //then
        recorderStatusCheckCallback.check(Monitor.getMonitor(), MOCK_SUBJECT, edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        //verify
        verify(videoRecorder, times(0)).stop();
    }

    @Test
    public void testCheck_Stop() throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        VideoRecorder videoRecorder = mock(VideoRecorder.class);
        doNothing().when(videoRecorder).stop();
        doNothing().when(videoRecorder).start();
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STARTED);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .recordingRequestsCount(1)
                .videoRecorder(videoRecorder)
                .expectedRecorderStatus(RecorderStatus.STOPPED)
                .build();

        RecorderStatusCheckCallbackImpl recorderStatusCheckCallback = new RecorderStatusCheckCallbackImpl(recorderService);
        RecorderStatusCheckCallbackImpl.setRESTART_SLEEP_TIME(0);
        //then
        recorderStatusCheckCallback.check(Monitor.getMonitor(), MOCK_SUBJECT, edgeConnectorForKVSConfiguration);
        //verify
        verify(videoRecorder, times(1)).stop();
    }

    @Test
    public void testCheck__Not_Need_Stop() throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        VideoRecorder videoRecorder = mock(VideoRecorder.class);
        doNothing().when(videoRecorder).stop();
        doNothing().when(videoRecorder).start();
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STARTED);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .recordingRequestsCount(0)
                .videoRecorder(videoRecorder)
                .expectedRecorderStatus(RecorderStatus.STOPPED)
                .build();

        RecorderStatusCheckCallbackImpl recorderStatusCheckCallback = new RecorderStatusCheckCallbackImpl(recorderService);
        RecorderStatusCheckCallbackImpl.setRESTART_SLEEP_TIME(0);
        //then
        recorderStatusCheckCallback.check(Monitor.getMonitor(), MOCK_SUBJECT, edgeConnectorForKVSConfiguration);
        //verify
        verify(videoRecorder, times(0)).stop();
    }
}