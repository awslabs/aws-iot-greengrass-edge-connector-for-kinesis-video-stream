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

import com.aws.iot.edgeconnectorforkvs.diskmanager.callback.TestWatchEventCallBack;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.util.VideoRecordVisitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class DiskManagerTest {

    private DiskManager diskManager;
    private DiskManagerUtil diskManagerUtil;
    private Path filePath;
    private Path filePath1;
    private Path currentFilePath;
    private Path latestHistoricalFilePath;
    private Path latestHistoricalFilePath_1;
    private Path latestHistoricalFilePath_2;
    private static final String FILE_NAME = "file.mvk";
    private static final String FILE_NAME_1 = "file_1.mvk";
    private static final String FILE_NAME_CURRENT_FILE = "video_1636264820948.mkv";
    private static final long currentFileTimeStamp = 1636264820948L;
    private static final String FILE_NAME_LATEST_HISTORICAL_FILE = "video_1636264645000.mkv";
    private static final long latestHistoricalFileTimeStamp = 1636264645000L;
    private static final String FILE_NAME_LATEST_HISTORICAL_1_FILE = "video_1636264765000.mkv";
    private static final long latestHistoricalFileTimeStamp1 = 1636264765000L;
    private static final String FILE_NAME_LATEST_HISTORICAL_2_FILE = "video_1635746365000.mkv";
    private static final long latestHistoricalFileTimeStamp2 = 1635746365000L;
    private static final String MOCK_VALUE = "mockValue";
    private int LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES = 10;
    private int testRepeatTimes = 5;
    private ExecutorService watchServiceExecutor = Executors.newCachedThreadPool();
    private ScheduledExecutorService fileCleanerService = Executors.newScheduledThreadPool(1);
    private TestWatchEventCallBack testWatchEventCallBack;

    @BeforeEach
    public void setUp() {
        diskManagerUtil = new DiskManagerUtil();
    }

    @AfterEach
    public void cleanup() {
        diskManagerUtil.getRecordedFilesMap().clear();
        diskManagerUtil.getRecordedFilesRetentionPeriodMap().clear();
        watchServiceExecutor.shutdown();
        fileCleanerService.shutdown();
    }

    @Test
    public void test_AppendRecordFileToDirPath_Null_RecordedFilesMap(@TempDir Path tempDir) {
        //when
        filePath = tempDir.resolve(FILE_NAME);
        //then
        diskManagerUtil.appendRecordFileToDirPath(tempDir, filePath);
        //verify
        assertEquals(diskManagerUtil.getRecordedFilesMap().get(tempDir).peek(), filePath);
    }

    @Test
    public void test_AppendRecordFileToDirPath_Exist_RecordedFilesMap(@TempDir Path tempDir) {
        //when
        filePath = tempDir.resolve(FILE_NAME);
        filePath1 = tempDir.resolve(FILE_NAME_1);
        //then
        diskManagerUtil.appendRecordFileToDirPath(tempDir, filePath);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, filePath1);
        //verify
        assertEquals(diskManagerUtil.getRecordedFilesMap().get(tempDir).peek(), filePath);
    }

    @Test
    public void test_GetEdgeConnectorForKVSConfigurationFromPath(@TempDir Path tempDir) {
        //when
        EdgeConnectorForKVSConfiguration configuration = new EdgeConnectorForKVSConfiguration();
        diskManagerUtil.getEdgeConnectorForKVSConfigurationMap().put(tempDir, configuration);
        //then and verify
        assertEquals(diskManagerUtil.getEdgeConnectorForKVSConfigurationFromPath(tempDir), configuration);
    }

    @Test
    public void testWatchService_File_Creation_Case(@TempDir Path tempDir) throws InterruptedException, IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = Collections.singletonList(
                EdgeConnectorForKVSConfiguration.builder()
                        .videoRecordFolderPath(tempDir)
                        .localDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                        .build());
        List<WatchEvent<?>> events = new ArrayList<>();
        testWatchEventCallBack = new TestWatchEventCallBack(events);
        diskManager = new DiskManager(edgeConnectorForKVSConfigurationList, diskManagerUtil, watchServiceExecutor,
                fileCleanerService, testWatchEventCallBack);
        //then
        diskManager.setupDiskManagerThread();
        Thread.sleep(3000);
        filePath = tempDir.resolve(FILE_NAME);
        Files.write(filePath, Collections.singletonList(MOCK_VALUE));
        //verify
        synchronized (events) {
            int i = 0;
            while (events.size() < 1 && i < testRepeatTimes) {
                events.wait(3000);
                i++;
            }
            assertEquals(1, events.size());
        }
    }

    @Test
    public void testWatchService_Path_Register_Failed_Case() {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = Collections.singletonList(
                EdgeConnectorForKVSConfiguration.builder()
                        .videoRecordFolderPath(Paths.get("NonExistentFile.txt"))
                        .localDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                        .build());
        List<WatchEvent<?>> events = new ArrayList<>();
        testWatchEventCallBack = new TestWatchEventCallBack(events);
        diskManager = new DiskManager(edgeConnectorForKVSConfigurationList, diskManagerUtil, watchServiceExecutor,
                fileCleanerService, testWatchEventCallBack);
        //then
        try {
            diskManager.setupDiskManagerThread();
        } catch (EdgeConnectorForKVSException e) {
            assertEquals(e.getClass(), EdgeConnectorForKVSException.class);
        }
    }

    @Test
    public void testWatchService_Build_Recorder_Files_Success_Case(@TempDir Path tempDir) {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = Collections.singletonList(
                EdgeConnectorForKVSConfiguration.builder()
                        .videoRecordFolderPath(tempDir)
                        .localDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                        .build());

        filePath = tempDir.resolve(FILE_NAME);

        List<WatchEvent<?>> events = new ArrayList<>();
        testWatchEventCallBack = new TestWatchEventCallBack(events);
        diskManager = new DiskManager(edgeConnectorForKVSConfigurationList, diskManagerUtil, watchServiceExecutor,
                fileCleanerService, testWatchEventCallBack);
        //then
        diskManager.initDiskManager();
        assertTrue(diskManagerUtil.getRecordedFilesMap().size() > 0);
    }

    @Test
    public void testWatchService_Build_Recorder_Files_Exception_Case() {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = Collections.singletonList(
                EdgeConnectorForKVSConfiguration.builder()
                        .videoRecordFolderPath(Paths.get("NonExistentFile.txt"))
                        .localDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                        .build());

        List<WatchEvent<?>> events = new ArrayList<>();
        testWatchEventCallBack = new TestWatchEventCallBack(events);
        diskManager = new DiskManager(edgeConnectorForKVSConfigurationList, diskManagerUtil, watchServiceExecutor,
                fileCleanerService, testWatchEventCallBack);
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () -> diskManager.initDiskManager());
    }

    @Test
    public void testWatchService_File_Monitor_Thread_Exists_Case(@TempDir Path tempDir) throws
            InterruptedException, IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = Collections.singletonList(
                EdgeConnectorForKVSConfiguration.builder()
                        .videoRecordFolderPath(Paths.get("NonExistentFile.txt"))
                        .localDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                        .build());

        List<WatchEvent<?>> events = new ArrayList<>();
        testWatchEventCallBack = new TestWatchEventCallBack(events);
        diskManager = new DiskManager(edgeConnectorForKVSConfigurationList, diskManagerUtil, watchServiceExecutor,
                fileCleanerService, testWatchEventCallBack);
        //then and verify
        diskManager.setupDiskManagerThread();
        diskManager.setupDiskManagerThread();
        Thread.sleep(3000);
        filePath = tempDir.resolve(FILE_NAME);
        Files.write(filePath, Collections.singletonList(MOCK_VALUE));
        //then
        try {
            diskManager.setupDiskManagerThread();
            Thread.sleep(3000);
        } catch (EdgeConnectorForKVSException | InterruptedException e) {
            assertEquals(e.getClass(), EdgeConnectorForKVSException.class);
        }
    }

    @Test
    public void test_getVideoRecordingPeriodStartTime_Not_Exist_DirectoryPath_In_RecordedFilesMap(
            @TempDir Path tempDir) {
        //when
        currentFilePath = tempDir.resolve(FILE_NAME_CURRENT_FILE);
        //then
        Date result = diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, currentFilePath);
        //verify
        assertEquals(result.getTime(), currentFileTimeStamp);
    }

    @Test
    public void test_getVideoRecordingPeriodStartTime_Not_Exist_DirectoryPath_In_RecordingStartTimeMap(
            @TempDir Path tempDir) {
        //when
        currentFilePath = tempDir.resolve(FILE_NAME_CURRENT_FILE);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, currentFilePath);
        //then
        Date result = diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, currentFilePath);
        //verify
        assertEquals(result.getTime(), currentFileTimeStamp);
    }

    @Test
    public void test_getVideoRecordingPeriodStartTime_StartNewRecordingPeriod(@TempDir Path tempDir) {
        //when
        currentFilePath = tempDir.resolve(FILE_NAME_CURRENT_FILE);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, currentFilePath);
        latestHistoricalFilePath = tempDir.resolve(FILE_NAME_LATEST_HISTORICAL_FILE);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, latestHistoricalFilePath);
        diskManagerUtil.getRecordingStartTimeMap().put(tempDir,
                VideoRecordVisitor.getDateFromFilePath(latestHistoricalFilePath));
        //then
        Date result = diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, currentFilePath);
        //verify
        assertEquals(result.getTime(), currentFileTimeStamp);
    }

    @Test
    public void test_getVideoRecordingPeriodStartTime_Not_StartNewRecordingPeriod(@TempDir Path tempDir) {
        //when
        currentFilePath = tempDir.resolve(FILE_NAME_CURRENT_FILE);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, currentFilePath);
        latestHistoricalFilePath_1 = tempDir.resolve(FILE_NAME_LATEST_HISTORICAL_1_FILE);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, latestHistoricalFilePath_1);
        diskManagerUtil.getRecordingStartTimeMap().put(tempDir,
                VideoRecordVisitor.getDateFromFilePath(latestHistoricalFilePath_1));
        //then
        Date result = diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, currentFilePath);
        //verify
        assertEquals(result.getTime(), latestHistoricalFileTimeStamp1);
    }

    @Test
    public void test_getVideoRecordingPeriodStartTime_ChangeNewStartTimeStamp(@TempDir Path tempDir) {
        //when
        currentFilePath = tempDir.resolve(FILE_NAME_CURRENT_FILE);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, currentFilePath);
        latestHistoricalFilePath_2 = tempDir.resolve(FILE_NAME_LATEST_HISTORICAL_2_FILE);
        diskManagerUtil.appendRecordFileToDirPath(tempDir, latestHistoricalFilePath_2);
        diskManagerUtil.getRecordingStartTimeMap().put(tempDir,
                VideoRecordVisitor.getDateFromFilePath(latestHistoricalFilePath_2));
        //then
        Date result = diskManagerUtil.getVideoRecordingPeriodStartTime(tempDir, currentFilePath);
        //verify
        assertEquals(result.getTime(), currentFileTimeStamp);
    }
}
