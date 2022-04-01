package com.aws.iot.edgeconnectorforkvs.videorecorder.callback;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

@Slf4j
@AllArgsConstructor
public class StatusCallbackImpl implements StatusCallback{
    private static final int restartSleepTime = RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;
    private EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration;
    private ExecutorService recorderService;

    @Override
    public void notifyStatus(VideoRecorderBase recorder, RecorderStatus status, String description) {
        String pipeLineName = recorder.getPipeline().getName();
        log.info("Recorder[" + pipeLineName + "] status changed callback: " + status);
        // Do not restart recorder when recordingRequestCount is less than 0
        // Camera level restart is in progress
        if (status.equals(RecorderStatus.FAILED) &&
                edgeConnectorForKVSConfiguration.getRecordingRequestsCount() > 0) {
            log.warn("Recorder failed due to errors. Pipeline name: " + pipeLineName);
            log.warn("Trying restart recorder");
            recorderService.submit(() -> {
                restartRecorder(recorder);
            });
        }
    }

    private void restartRecorder(@NonNull VideoRecorderBase recorder) {
        recorder.stopRecording();
        try {
            Thread.sleep(restartSleepTime);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted.");
        }
        recorder.startRecording();
        log.info("Restart Recording for pipeline recorder " + recorder.getPipeline().getName());
    }
}
