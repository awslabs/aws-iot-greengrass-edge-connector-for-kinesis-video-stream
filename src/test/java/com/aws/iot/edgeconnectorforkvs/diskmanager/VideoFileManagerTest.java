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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VideoFileManagerTest {

    private VideoFileManager videoFileManager;

    private List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList;

    private DiskManagerUtil diskManagerUtil;

    private int LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES = 10;

    private int VIDEO_FILE_1_CACHED_TIME_IN_MINUTES = 15;

    private int VIDEO_FILE_2_CACHED_TIME_IN_MINUTES = 0;

    private Path directoryPath;

    private Path videoFilePath_1;

    private Path videoFilePath_2;

    @Mock
    private StreamManager streamManager;

    @Mock
    private Path mockPath;

    private static final String MOCK_FILE_NAME = "mockFileName";

    @BeforeEach
    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public void setUp() {
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = new EdgeConnectorForKVSConfiguration();
        edgeConnectorForKVSConfiguration.setLocalDataRetentionPeriodInMinutes(LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES);
        edgeConnectorForKVSConfiguration.setStreamManager(streamManager);
        when(streamManager.pushData(any(), any(), any(), any())).thenReturn(0L);
        this.directoryPath = Paths.get("/mock/videoTest/");
        edgeConnectorForKVSConfiguration.setVideoRecordFolderPath(directoryPath);
        this.edgeConnectorForKVSConfigurationList = new ArrayList<>();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        long video_file_1_timeStamp = LocalDateTime.now().minusMinutes(
                VIDEO_FILE_1_CACHED_TIME_IN_MINUTES)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        long video_file_2_timeStamp = LocalDateTime.now().minusMinutes(
                VIDEO_FILE_2_CACHED_TIME_IN_MINUTES)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        this.videoFilePath_1 = Paths.get("/mock/videoTest/video_" + video_file_1_timeStamp + ".mkv");
        this.videoFilePath_2 = Paths.get("/mock/videoTest/video_" + video_file_2_timeStamp + ".mkv");

        diskManagerUtil = new DiskManagerUtil();
        diskManagerUtil.appendRecordFileToDirPath(directoryPath, videoFilePath_1);
        diskManagerUtil.appendRecordFileToDirPath(directoryPath, videoFilePath_2);

        videoFileManager = VideoFileManager.builder()
                .diskManagerUtil(diskManagerUtil)
                .edgeConnectorForKVSConfigurationList(edgeConnectorForKVSConfigurationList)
                .build();
    }

    @Test
    public void testCleanUp_Success_Case() {
        //then
        videoFileManager.run();
        //verify
        assertEquals(diskManagerUtil.getRecordedFilesMap().size(), 1);
        assertEquals(diskManagerUtil.getRecordedFilesMap().get(directoryPath).peek(), videoFilePath_2);
    }

    @Test
    public void testCleanUp_DirectoryPath_Does_Not_Exist() {
        //when
        diskManagerUtil.getRecordedFilesMap().clear();
        //then
        videoFileManager.run();
        //verify
        assertEquals(diskManagerUtil.getRecordedFilesMap().size(), 0);
    }

    @Test
    public void testCleanUp_Delete_File_Exception() {
        //when
        diskManagerUtil.getRecordedFilesMap().clear();
        diskManagerUtil.appendRecordFileToDirPath(directoryPath, mockPath);
        when(mockPath.getFileSystem()).thenReturn(null);
        when(mockPath.getFileName()).thenReturn(Paths.get(MOCK_FILE_NAME));
        //then
        videoFileManager.run();
        //verify
        assertEquals(diskManagerUtil.getRecordedFilesMap().size(), 1);
    }

    @Test
    public void testSendHeartBeatMessageToSiteWise_HasOldestVideoFile() {
        //then
        videoFileManager.run();
        //verify
        ArgumentCaptor<String> siteWiseAssetIdArgumentCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cachedVideoAgeOutOnEdgePropertyIdArgumentCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> cachedVideoAgeOutOnEdgeTimeArgumentCaptor =
                ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Optional> optionalArgumentCaptor =
                ArgumentCaptor.forClass(Optional.class);
        verify(streamManager).pushData(siteWiseAssetIdArgumentCaptor.capture(),
                cachedVideoAgeOutOnEdgePropertyIdArgumentCaptor.capture(),
                cachedVideoAgeOutOnEdgeTimeArgumentCaptor.capture(),
                optionalArgumentCaptor.capture());
        assertTrue(cachedVideoAgeOutOnEdgeTimeArgumentCaptor.getValue() != 0);
    }

    @Test
    public void testSendHeartBeatMessageToSiteWise_NotHasOldestVideoFile() {
        //when
        diskManagerUtil.getRecordedFilesMap().clear();
        //then
        videoFileManager.run();
        //verify
        ArgumentCaptor<String> siteWiseAssetIdArgumentCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cachedVideoAgeOutOnEdgePropertyIdArgumentCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> cachedVideoAgeOutOnEdgeTimeArgumentCaptor =
                ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Optional> optionalArgumentCaptor =
                ArgumentCaptor.forClass(Optional.class);
        verify(streamManager).pushData(siteWiseAssetIdArgumentCaptor.capture(),
                cachedVideoAgeOutOnEdgePropertyIdArgumentCaptor.capture(),
                cachedVideoAgeOutOnEdgeTimeArgumentCaptor.capture(),
                optionalArgumentCaptor.capture());
        assertEquals(cachedVideoAgeOutOnEdgeTimeArgumentCaptor.getValue(), 0);
    }
}
