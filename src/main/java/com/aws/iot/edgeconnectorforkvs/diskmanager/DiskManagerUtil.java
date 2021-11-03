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

import static com.aws.iot.edgeconnectorforkvs.util.Constants.MAX_EXPECTED_VIDEO_FILE_TIME_GAP_IN_MINUTES;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.MAX_TIME_GAP_FOR_BACKFILL_SITEWISE_TIMESTAMP_IN_MINUTES;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.VideoRecordVisitor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/***
 * DiskManagerUtil used to capture file information
 */
@Slf4j
@Getter
@NoArgsConstructor
public class DiskManagerUtil {
    // Map for directoryPath to ConcurrentLinkedQueue which contains all files belong to the directory
    private Map<Path, ConcurrentLinkedDeque<Path>> recordedFilesMap = new Hashtable<>();
    // Map for directoryPath to configured file retention period
    private Map<Path, Integer> recordedFilesRetentionPeriodMap = new Hashtable<>();
    // Map for directoryPath to the start timestamp for video file recording time periods.
    private Map<Path, Date> recordingStartTimeMap = new Hashtable<>();
    // Map for directoryPath to EdgeConnectorForKVSConfiguration.
    private Map<Path, EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationMap = new Hashtable<>();

    /***
     * Method to append new file to the recordedFilesMap
     *
     * @param directoryPath  {@link Path} for the video recording directory
     * @param filePath       {@link Path} for the video file
     */
    public void appendRecordFileToDirPath(@NonNull Path directoryPath, @NonNull Path filePath) {
        if (recordedFilesMap.get(directoryPath) == null) {
            recordedFilesMap.put(directoryPath, new ConcurrentLinkedDeque<>());
        }
        recordedFilesMap.get(directoryPath).add(filePath);
    }

    /***
     * Generate EdgeConnectorForKVSConfigurationMap
     * Key: VideoRecordFolderPath
     * Value: edgeConnectorForKVSConfiguration
     * @param edgeConnectorForKVSConfigurationList    List of edgeConnectorForKVSConfiguration
     */
    public void buildEdgeConnectorForKVSConfigurationMap(@NonNull List<EdgeConnectorForKVSConfiguration>
                                                                 edgeConnectorForKVSConfigurationList) {
        edgeConnectorForKVSConfigurationList.forEach(configuration -> {
            edgeConnectorForKVSConfigurationMap.put(configuration.getVideoRecordFolderPath(), configuration);
        });
    }

    /***
     * Get edgeConnectorForKVSConfiguration from given VideoRecordFolderPath
     * @param directoryPath VideoRecordFolderPath
     * @return edgeConnectorForKVSConfiguration
     */
    public EdgeConnectorForKVSConfiguration getEdgeConnectorForKVSConfigurationFromPath(@NonNull Path directoryPath) {
        return edgeConnectorForKVSConfigurationMap.get(directoryPath);
    }

    /***
     * Method to initialize local cached video file records.
     * The method all walk all given dir paths, generate Map, key is directoryPath, value is the
     * ConcurrentLinkedQueue which contains all existing video files.
     *
     * @param edgeConnectorForKVSConfiguration EdgeConnectorForKVSConfiguration
     * @throws IOException           throw IOException
     */
    public void buildRecordedFilesMap(@NonNull EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration)
            throws IOException {
        Path directoryPath = edgeConnectorForKVSConfiguration.getVideoRecordFolderPath();
        ConcurrentLinkedDeque<Path> recordedFilesQueue = new ConcurrentLinkedDeque<>();
        recordedFilesMap.put(directoryPath, recordedFilesQueue);

        int localDataRetentionPeriodInMinutes = edgeConnectorForKVSConfiguration.getLocalDataRetentionPeriodInMinutes();
        recordedFilesRetentionPeriodMap.put(directoryPath, localDataRetentionPeriodInMinutes);

        try (Stream<Path> walk = Files.walk(directoryPath)) {
            walk.filter(p -> !Files.isDirectory(p))
                    //Sort the files by fileName;
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList())
                    .forEach(p -> appendRecordFileToDirPath(directoryPath, p));
        } catch (IOException e) {
            log.error("Unable to initialize recorded files map for dir path: " + directoryPath);
            log.error(e.getMessage());
            throw new IOException(e);
        }
    }

    /***
     * Get video recording period starting date
     * If the latest recorded is 2 minutes ago compare to the current recorded video file, treat it as new recording
     * period, return the current video file timestamp
     * If the latest recorded is 6 days ago, replace the latest recording period start time to current video file
     * timestamp and return it.
     *
     * @param directoryPath  VideoRecordFolderPath
     * @param videoFilePath  new generated video file path
     * @return Date
     */
    public Date getVideoRecordingPeriodStartTime(@NonNull Path directoryPath,
                                                 @NonNull Path videoFilePath) {
        Date videoFileDate = VideoRecordVisitor.getDateFromFilePath(videoFilePath);
        if (!getRecordedFilesMap().containsKey(directoryPath)) {
            return videoFileDate;
        } else {
            if (!getRecordingStartTimeMap().containsKey(directoryPath)) {
                getRecordingStartTimeMap().put(directoryPath, videoFileDate);
            } else {
                Instant lastRecordedTime = VideoRecordVisitor.getDateFromFilePath(getRecordedFilesMap()
                        .get(directoryPath).getLast()).toInstant();
                Instant currentFileTime = VideoRecordVisitor.getDateFromFilePath(videoFilePath).toInstant();
                Instant recordingStartTime = getRecordingStartTimeMap().get(directoryPath).toInstant();
                if (lastRecordedTime != null && currentFileTime != null) {
                    if (isUsingCurrentTimeAsNewStartTime(lastRecordedTime, currentFileTime) ||
                            isNeedToSwitchRecordingStartTime(recordingStartTime, currentFileTime)) {
                        getRecordingStartTimeMap().put(directoryPath, Date.from(currentFileTime));
                    }
                }
            }
        }
        return getRecordingStartTimeMap().get(directoryPath);
    }

    /***
     * Return whether need to using current file time as new recording start time.
     * If the lastRecordedTime is 2 minutes ago, treat currentFileTime as the new file recording start time
     * @param lastRecordedTime lastRecordedTime
     * @param currentFileTime currentFileTime
     * @return true|false
     */
    private boolean isUsingCurrentTimeAsNewStartTime(Instant lastRecordedTime, Instant currentFileTime) {
        return currentFileTime.minus(MAX_EXPECTED_VIDEO_FILE_TIME_GAP_IN_MINUTES, ChronoUnit.MINUTES)
                .isAfter(lastRecordedTime);
    }

    /***
     * Return whether need to switch recording start time.
     * Currently SiteWise just
     * Check  https://docs.aws.amazon.com/iot-sitewise/latest/APIReference/API_BatchPutAssetPropertyValue.html for
     * more details only TQVs that have a timestamp of no more than 7 days in the past and no more than 10 minutes in
     * the future.
     * Edge connector for KVS use recordingStartTime for SiteWise's property's timestamp. So need to switch this value
     * to prevent the value exceeds 7 days' limitation.
     * Currently use MAX_TIME_GAP_FOR_BACKFILL_SITEWISE_TIMESTAMP_IN_MINUTES as the time gap, 6 days.
     * @param recordingStartTime recordingStartTime
     * @param currentFileTime currentFileTime
     * @return true|false
     */
    private boolean isNeedToSwitchRecordingStartTime(Instant recordingStartTime, Instant currentFileTime) {
        return currentFileTime.minus(MAX_TIME_GAP_FOR_BACKFILL_SITEWISE_TIMESTAMP_IN_MINUTES,
                ChronoUnit.MINUTES).isAfter(recordingStartTime);
    }
}
