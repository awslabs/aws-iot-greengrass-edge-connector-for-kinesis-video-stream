package com.aws.iot.edgeconnectorforkvs.videorecorder.callback;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Test;

public class StatusCallbackImplTest {
    @Test
    public void testNotifyStatus_Restart() {
        VideoRecorderBase videoRecorder = mock(VideoRecorderBase.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        when(pipeline.getName()).thenReturn("mockName");
        StatusCallback statusCallback = new StatusCallbackImpl();
        statusCallback.notifyStatus(videoRecorder, RecorderStatus.FAILED, "");
    }
}
