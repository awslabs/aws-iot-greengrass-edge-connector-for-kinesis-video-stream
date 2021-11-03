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

package com.aws.iot.edgeconnectorforkvs.videouploader.callback;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.VideoFile;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;

/**
 * This is a functional interface for callback whenever there is any updated KVS fragment or uploaded file.
 */
@Slf4j
public class FragmentStatusUpdater implements Runnable {

    private EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration;

    private Date dateBegin;

    private long lastUpdatedFragmentTimeCode;

    private VideoFile lastUpdatedVideoFile;

    /**
     * Update status of newly fragment or file.
     *
     * @param edgeConnectorForKVSConfiguration      EdgeConnectorForKVSConfiguration
     * @param dateBegin                   The date when the uploading period begins
     * @param lastUpdatedFragmentTimeCode Timecode of the last fragment
     * @param lastUpdatedVideoFile        File of the last file
     */
    public FragmentStatusUpdater(@NonNull EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration,
                                 @NonNull Date dateBegin,
                                 long lastUpdatedFragmentTimeCode,
                                 VideoFile lastUpdatedVideoFile) {
        this.edgeConnectorForKVSConfiguration = edgeConnectorForKVSConfiguration;
        this.dateBegin = new Date(dateBegin.getTime());
        this.lastUpdatedFragmentTimeCode = lastUpdatedFragmentTimeCode;
        this.lastUpdatedVideoFile = lastUpdatedVideoFile;
    }

    public void run() {
        StreamManager streamManager = edgeConnectorForKVSConfiguration.getStreamManager();
        streamManager.pushData(edgeConnectorForKVSConfiguration.getSiteWiseAssetId(),
                edgeConnectorForKVSConfiguration.getVideoUploadedTimeRangePropertyId(),
                (double) lastUpdatedFragmentTimeCode / (double) Constants.MILLI_SECOND_TO_SECOND,
                Optional.of(dateBegin));
        log.trace("lastUpdatedFragment: " + lastUpdatedFragmentTimeCode + ", lastUpdatedVideoFile: "
                + ((lastUpdatedVideoFile == null) ? "null" : lastUpdatedVideoFile.getName()));
    }
}
