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

package com.aws.iot.edgeconnectorforkvs.handler;

import static com.aws.iot.edgeconnectorforkvs.controller.HistoricalVideoController.startHistoricalVideoUploading;
import static com.aws.iot.edgeconnectorforkvs.controller.LiveVideoStreamingController.getStopLiveStreamingTask;
import static com.aws.iot.edgeconnectorforkvs.controller.LiveVideoStreamingController.startLiveVideoStreaming;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.LIVE_STREAMING_STOP_TIMER_DELAY_IN_SECONDS;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class VideoUploadRequestEventImpl implements VideoUploadRequestEvent{
    @Override
    public void onStart(boolean isLive,
                        long updateTimestamp,
                        long startTime,
                        long endTime,
                        EdgeConnectorForKVSConfiguration configuration,
                        ExecutorService recorderService,
                        ExecutorService liveStreamingExecutor,
                        ScheduledExecutorService stopLiveStreamingExecutor) {
        if (isLive) {
            log.info("Received Live Streaming request for stream: "
                    + configuration.getKinesisVideoStreamName());
            ScheduledFuture<?> future = configuration.getStopLiveStreamingTaskFuture();
            if (future == null) {
                // Kick-off Live Streaming for 5 mins
                // do it only for the first request
                log.info("Start Live Streaming");
                liveStreamingExecutor.submit(() -> {
                    try {
                        startLiveVideoStreaming(configuration, recorderService);
                    } catch (Exception ex) {
                        log.error("Error starting live video streaming." + ex.getMessage());
                        configuration.getFatalStatus().set(true);
                    }
                });
            } else {
                log.info("Live Streaming was already started. Continue Streaming.");
                // Cancel the previously started scheduled task
                // and restart the task below
                future.cancel(false);
            }
            Runnable task = getStopLiveStreamingTask(configuration);
            future = stopLiveStreamingExecutor.schedule(task,
                    LIVE_STREAMING_STOP_TIMER_DELAY_IN_SECONDS, TimeUnit.SECONDS);
            configuration.setStopLiveStreamingTaskFuture(future);
            log.info("Schedule Live Streaming to stop after " +
                    LIVE_STREAMING_STOP_TIMER_DELAY_IN_SECONDS + "s for stream: " +
                    configuration.getKinesisVideoStreamName());
        } else {
            startHistoricalVideoUploading(configuration, startTime, endTime);
        }
    }

    @Override
    public void onError(String errMessage, EdgeConnectorForKVSConfiguration configuration) {
        log.info("MQTT Error " + errMessage + " for stream "
                + configuration.getKinesisVideoStreamName());
    }
}
