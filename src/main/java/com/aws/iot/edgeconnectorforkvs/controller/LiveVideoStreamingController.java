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

import static com.aws.iot.edgeconnectorforkvs.controller.RecordingController.startRecordingJob;
import static com.aws.iot.edgeconnectorforkvs.controller.RecordingController.stopRecordingJob;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.INIT_LOCK_TIMEOUT_IN_SECONDS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.RECORDING_JOB_WAIT_TIME_IN_SECS;
import static com.aws.iot.edgeconnectorforkvs.util.StreamUtils.flushInputStream;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploader;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.StatusChangedCallBack;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.UploadCallBack;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class LiveVideoStreamingController {
    @Setter
    private static boolean retryOnFail = true;

    public static void startLiveVideoStreaming(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration,
                                               ExecutorService recorderService)
            throws IOException, InterruptedException {
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(
                    INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Start Live Video Streaming Called for " +
                        edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));
                edgeConnectorForKVSConfiguration.setLiveStreamingRequestsCount(edgeConnectorForKVSConfiguration
                        .getLiveStreamingRequestsCount() + 1);
                if (edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() > 1) {
                    log.info("Live Streaming already running. Requests Count: " +
                            edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
                    return;
                }
            } else {
                log.error("Start uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                        + " timeout, re-init camera to restart the process.");
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } catch (InterruptedException e) {
            log.error("Start uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                    + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
        }

        // kick-off recording if it wasn't already started
        Future<?> future = recorderService.submit(() -> {
            startRecordingJob(edgeConnectorForKVSConfiguration, recorderService);
        });
        try {
            // startRecordingJob is a blocking call, so we wait
            // upto 5 seconds for the recording to start before
            // we start live streaming below
            future.get(RECORDING_JOB_WAIT_TIME_IN_SECS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            log.error("Start Live Streaming Interrupted Exception: " + ex.getMessage());
        } catch (ExecutionException ex) {
            log.error("Start Live Streaming Execution Exception: " + ex.getMessage());
        } catch (TimeoutException ex) {
            // Ignore this exception, it is expected since
            // startRecordingJob is a blocking call
        }

        VideoRecorder videoRecorder = edgeConnectorForKVSConfiguration.getVideoRecorder();
        VideoUploader videoUploader = edgeConnectorForKVSConfiguration.getVideoUploader();

        do {
            PipedOutputStream outputStream = new PipedOutputStream();
            PipedInputStream inputStream = new PipedInputStream();

            // Toggle to false before switching outputStream (may not be required)
            videoRecorder.toggleAppDataOutputStream(false);

            edgeConnectorForKVSConfiguration.setOutputStream(outputStream);
            edgeConnectorForKVSConfiguration.setInputStream(inputStream);
            outputStream.connect(inputStream);
            videoRecorder.setAppDataOutputStream(outputStream);

            log.info("Connected streams for KVS Stream: " +
                    edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            videoRecorder.toggleAppDataOutputStream(true);

            log.info("Turned on outputStream in recorder and start uploading!");
            Date dateNow = new Date();
            try {
                videoUploader.uploadStream(inputStream, dateNow, new StatusChangedCallBack(),
                        new UploadCallBack(dateNow, edgeConnectorForKVSConfiguration));
            } catch (Exception exception) {
                if (processLock.tryLock(INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                    log.error("Failed to upload stream: {}", exception.getMessage());

                    AtomicBoolean isRecorderToggleOff = new AtomicBoolean();
                    Thread toggleRecorderOffThreaed = new Thread(() -> {
                        log.info("Waiting for toggling recorder off");
                        videoRecorder.toggleAppDataOutputStream(false);
                        try {
                            TimeUnit.MILLISECONDS.sleep(2000);
                        } catch (InterruptedException e) {
                            log.error("toggleRecorderOffThread exception: " + e.getMessage());
                        }
                        isRecorderToggleOff.set(true);
                        log.info("Toggling recorder off");
                    });

                    toggleRecorderOffThreaed.start();
                    log.info("InputStream is flushing");
                    try {
                        int bytesAvailable = inputStream.available();
                        while (!isRecorderToggleOff.get() || bytesAvailable > 0) {
                            byte[] b = new byte[bytesAvailable];
                            inputStream.read(b);
                            bytesAvailable = inputStream.available();
                        }
                    } catch (IOException e) {
                        log.error("Exception flush inputStream: " + e.getMessage());
                    } finally {
                        if (processLock.isHeldByCurrentThread()) processLock.unlock();
                    }
                    log.info("InputStream is flushed");

                    outputStream.close();
                    inputStream.close();
                } else {
                    log.error("Restart uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                            + " timeout, re-init camera to restart the process.");
                    edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
                    if (processLock.isHeldByCurrentThread()) processLock.unlock();
                    break;
                }
            }
        } while (retryOnFail && edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() > 0);
    }

    public static void stopLiveVideoStreaming(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration) {
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(
                    INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Stop Live Video Streaming Called for " +
                        edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));

                // Stop video recording provided there's no scheduled / mqtt based live streaming in progress
                edgeConnectorForKVSConfiguration.setLiveStreamingRequestsCount(
                        edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() - 1);
                if (edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() > 0) {
                    log.info("Live Streaming is being used by multiple tasks. Requests Count " +
                            edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
                    return;
                }
                log.info("Live Steaming Requests Count is 0. Stopping.");

                VideoRecorder videoRecorder = edgeConnectorForKVSConfiguration.getVideoRecorder();
                VideoUploader videoUploader = edgeConnectorForKVSConfiguration.getVideoUploader();

                log.info("Toggle output stream off for KVS Stream: " +
                        edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                videoRecorder.toggleAppDataOutputStream(false);

                log.info("Close video uploader for KVS Stream: " +
                        edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                videoUploader.close();

                // Manually flush input stream
                flushInputStream(edgeConnectorForKVSConfiguration.getInputStream());

                edgeConnectorForKVSConfiguration.getInputStream().close();
                edgeConnectorForKVSConfiguration.getOutputStream().close();
                // Stop recording if it was kicked-off only for this live-streaming
                stopRecordingJob(edgeConnectorForKVSConfiguration);
            } else {
                log.error("Stop uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                        + " timeout, re-init camera to restart the process.");
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } catch (InterruptedException e) {
            log.error("Stop uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                    + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } catch (IOException e) {
            log.error("Exception when deInitVideoUploaders: " + e.getMessage());
        }
        finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
        }
    }

    public static Runnable getStopLiveStreamingTask(EdgeConnectorForKVSConfiguration configuration) {
        return () -> {
            try {
                log.info("Stop Live Streaming for stream " +
                        configuration.getKinesisVideoStreamName() +
                        ". Thread's name: " + Thread.currentThread().getName());
                configuration.setStopLiveStreamingTaskFuture(null);
                stopLiveVideoStreaming(configuration);
            } catch (Exception ex) {
                log.error("Error stopping live video streaming." + ex.getMessage());
                configuration.getFatalStatus().set(true);
            }
        };
    }
}
