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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.diskmanager.callback.FileHandlingCallBack;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Collections;

@ExtendWith(MockitoExtension.class)
public class FileHandlingCallBackTest {
    private DiskManagerUtil diskManagerUtil;
    private FileHandlingCallBack fileHandlingCallBack;
    private Path filePath;
    private static final String FILE_NAME = "file.mvk";

    private EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration;

    @Mock
    private StreamManager streamManager;

    @Mock
    private WatchEvent event;

    @BeforeEach
    public void setUp() {
        diskManagerUtil = new DiskManagerUtil();
        fileHandlingCallBack = new FileHandlingCallBack();
        edgeConnectorForKVSConfiguration = new EdgeConnectorForKVSConfiguration();
        edgeConnectorForKVSConfiguration.setStreamManager(streamManager);
        when(streamManager.pushData(any(), any(), any(), any())).thenReturn(0L);
    }

    @AfterEach
    public void cleanup() {
        diskManagerUtil.getRecordedFilesMap().clear();
        diskManagerUtil.getRecordedFilesRetentionPeriodMap().clear();
    }

    @Test
    public void testHandleWatchEvent(@TempDir Path tempDir) {
        //when
        filePath = tempDir.resolve(FILE_NAME);
        when(event.context()).thenReturn(filePath);
        edgeConnectorForKVSConfiguration.setVideoRecordFolderPath(tempDir);
        diskManagerUtil.buildEdgeConnectorForKVSConfigurationMap(Collections.singletonList(edgeConnectorForKVSConfiguration));
        //then
        fileHandlingCallBack.handleWatchEvent(tempDir, event, diskManagerUtil);
        //verify
        assertEquals(diskManagerUtil.getRecordedFilesMap().size(), 1);
        assertEquals(diskManagerUtil.getRecordedFilesMap().get(tempDir).size(), 1);
        verify(streamManager,times(1)).pushData(any(), any(), any(), any());
    }
}
