package com.aws.iot.edgeconnectorforkvs.videorecorder.callback;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusCallbackImplTest {
    @Test
    public void testNotifyStatus_Restart() throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        VideoRecorderBase videoRecorderBase = mock(VideoRecorderBase.class);
        doNothing().when(videoRecorderBase).stopRecording();
        doNothing().when(videoRecorderBase).startRecording();
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorderBase.getPipeline()).thenReturn(pipeline);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .recordingRequestsCount(1)
                .build();

        //then
        StatusCallback statusCallback = new StatusCallbackImpl(edgeConnectorForKVSConfiguration, recorderService);
        statusCallback.notifyStatus(videoRecorderBase, RecorderStatus.FAILED, "");
        //verify
        Thread.sleep(1000);
        verify(videoRecorderBase, times(1)).stopRecording();
    }

    @Test
    public void testNotifyStatus_Not_Restart() {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        VideoRecorderBase videoRecorderBase = mock(VideoRecorderBase.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorderBase.getPipeline()).thenReturn(pipeline);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .recordingRequestsCount(1)
                .build();

        //then
        StatusCallback statusCallback = new StatusCallbackImpl(edgeConnectorForKVSConfiguration, recorderService);
        statusCallback.notifyStatus(videoRecorderBase, RecorderStatus.STARTED, "");
        //verify
        verify(videoRecorderBase, times(0)).stopRecording();
    }
}
