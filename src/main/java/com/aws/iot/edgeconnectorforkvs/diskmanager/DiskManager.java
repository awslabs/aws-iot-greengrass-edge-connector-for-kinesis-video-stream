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

import com.aws.iot.edgeconnectorforkvs.diskmanager.callback.WatchEventCallBack;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/***
 * DiskManagers for video files monitoring and clean up.
 * This class sets up watchService and watch files changes in the provided dir path. When the oldest video file exceeded
 * the local retention time period, DiskManagers will start file clean thread and remove these files.
 */
@Slf4j
public class DiskManager {
    // Thread lock
    private final Object diskManagementLock = new Object();
    // File monitor thread
    private Future<?> fileMonitorThread;
    // The executor which runs watch service
    private ExecutorService watchServiceExecutor;
    // The scheduled executor which runs file cleaner thread
    private ScheduledExecutorService fileCleanerService;
    // EdgeConnectorForKVSConfiguration lists
    private List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList;
    // DiskManagerUtil
    private DiskManagerUtil diskManagerUtil;
    // Call back been triggered by WatchEvent
    private WatchEventCallBack callback;

    public DiskManager(@NonNull List<EdgeConnectorForKVSConfiguration>
                               edgeConnectorForKVSConfigurationList,
                       @NonNull DiskManagerUtil diskManagerUtil,
                       @NonNull ExecutorService watchServiceExecutor,
                       @NonNull ScheduledExecutorService fileCleanerService,
                       @NonNull WatchEventCallBack callback) {
        this.edgeConnectorForKVSConfigurationList = edgeConnectorForKVSConfigurationList;
        this.watchServiceExecutor = watchServiceExecutor;
        this.fileCleanerService = fileCleanerService;
        this.diskManagerUtil = diskManagerUtil;
        this.callback = callback;
    }

    /***
     * Method to initialize local cached video file records.
     * The method all walk all given dir paths, generate Map, key is directoryPath, value is the
     * ConcurrentLinkedQueue which contains all existing video files.
     * @throws EdgeConnectorForKVSException - EdgeConnectorForKVS generic exception
     */
    public void initDiskManager() throws EdgeConnectorForKVSException {
        diskManagerUtil.buildEdgeConnectorForKVSConfigurationMap(edgeConnectorForKVSConfigurationList);
        edgeConnectorForKVSConfigurationList.forEach(configuration -> {
            try {
                //Walk and build the existing records queue from existing recorded files
                diskManagerUtil.buildRecordedFilesMap(configuration);
            } catch (IOException e) {
                log.error(String.format("Fail to initialize recorded file map for path %s",
                        configuration.getVideoRecordFolderPath().toString()));
                throw new EdgeConnectorForKVSException(e);
            }
        });
    }

    /***
     * Method to start up disk manager thread.
     */
    public void setupDiskManagerThread() throws EdgeConnectorForKVSException {
        synchronized (diskManagementLock) {
            if (fileMonitorThread != null) {
                fileMonitorThread.cancel(true);
            }
            fileMonitorThread = watchServiceExecutor.submit(() -> {
                try {
                    setupWatchService();
                } catch (Exception e) {
                    throw new EdgeConnectorForKVSException(e);
                }
            });
            fileCleanerService.scheduleAtFixedRate(
                    VideoFileManager.builder().
                            diskManagerUtil(diskManagerUtil)
                            .edgeConnectorForKVSConfigurationList(edgeConnectorForKVSConfigurationList)
                            .build(),
                    Constants.FILE_CLEANER_SERVICE_FIRST_EXECUTE_TIME_DELAY_IN_MINUTES,
                    Constants.FILE_CLEANER_SERVICE_EXECUTE_FREQUENCY_IN_MINUTES,
                    TimeUnit.MINUTES);
        }
    }

    private void setupWatchService() throws IOException {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            try {
                edgeConnectorForKVSConfigurationList.forEach(configuration -> {
                    Path path = configuration.getVideoRecordFolderPath();
                    try {
                        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                        log.info(String.format("Start watch service for video record path: %s",
                                path.getFileName()));
                    } catch (IOException e) {
                        log.error(String.format("Start watch service for video record path: %s failed!",
                                path.toString()));
                        throw new EdgeConnectorForKVSException(e);
                    }
                });

                while (true) {
                    final WatchKey watchKey = watchService.take();
                    final Watchable watchable = watchKey.watchable();

                    if (watchable instanceof Path) {
                        final Path directoryPath = (Path) watchable;
                        for (WatchEvent<?> event : watchKey.pollEvents()) {
                            this.callback.handleWatchEvent(directoryPath, event, diskManagerUtil);
                        }
                    }

                    if (!watchKey.reset()) {
                        log.warn("Disk Management run into issue. Restarting the process...");
                        setupDiskManagerThread();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                log.error("Disk Management process interrupted! ");
                Thread.currentThread().interrupt();
            }
        }
    }
}
