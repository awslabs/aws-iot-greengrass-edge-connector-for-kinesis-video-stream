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

package com.aws.iot.edgeconnectorforkvs.model;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploader;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploaderClient;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.UploaderStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Synchronized;
import lombok.experimental.SuperBuilder;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/***
 * EdgeConnectorForKVSConfiguration
 */
@Data
@SuperBuilder
@NoArgsConstructor
@SuppressWarnings("checkstyle:VisibilityModifier")
public class EdgeConnectorForKVSConfiguration {
    public String kinesisVideoStreamName;
    public String siteWiseAssetId;
    public String rtspStreamSecretARN;
    public String rtspStreamURL;
    public String liveStreamingStartTime;
    public int liveStreamingDurationInMinutes;
    public int localDataRetentionPeriodInMinutes;
    public String captureStartTime;
    public int captureDurationInMinutes;
    public String videoUploadRequestMqttTopic;
    public String videoUploadedTimeRangePropertyId;
    public String videoRecordedTimeRangePropertyId;
    public String cachedVideoAgeOutOnEdgePropertyId;
    public Path videoRecordFolderPath;
    public VideoUploader videoUploader;
    public UploaderStatus expectedUploaderStatus;
    public VideoUploaderClient.VideoUploaderClientBuilder videoUploaderClientBuilder;
    public VideoRecorder videoRecorder;
    public RecorderStatus expectedRecorderStatus;
    public PipedInputStream inputStream;
    public PipedOutputStream outputStream;
    public StreamManager streamManager;
    public ScheduledFuture<?> stopLiveStreamingTaskFuture;
    public AtomicBoolean fatalStatus;
    // Thread lock for recorder and uploader thread
    public ReentrantLock processLock;

    private final Object syncLock = new Object[0];
    private int recordingRequestsCount;
    private int liveStreamingRequestsCount;

    @Synchronized("syncLock")
    public void setRecordingRequestsCount(int count) {
        recordingRequestsCount = count;
    }

    @Synchronized("syncLock")
    public int getRecordingRequestsCount() {
        return recordingRequestsCount;
    }

    @Synchronized("syncLock")
    public void setLiveStreamingRequestsCount(int count) {
        liveStreamingRequestsCount = count;
    }

    @Synchronized("syncLock")
    public int getLiveStreamingRequestsCount() {
        return liveStreamingRequestsCount;
    }

}
