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

package com.aws.iot.edgeconnectorforkvs.diskmanager;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.VideoRecordVisitor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/***
 * VideoFileCleaner thread used to clean up out of range video files;
 * After check and clean up, VideoFileCleaner will send heart beat signal to SiteWise measurement.
 */
@Slf4j
@Builder
@AllArgsConstructor

public class VideoFileManager implements Runnable {

    // EdgeConnectorForKVSConfiguration list
    @NonNull
    private List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList;

    // DiskManagerUtil used to capture file information
    @NonNull
    private DiskManagerUtil diskManagerUtil;

    //If the video files size exceed the limitation set by the customer, component will keep delete the oldest files
    //until all files meet retention requirements.
    @Override
    public void run() {
        edgeConnectorForKVSConfigurationList.forEach(edgeConnectorForKVSConfiguration -> {
            Path directoryPath = edgeConnectorForKVSConfiguration.getVideoRecordFolderPath();
            while (hasVideoFileExceedsRetentionTime(directoryPath, edgeConnectorForKVSConfiguration)) {
                Path toBeDeletedFilePath = diskManagerUtil.getRecordedFilesMap().get(directoryPath).poll();
                if (toBeDeletedFilePath != null) {
                    try {
                        Files.deleteIfExists(toBeDeletedFilePath);
                    } catch (Exception e) {
                        log.error(String.format("Delete video record in path: %s failed!",
                                toBeDeletedFilePath.getFileName()));
                        log.error(e.getMessage());
                    }
                }
            }
            sendHeartBeatMessageToSiteWise(directoryPath, edgeConnectorForKVSConfiguration);
        });
    }

    /***
     * Method checks if the oldest video file belongs to directory path exceeds retention time period.
     *
     * @param directoryPath            {@link Path} for the video recording directory
     * @param edgeConnectorForKVSConfiguration   EdgeConnectorForKVSConfiguration
     * @return boolean
     */
    private boolean hasVideoFileExceedsRetentionTime(@NonNull Path directoryPath,
                                                     @NonNull EdgeConnectorForKVSConfiguration
                                                             edgeConnectorForKVSConfiguration) {
        if (diskManagerUtil.getRecordedFilesMap().isEmpty() ||
                diskManagerUtil.getRecordedFilesMap().get(directoryPath) == null
                || diskManagerUtil.getRecordedFilesMap().get(directoryPath).isEmpty()) {
            return false;
        } else {
            Path oldestVideoFilePath = diskManagerUtil.getRecordedFilesMap().get(directoryPath).peek();
            if (oldestVideoFilePath == null) {
                return false;
            } else {
                Date oldestVideoFileDate = VideoRecordVisitor.getDateFromFilePath(oldestVideoFilePath);
                Date fileRetentionBoundary = Date.from(LocalDateTime.now().minusMinutes(
                        edgeConnectorForKVSConfiguration.getLocalDataRetentionPeriodInMinutes())
                        .atZone(ZoneId.systemDefault()).toInstant());
                return oldestVideoFileDate.before(fileRetentionBoundary);
            }
        }
    }

    /***
     * Method to update SiteWise CachedVideoAgeOutOnEdge property, used as heart beat measurement.
     * If given directory path has recorded video file, the updated property value will be the timestamp from the file
     * name;
     * If given directory path does not have recorded video file, the updated property value will be 0.
     *
     * @param directoryPath            {@link Path} for the video recording directory
     * @param edgeConnectorForKVSConfiguration   EdgeConnectorForKVSConfiguration
     */
    private void sendHeartBeatMessageToSiteWise(@NonNull Path directoryPath, @NonNull EdgeConnectorForKVSConfiguration
            edgeConnectorForKVSConfiguration) {
        StreamManager streamManager = edgeConnectorForKVSConfiguration.getStreamManager();
        double cachedVideoAgeOutOnEdgeTime = 0;
        if (!diskManagerUtil.getRecordedFilesMap().isEmpty() &&
                diskManagerUtil.getRecordedFilesMap().get(directoryPath) != null &&
                !diskManagerUtil.getRecordedFilesMap().get(directoryPath).isEmpty()) {
            Path oldestVideoFilePath = diskManagerUtil.getRecordedFilesMap().get(directoryPath).peek();
            if (oldestVideoFilePath != null) {
                Date oldestVideoFileDate = VideoRecordVisitor.getDateFromFilePath(oldestVideoFilePath);
                cachedVideoAgeOutOnEdgeTime = oldestVideoFileDate.toInstant().getEpochSecond();
            }
        }
        streamManager.pushData(
                edgeConnectorForKVSConfiguration.getSiteWiseAssetId(),
                edgeConnectorForKVSConfiguration.getCachedVideoAgeOutOnEdgePropertyId(),
                cachedVideoAgeOutOnEdgeTime,
                Optional.empty());
    }
}
