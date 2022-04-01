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

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploader;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.StatusChangedCallBack;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.UploadCallBack;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

@Slf4j
public class HistoricalVideoController {
    @Setter
    private static boolean retryOnFail = true;

    public static void startHistoricalVideoUploading(EdgeConnectorForKVSConfiguration configuration,
                                                     long startTime,
                                                     long endTime) {
        log.info("Start uploading video between " + startTime + " and "
                + endTime + " for stream "
                + configuration.getKinesisVideoStreamName());
        VideoUploader videoUploader = configuration.getVideoUploaderClientBuilder().build();
        Date dStartTime = new Date(startTime);
        Date dEndTime = new Date(endTime);
        boolean isUploadingFinished = false;

        do {
            try {
                videoUploader.uploadHistoricalVideo(dStartTime, dEndTime,
                        new StatusChangedCallBack(), new UploadCallBack(dStartTime, configuration));
                isUploadingFinished = true;
            } catch (Exception ex) {
                // Log error and retry historical uploading process
                log.error("Failed to upload historical videos: {}", ex.getMessage());
            }
        } while (retryOnFail && !isUploadingFinished);
    }
}
