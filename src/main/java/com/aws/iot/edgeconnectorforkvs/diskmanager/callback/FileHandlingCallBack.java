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

package com.aws.iot.edgeconnectorforkvs.diskmanager.callback;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.diskmanager.DiskManagerUtil;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.VideoRecordVisitor;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Optional;

@NoArgsConstructor
public class FileHandlingCallBack implements WatchEventCallBack {
    // Thread lock
    private final Object diskManagementLock = new Object();

    //Thread safe method
    //   1. add new added file to the recordedFilesMap in DiskManagerUtil
    //   2. compute video recording period timestamp, then update SiteWise with new added file time stamp
    public void handleWatchEvent(@NonNull Path directoryPath,
                                 @NonNull WatchEvent<?> event,
                                 @NonNull DiskManagerUtil diskManagerUtil) {
        synchronized (diskManagementLock) {
            Path videoFilePath = directoryPath.resolve((Path) event.context());
            diskManagerUtil.appendRecordFileToDirPath(directoryPath, videoFilePath);

            EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = diskManagerUtil
                    .getEdgeConnectorForKVSConfigurationFromPath(directoryPath);

            StreamManager streamManager = edgeConnectorForKVSConfiguration.getStreamManager();
            if (streamManager != null) {
                streamManager.pushData(edgeConnectorForKVSConfiguration.getSiteWiseAssetId(),
                        edgeConnectorForKVSConfiguration.getVideoRecordedTimeRangePropertyId(),
                        (double) VideoRecordVisitor.getDateFromFilePath(videoFilePath).toInstant().getEpochSecond(),
                        Optional.of(diskManagerUtil.getVideoRecordingPeriodStartTime(directoryPath, videoFilePath)));
            }
        }
    }
}
