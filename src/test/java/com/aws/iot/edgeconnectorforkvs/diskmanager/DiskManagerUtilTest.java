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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DiskManagerUtilTest {

    private DiskManagerUtil diskManagerUtil;

    @TempDir
    static Path tempDir;

    private Path filePath_0, filePath_1, filePath_2, filePath_3;

    private static final String FILE_NAME_0 = "file0.mvk";
    private static final String FILE_NAME_1 = "file1.mvk";
    private static final String FILE_NAME_2 = "file2.mkv";
    private static final String FILE_NAME_3 = "video_1624914285708.mkv";
    private static final long FILE_NAME_3_TIMESTAMP = 1624914285708L;
    private static final String MOCK_VALUE = "mockValue";
    public static final String MOCK_DIR_PATH = "mockDirPath";

    private EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration;

    private int LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES = 10;

    @BeforeEach
    public void setUp() {
        diskManagerUtil = new DiskManagerUtil();
    }

    @Test
    @Order(1)
    public void testAppendRecordFileToDirPath_Success() {
        //then
        filePath_0 = tempDir.resolve(FILE_NAME_0);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, filePath_0);
        //verify
        assertEquals(diskManagerUtil.getRecordedFilesMap().size(), 1);
        assertEquals(diskManagerUtil.getRecordedFilesMap().get(tempDir).peek(), filePath_0);
    }

    @Test
    @Order(2)
    public void testAppendRecordFileToDirPath_NullParameters() {
        //verify
        assertThrows(NullPointerException.class, () ->
                diskManagerUtil.appendRecordFileToDirPath(null, filePath_1));
        assertThrows(NullPointerException.class, () ->
                diskManagerUtil.appendRecordFileToDirPath(tempDir, null));
    }

    @Test
    @Order(3)
    public void testBuildRecordedFilesMap_Success() throws IOException {
        //when
        filePath_1 = tempDir.resolve(FILE_NAME_1);
        filePath_2 = tempDir.resolve(FILE_NAME_2);

        List<String> lines = Collections.singletonList(MOCK_VALUE);
        Files.write(filePath_1, lines);
        Files.write(filePath_2, lines);

        edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .videoRecordFolderPath(tempDir)
                .localDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                .build();
        //then
        diskManagerUtil.buildRecordedFilesMap(edgeConnectorForKVSConfiguration);
        //verify
        assertTrue(diskManagerUtil.getRecordedFilesMap().get(tempDir).contains(filePath_1));
        assertTrue(diskManagerUtil.getRecordedFilesMap().get(tempDir).contains(filePath_2));
    }

    @Test
    @Order(4)
    public void testBuildRecordedFilesMap_Fail(){
        Path notExistDirPath = Paths.get(MOCK_DIR_PATH);
        edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .videoRecordFolderPath(notExistDirPath)
                .localDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                .build();
        //then and verify
        assertThrows(IOException.class, () ->
                diskManagerUtil.buildRecordedFilesMap(edgeConnectorForKVSConfiguration));
    }

    @Test
    @Order(5)
    public void testGetVideoRecordingPeriodStartTimeStamp_RecordedFilesMap_Empty(){
        //when
        Path videoPath = Paths.get(FILE_NAME_3);
        //then and verify
        assertEquals(diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, videoPath).getTime(),
                FILE_NAME_3_TIMESTAMP);
    }

    @Test
    @Order(6)
    public void testGetVideoRecordingPeriodStartTimeStamp_RecordingStartTimeMap_DoesNotContainsDirectoryPath(){
        //when
        filePath_3 = tempDir.resolve(FILE_NAME_3);
        ConcurrentLinkedDeque<Path> deque = new ConcurrentLinkedDeque<>();
        deque.add(filePath_3);
        diskManagerUtil.getRecordedFilesMap().put(tempDir, deque);
        //then and verify
        assertEquals(diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, filePath_3).getTime(),
                FILE_NAME_3_TIMESTAMP);
    }

    @Test
    @Order(6)
    public void testGetVideoRecordingPeriodStartTimeStamp_RecordingStartTimeMap_LessThanTimeGap(){
        //when
        filePath_3 = tempDir.resolve(FILE_NAME_3);
        Instant file3Time = (new Date(FILE_NAME_3_TIMESTAMP)).toInstant();
        // Less than MAX_EXPECTED_VIDEO_FILE_TIME_GAP_IN_MINUTES
        Instant oldestFileTime = file3Time.minusSeconds(119);
        String oldestFileName = "video_" + oldestFileTime.toEpochMilli() +".mkv";
        Path oldestFilePath = tempDir.resolve(oldestFileName);
        ConcurrentLinkedDeque<Path> deque = new ConcurrentLinkedDeque<>();
        deque.add(oldestFilePath);

        diskManagerUtil.getRecordedFilesMap().put(tempDir, deque);
        diskManagerUtil.getRecordingStartTimeMap().put(tempDir, Date.from(oldestFileTime));
        //then and verify
        assertEquals(diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, filePath_3).getTime(),
                oldestFileTime.toEpochMilli());
    }

    @Test
    @Order(7)
    public void testGetVideoRecordingPeriodStartTimeStamp_RecordingStartTimeMap_LongerThanTimeGap(){
        //when
        filePath_3 = tempDir.resolve(FILE_NAME_3);
        Instant file3Time = (new Date(FILE_NAME_3_TIMESTAMP)).toInstant();
        // More than MAX_EXPECTED_VIDEO_FILE_TIME_GAP_IN_MINUTES
        Instant oldestFileTime = file3Time.minusSeconds(121);
        String oldestFileName = "video_" + oldestFileTime.toEpochMilli() +".mkv";
        Path oldestFilePath = tempDir.resolve(oldestFileName);
        ConcurrentLinkedDeque<Path> deque = new ConcurrentLinkedDeque<>();
        deque.add(oldestFilePath);

        diskManagerUtil.getRecordedFilesMap().put(tempDir, deque);
        diskManagerUtil.getRecordingStartTimeMap().put(tempDir, Date.from(oldestFileTime));
        //then and verify
        assertEquals(diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, filePath_3).getTime(),
                file3Time.toEpochMilli());
        assertEquals(diskManagerUtil.getRecordingStartTimeMap().get(tempDir).getTime(),
                file3Time.toEpochMilli());
    }
}
