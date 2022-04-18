package com.aws.iot.edgeconnectorforkvs.videorecorder.callback;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

/**
 * Controller implementation of the recorder status callback.
 */
@Slf4j
@AllArgsConstructor
public class StatusCallbackImpl implements StatusCallback {
    @Override
    public void notifyStatus(VideoRecorderBase recorder, RecorderStatus status, String description) {
        String pipeLineName = recorder.getPipeline().getName();
        log.info("Recorder[" + pipeLineName + "] status changed callback: " + status);
    }
}
