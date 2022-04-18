package com.aws.iot.edgeconnectorforkvs.videorecorder.callback;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.RECORDER_STATS_PERIODICAL_CHECK_TIME;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.monitor.Monitor;
import com.aws.iot.edgeconnectorforkvs.monitor.callback.CheckCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

@Slf4j
@AllArgsConstructor
public class RecorderStatusCheckCallbackImpl implements CheckCallback {
    @Setter
    private static int RESTART_SLEEP_TIME = RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;
    private ExecutorService recorderService;

    @Override
    public void check(@NonNull Monitor monitor, @NonNull String subject, @NonNull Object userData) {
        EdgeConnectorForKVSConfiguration configuration = (EdgeConnectorForKVSConfiguration) userData;
        VideoRecorder recorder = configuration.getVideoRecorder();
        RecorderStatus currentStatus = recorder.getStatus();
        RecorderStatus expectedStatus = configuration.getExpectedRecorderStatus();
        if (expectedStatus == RecorderStatus.STARTED && currentStatus != RecorderStatus.STARTED
                && configuration.getRecordingRequestsCount() > 0) {
            // If current recorder not running and do not have Camera level restart on going, restart recorder
            log.warn("Recorder failed due to errors. Pipeline name: " + recorder.getPipeline().getName());
            log.warn("Trying restart recorder");
            recorderService.submit(() -> {
                restartRecorder(recorder);
            });
        } else if (expectedStatus == RecorderStatus.STOPPED && currentStatus == RecorderStatus.STARTED
                && configuration.getRecordingRequestsCount() > 0) {
            recorder.stop();
        }
        monitor.add(subject, this, RECORDER_STATS_PERIODICAL_CHECK_TIME, configuration);
    }

    private void restartRecorder(@NonNull VideoRecorderBase recorder) {
        recorder.stop();
        try {
            Thread.sleep(RESTART_SLEEP_TIME);
        } catch (InterruptedException e) {
            log.error("Thread sleep interrupted.");
        }
        log.info("Restart Recording for pipeline recorder " + recorder.recorderName);
        recorder.start();
    }
}
