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

package com.aws.iot.edgeconnectorforkvs.controller;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.INIT_LOCK_TIMEOUT_IN_SECONDS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.PATH_DELIMITER;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.VIDEO_FILENAME_PREFIX_WITH_OUT_UNDERLINE;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.WAIT_TIME_BEFORE_RESTART_IN_MILLISECS;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorderBuilder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.GStreamerAppDataCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallbackImpl;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.CameraType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class RecordingController {
    @Setter
    private static int restartSleepTime = RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;

    public static void startRecordingJob(@NonNull EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration,
                                         @NonNull ExecutorService recorderService) {
        VideoRecorder videoRecorder = null;
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Start Recording called for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));
                edgeConnectorForKVSConfiguration.setRecordingRequestsCount(edgeConnectorForKVSConfiguration
                        .getRecordingRequestsCount() + 1);
                if (edgeConnectorForKVSConfiguration.getRecordingRequestsCount() > 1) {
                    log.info("Recording already running. Requests Count: " +
                            edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
                    return;
                }
                VideoRecorderBuilder builder = getVideoRecorderBuilder(edgeConnectorForKVSConfiguration,
                        recorderService);
                PipedOutputStream outputStream = new PipedOutputStream();
                builder.registerCamera(CameraType.RTSP, edgeConnectorForKVSConfiguration.getRtspStreamURL());
                builder.registerFileSink(ContainerType.MATROSKA,
                        edgeConnectorForKVSConfiguration.getVideoRecordFolderPath().toString() +
                                VIDEO_FILENAME_PREFIX_WITH_OUT_UNDERLINE);
                builder.registerAppDataCallback(ContainerType.MATROSKA, new GStreamerAppDataCallback());
                builder.registerAppDataOutputStream(ContainerType.MATROSKA, outputStream);
                videoRecorder = builder.construct();
                edgeConnectorForKVSConfiguration.setVideoRecorder(videoRecorder);
                edgeConnectorForKVSConfiguration.setOutputStream(outputStream);
                log.info("Recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName() +
                        " has been initialized");
            } else {
                // Recorder cannot init. Will retry in the method end
                log.error("Fail to init recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            }
        } catch (InterruptedException e) {
            log.error("Init recorder process for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                    + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
        }

        if (videoRecorder != null) {
            log.info("Start recording for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            videoRecorder.startRecording();
        } else {
            log.error("Fail to init recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        }
    }

    public static VideoRecorderBuilder getVideoRecorderBuilder(
            EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration,
            ExecutorService recorderService) {
        StatusCallback statusCallback = new StatusCallbackImpl(edgeConnectorForKVSConfiguration,
                recorderService);
        return new VideoRecorderBuilder(statusCallback);
    }

    public static void stopRecordingJob(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration) {
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(
                    INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Stop Recording called for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));
                VideoRecorder videoRecorder = edgeConnectorForKVSConfiguration.getVideoRecorder();
                // Stop video recording provided there's no live-streaming / scheduled recording in progress
                edgeConnectorForKVSConfiguration.setRecordingRequestsCount(edgeConnectorForKVSConfiguration
                        .getRecordingRequestsCount() - 1);
                if (edgeConnectorForKVSConfiguration.getRecordingRequestsCount() > 0) {
                    log.info("Recording is requested by multiple tasks. Requests Count " +
                            edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
                    return;
                }
                log.info("Recorder Requests Count is 0. Stopping.");
                int maxRetry = 5;
                while (!videoRecorder.getStatus().equals(RecorderStatus.STOPPED)) {
                    videoRecorder.stopRecording();
                    if (maxRetry > 0) {
                        maxRetry--;
                    } else {
                        log.error("Max retry reached to stop recorder for " +
                                edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                        edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
                        break;
                    }
                    Thread.sleep(restartSleepTime);
                }
                // Close the pipeline, so we don't have duplicate videos after restart
                if (videoRecorder.getPipeline() != null) {
                    edgeConnectorForKVSConfiguration.getVideoRecorder().getPipeline().close();
                }
            } else {
                log.error("Fail to stop recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } catch (InterruptedException e) {
            log.error("Stop recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                    + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
        }
    }

    public static void deInitVideoRecorders(EdgeConnectorForKVSConfiguration restartNeededConfiguration) {
        if (!restartNeededConfiguration.getVideoRecorder().getStatus().equals(RecorderStatus.STOPPED)) {
            stopRecordingJob(restartNeededConfiguration);
        }
    }
}
