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

package com.aws.iot.edgeconnectorforkvs;

import static com.aws.iot.edgeconnectorforkvs.controller.LiveVideoStreamingController.startLiveVideoStreaming;
import static com.aws.iot.edgeconnectorforkvs.controller.LiveVideoStreamingController.stopLiveVideoStreaming;
import static com.aws.iot.edgeconnectorforkvs.controller.RecordingController.deInitVideoRecorders;
import static com.aws.iot.edgeconnectorforkvs.controller.RecordingController.startRecordingJob;
import static com.aws.iot.edgeconnectorforkvs.controller.RecordingController.stopRecordingJob;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.ENV_VAR_REGION;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.EXTRA_THREADS_PER_POOL;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.PATH_DELIMITER;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.SECRETS_MANAGER_SECRET_KEY;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.START_TIME_EXPR_ALWAYS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.START_TIME_EXPR_NEVER;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.WAIT_TIME_BEFORE_POLLING_IN_MILLISECS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.WAIT_TIME_BEFORE_RESTART_IN_MILLISECS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.WORK_DIR_ROOT_PATH;
import static com.aws.iot.edgeconnectorforkvs.util.CredentialsUtils.getContainerCredentialsProviderV1;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.model.StreamInfo;
import com.amazonaws.util.StringUtils;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.KvsClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SecretsClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SiteWiseClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SiteWiseManager;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.diskmanager.DiskManager;
import com.aws.iot.edgeconnectorforkvs.diskmanager.DiskManagerUtil;
import com.aws.iot.edgeconnectorforkvs.diskmanager.callback.FileHandlingCallBack;
import com.aws.iot.edgeconnectorforkvs.handler.VideoUploadRequestEvent;
import com.aws.iot.edgeconnectorforkvs.handler.VideoUploadRequestEventImpl;
import com.aws.iot.edgeconnectorforkvs.handler.VideoUploadRequestHandler;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSUnrecoverableException;
import com.aws.iot.edgeconnectorforkvs.scheduler.JobScheduler;
import com.aws.iot.edgeconnectorforkvs.scheduler.SchedulerCallback;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import com.aws.iot.edgeconnectorforkvs.util.JSONUtils;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploader;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploaderClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.io.PipedInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Builder
@Setter
@AllArgsConstructor
public class EdgeConnectorForKVSService implements SchedulerCallback {
    private static String hubSiteWiseAssetId;
    private SiteWiseClient siteWiseClient;
    private SiteWiseManager siteWiseManager;
    private SecretsClient secretsClient;
    private KvsClient kvsClient;
    private static List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList;
    private String videoRecordingRootPath;
    private ExecutorService recorderService;
    private String regionName;
    private Map<String, EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationMap;
    private com.amazonaws.auth.AWSCredentialsProvider awsCredentialsProviderV1;
    @Getter
    private JobScheduler jobScheduler;
    private ExecutorService liveStreamingExecutor;
    @Getter
    private ScheduledExecutorService stopLiveStreamingExecutor;
    private StreamManager.StreamManagerBuilder streamManagerBuilder;
    private VideoUploadRequestHandler videoUploadRequestHandler;

    public EdgeConnectorForKVSService() {
        this.regionName = System.getenv(ENV_VAR_REGION);
        this.videoRecordingRootPath = WORK_DIR_ROOT_PATH;
        this.awsCredentialsProviderV1 = getContainerCredentialsProviderV1();
        this.siteWiseClient = new SiteWiseClient(ContainerCredentialsProvider.builder().build(),
                Region.of(regionName));
        this.siteWiseManager = SiteWiseManager.builder().siteWiseClient(siteWiseClient).build();
        this.secretsClient = new SecretsClient(ContainerCredentialsProvider.builder().build(), Region.of(regionName));
        this.kvsClient = new KvsClient(awsCredentialsProviderV1,
                com.amazonaws.regions.Region.getRegion(Regions.fromName(regionName)));
        this.edgeConnectorForKVSConfigurationMap = new Hashtable<>();
        this.streamManagerBuilder = StreamManager.builder();
        this.videoUploadRequestHandler = new VideoUploadRequestHandler();
    }

    public EdgeConnectorForKVSService(@NonNull String regionName,
                                      @NonNull VideoUploadRequestHandler videoUploadRequestHandler) {
        this.regionName = regionName;
        this.videoRecordingRootPath = WORK_DIR_ROOT_PATH;
        this.awsCredentialsProviderV1 = getContainerCredentialsProviderV1();
        this.siteWiseClient = new SiteWiseClient(ContainerCredentialsProvider.builder().build(),
                Region.of(regionName));
        this.siteWiseManager = SiteWiseManager.builder().siteWiseClient(siteWiseClient).build();
        this.secretsClient = new SecretsClient(ContainerCredentialsProvider.builder().build(), Region.of(regionName));
        this.kvsClient = new KvsClient(awsCredentialsProviderV1,
                com.amazonaws.regions.Region.getRegion(Regions.fromName(regionName)));
        this.edgeConnectorForKVSConfigurationMap = new Hashtable<>();
        this.streamManagerBuilder = StreamManager.builder();
        this.videoUploadRequestHandler = videoUploadRequestHandler;
    }

    public void setUpSharedEdgeConnectorForKVSService() throws IOException {
        // Step 1. Get configuration from GG configuration
        //         Verify input configurations. Get all Camera configuration from SiteWise asset.
        //         Sink SiteWise assets values.
        initConfiguration();
        // Step 2. Retrieve RTSP Stream URL from Secrets
        initSecretsManager();
        // Step 3. Verify provided KVS name. Create KVS stream if resource not found.
        initKinesisVideoStream();
        // Step 4. Init disk management thread. Including heart beat.
        initDiskManagement();
        // Step 5. Init Stream Managers to push data to SiteWise
        initStreamManagers();
    }

    public void setUpCameraLevelEdgeConnectorForKVSService(final EdgeConnectorForKVSConfiguration configuration) {
        edgeConnectorForKVSConfigurationMap.putIfAbsent(configuration.kinesisVideoStreamName, configuration);
        // Step 6. Init scheduler
        initScheduler(configuration);
        // Step 7. Init video recorder
        initVideoRecorders(configuration);
        // Step 8. Init video uploader
        initVideoUploaders(configuration);
        // Step 9. Subscribe to MQTT
        initMQTTSubscription(configuration);
        // Step 10. Start scheduler at the end
        jobScheduler.start();
    }

    private void initConfiguration() throws IOException {
        // Pull and init needed camera configurations from SiteWise
        edgeConnectorForKVSConfigurationList = siteWiseManager
                .initEdgeConnectorForKVSServiceConfiguration(hubSiteWiseAssetId);
        for (EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration : edgeConnectorForKVSConfigurationList) {
            generateRecordingPath(edgeConnectorForKVSConfiguration);
            edgeConnectorForKVSConfiguration.setProcessLock(new ReentrantLock());
            edgeConnectorForKVSConfiguration.setRecordingRequestsCount(0);
            edgeConnectorForKVSConfiguration.setLiveStreamingRequestsCount(0);
            edgeConnectorForKVSConfiguration.setFatalStatus(new AtomicBoolean(false));
        }
        edgeConnectorForKVSConfigurationMap = edgeConnectorForKVSConfigurationList.stream()
                .collect(Collectors.toMap(EdgeConnectorForKVSConfiguration::getKinesisVideoStreamName,
                        Function.identity()));
    }

    private void initSecretsManager() {
        log.info("Retrieving Secret Value for RTSP Stream URL");
        edgeConnectorForKVSConfigurationList
                .forEach(configuration -> {
                    String secretValue = this.secretsClient.getSecretValue(configuration.getRtspStreamSecretARN());
                    Map<String, Object> jsonMap = JSONUtils.jsonToMap(secretValue);
                    jsonMap.forEach((k, v) -> {
                        if (k.equalsIgnoreCase(SECRETS_MANAGER_SECRET_KEY)) {
                            String rtspStreamURL = (String) v;
                            log.trace(rtspStreamURL);
                            configuration.setRtspStreamURL(rtspStreamURL);
                        }
                    });
                });
    }

    private void initDiskManagement() {
        DiskManager diskManager = new DiskManager(
                edgeConnectorForKVSConfigurationList,
                new DiskManagerUtil(),
                Executors.newCachedThreadPool(),
                Executors.newScheduledThreadPool(1),
                new FileHandlingCallBack()
        );
        diskManager.initDiskManager();
        diskManager.setupDiskManagerThread();
    }

    private void initStreamManagers() {
        edgeConnectorForKVSConfigurationList
                .forEach(configuration -> {
                    StreamManager streamManager = streamManagerBuilder.build();
                    String msgStreamName = configuration.getKinesisVideoStreamName() + "-" +
                            UUID.randomUUID().toString();
                    log.info("Creating Message Stream: " + msgStreamName);
                    streamManager.createMessageStream(msgStreamName);
                    configuration.setStreamManager(streamManager);
                });
    }

    private void initKinesisVideoStream() {
        for (EdgeConnectorForKVSConfiguration configuration : edgeConnectorForKVSConfigurationList) {
            String kinesisVideoStreamName = configuration.getKinesisVideoStreamName();
            Optional<StreamInfo> result = kvsClient.describeStream(kinesisVideoStreamName);
            if (!result.isPresent()) {
                log.info("Configured KinesisVideoStream name :"
                        + kinesisVideoStreamName + " does not exist, creating it ...");
                kvsClient.createStream(kinesisVideoStreamName);
                log.info("Created KinesisVideoStream name :" + kinesisVideoStreamName);
            }
        }
    }

    private void initScheduler(EdgeConnectorForKVSConfiguration configuration) {
        jobScheduler = new JobScheduler(this);
        // Video Capture
        if (!StringUtils.isNullOrEmpty(configuration.getCaptureStartTime()) &&
                !configuration.getCaptureStartTime().equals(START_TIME_EXPR_ALWAYS) &&
                !configuration.getCaptureStartTime().equals(START_TIME_EXPR_NEVER)) {
            jobScheduler.scheduleJob(Constants.JobType.LOCAL_VIDEO_CAPTURE,
                    configuration.getKinesisVideoStreamName(), configuration.getCaptureStartTime(),
                    configuration.getCaptureDurationInMinutes());
        }
        // Live Streaming
        if (!StringUtils.isNullOrEmpty(configuration.getLiveStreamingStartTime()) &&
                !configuration.getLiveStreamingStartTime().equals(START_TIME_EXPR_ALWAYS) &&
                !configuration.getLiveStreamingStartTime().equals(START_TIME_EXPR_NEVER)) {
            jobScheduler.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING,
                    configuration.getKinesisVideoStreamName(), configuration.getLiveStreamingStartTime(),
                    configuration.getLiveStreamingDurationInMinutes());
        }
    }

    private void initVideoRecorders(EdgeConnectorForKVSConfiguration configuration) {
        // Initialize recorderService once for all cameras
        if (recorderService == null) {
            recorderService = Executors.newFixedThreadPool(edgeConnectorForKVSConfigurationList.size()
                    + EXTRA_THREADS_PER_POOL);
        }
        // Start recording only for assets where CaptureStartTime is set to "* * * * *"
        if (!StringUtils.isNullOrEmpty(configuration.getCaptureStartTime()) &&
                configuration.getCaptureStartTime().equals(START_TIME_EXPR_ALWAYS)) {
            log.info("Continuous recording configured for stream " + configuration.getKinesisVideoStreamName());
            recorderService.submit(() -> {
                startRecordingJob(configuration, recorderService);
            });
        }
    }

    private void generateRecordingPath(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration)
            throws IOException {
        String filePath =
                videoRecordingRootPath + edgeConnectorForKVSConfiguration.getSiteWiseAssetId() + PATH_DELIMITER;
        //Create target folder if not exists
        Files.createDirectories(Paths.get(filePath));
        edgeConnectorForKVSConfiguration.setVideoRecordFolderPath(
                Paths.get(filePath)
        );
    }

    private void initVideoUploaders(EdgeConnectorForKVSConfiguration configuration) {
        if (liveStreamingExecutor == null) {
            liveStreamingExecutor = Executors.newFixedThreadPool(
                    edgeConnectorForKVSConfigurationList.size() + EXTRA_THREADS_PER_POOL);
        }
        if (stopLiveStreamingExecutor == null) {
            stopLiveStreamingExecutor = Executors.newScheduledThreadPool(
                    edgeConnectorForKVSConfigurationList.size() + EXTRA_THREADS_PER_POOL);
        }
        configuration.setInputStream(new PipedInputStream());
        configuration.setVideoUploader(generateVideoUploader(configuration));
        // Start live streaming only for assets where LiveStreamingStartTime is set to "* * * * *"
        if (!StringUtils.isNullOrEmpty(configuration.getLiveStreamingStartTime()) &&
                configuration.getLiveStreamingStartTime().equals(START_TIME_EXPR_ALWAYS)) {
            log.info("Continuous live streaming configured for stream " +
                    configuration.getKinesisVideoStreamName());
            liveStreamingExecutor.submit(() -> {
                try {
                    startLiveVideoStreaming(configuration, recorderService);
                } catch (Exception ex) {
                    log.error("Start Live Streaming Exception ({}): {}", ex.getClass().getName(),
                            ex.getMessage());
                    configuration.getFatalStatus().set(true);
                }
            });
        }
    }

    private VideoUploader generateVideoUploader(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration) {
        VideoUploaderClient.VideoUploaderClientBuilder videoUploaderClientBuilder =
                VideoUploaderClient.builder()
                        .awsCredentialsProvider(awsCredentialsProviderV1)
                        .region(com.amazonaws.regions.Region.getRegion(Regions.fromName(regionName)))
                        .kvsStreamName(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName())
                        .recordFilePath(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath().toString());
        edgeConnectorForKVSConfiguration.setVideoUploaderClientBuilder(videoUploaderClientBuilder);
        return videoUploaderClientBuilder.build();
    }

    @Override
    public void schedulerStartTaskCallback(Constants.@NonNull JobType jobType, @NonNull String streamName) {
        log.info("Start Scheduled Task - " + jobType.name() + " Stream: " + streamName);
        final EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration =
                edgeConnectorForKVSConfigurationMap.get(streamName);
        if (jobType == Constants.JobType.LIVE_VIDEO_STREAMING) {
            liveStreamingExecutor.submit(() -> {
                try {
                    startLiveVideoStreaming(edgeConnectorForKVSConfiguration, recorderService);
                } catch (Exception ex) {
                    log.error("Start Live Streaming Exception ({}): {}", ex.getClass().getName(),
                            ex.getMessage());
                    edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
                }
            });
        } else if (jobType == Constants.JobType.LOCAL_VIDEO_CAPTURE) {
            recorderService.submit(() -> {
                try {
                    startRecordingJob(edgeConnectorForKVSConfiguration, recorderService);
                } catch (Exception ex) {
                    log.error("Start Recording Exception ({}): {}", ex.getClass().getName(),
                            ex.getMessage());
                    edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
                }
            });
        }
    }

    @Override
    public void schedulerStopTaskCallback(Constants.@NonNull JobType jobType, @NonNull String streamName) {
        log.info("Stop Scheduled Task - " + jobType.name() + " Stream: " + streamName);
        final EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration =
                edgeConnectorForKVSConfigurationMap.get(streamName);
        if (jobType == Constants.JobType.LIVE_VIDEO_STREAMING) {
            try {
                stopLiveVideoStreaming(edgeConnectorForKVSConfiguration);
            } catch (Exception ex) {
                log.error("Exception on schedulerStopTaskCallback - stopLiveVideoStreaming: " + ex.getMessage());
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } else if (jobType == Constants.JobType.LOCAL_VIDEO_CAPTURE) {
            stopRecordingJob(edgeConnectorForKVSConfiguration);
        }
    }

    private void initMQTTSubscription(EdgeConnectorForKVSConfiguration configuration) {
        VideoUploadRequestEvent event = new VideoUploadRequestEventImpl();
        if (configuration.getVideoUploadRequestMqttTopic() != null) {
            videoUploadRequestHandler.subscribeToMqttTopic(configuration.getVideoUploadRequestMqttTopic(), event,
                    configuration, recorderService, liveStreamingExecutor, stopLiveStreamingExecutor);
        }
    }

    @Synchronized
    public void cleanUpEdgeConnectorForKVSService(
            EdgeConnectorForKVSConfiguration restartNeededConfiguration) {
        // DeInit all cameras if camera level restart failed
        if (restartNeededConfiguration == null) {
            log.info("Edge connector for KVS service is cleaning up for all cameras");
            jobScheduler.stopAllCameras();
            edgeConnectorForKVSConfigurationList.forEach(configuration -> {
                // Deinit video uploader
                stopLiveVideoStreaming(configuration);
                VideoRecorder videoRecorder = configuration.getVideoRecorder();
                // Deinit video recorder if video recorder is not closed by deInitVideo Uploaders
                if (videoRecorder != null && !videoRecorder.getStatus().equals(RecorderStatus.STOPPED)) {
                    // Unlock the processing lock so we can stop the recorder
                    if (configuration.getProcessLock().isHeldByCurrentThread()) {
                        configuration.getProcessLock().unlock();
                    }
                    deInitVideoRecorders(configuration);
                }
            });
        } else {
            log.info("Edge connector for KVS service is cleaning up for this camera: KVS stream: "
                    + restartNeededConfiguration.getKinesisVideoStreamName() + ", siteWise ID: "
                    + restartNeededConfiguration.getSiteWiseAssetId());
            // Stop scheduler for failed cameras only
            jobScheduler.stop(restartNeededConfiguration);
            // Deinit video uploader
            stopLiveVideoStreaming(restartNeededConfiguration);
            // Deinit video recorder if video recorder is not closed by deInitVideoUploaders
            VideoRecorder restartNeededVideoRecorder = restartNeededConfiguration.getVideoRecorder();
            if (restartNeededVideoRecorder != null
                    && !restartNeededVideoRecorder.getStatus().equals(RecorderStatus.STOPPED)) {
                // Unlock the processing lock so we can stop the recorder
                if (restartNeededConfiguration.getProcessLock().isHeldByCurrentThread()) {
                    restartNeededConfiguration.getProcessLock().unlock();
                }
                deInitVideoRecorders(restartNeededConfiguration);
            }
        }

        log.info("Edge connector for KVS service is cleaned up");
    }

    private static void clearFatalStatus() {
        edgeConnectorForKVSConfigurationList.forEach(configuration -> {
            if (configuration.getFatalStatus().get()) {
                configuration.getFatalStatus().set(false);
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        log.info("---------- EdgeConnectorForKVS Starting ----------");
        boolean retry = true;
        boolean isInitialSetup = true;
        List<String> restartNeededConfigurationList = new ArrayList<>();
        do {
            hubSiteWiseAssetId = args[Constants.ARG_INDEX_SITE_WISE_ASSET_ID_FOR_HUB];
            log.info("EdgeConnectorForKVS Hub Asset Id: " + hubSiteWiseAssetId);
            final EdgeConnectorForKVSService edgeConnectorForKVSService = new EdgeConnectorForKVSService();

            try {
                // set up shared configurations first
                if (isInitialSetup) {
                    edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
                    restartNeededConfigurationList.addAll(edgeConnectorForKVSConfigurationList.stream()
                            .map(EdgeConnectorForKVSConfiguration::getKinesisVideoStreamName)
                            .collect(Collectors.toList()));
                    isInitialSetup = false;
                }
                if (restartNeededConfigurationList == null || restartNeededConfigurationList.isEmpty()) {
                    throw new EdgeConnectorForKVSUnrecoverableException("Unable to initialize component");
                }

                // initialize or re-init camera level configurations
                edgeConnectorForKVSConfigurationList.forEach(configuration -> {
                    if (restartNeededConfigurationList.contains(configuration.kinesisVideoStreamName)) {
                        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(configuration);
                    }
                });

                // clear this configuration list after each restart or initial setup
                restartNeededConfigurationList.clear();
                // block main thread and regularly check camera level health status
                while (true) {
                    for (EdgeConnectorForKVSConfiguration configuration : edgeConnectorForKVSConfigurationList) {
                        if (configuration.getFatalStatus().get()) {
                            log.info("fatal status found for " + configuration.getKinesisVideoStreamName());
                            restartNeededConfigurationList.add(configuration.kinesisVideoStreamName);
                        }
                    }
                    if (!restartNeededConfigurationList.isEmpty()) {
                        //  fatal status was set, throw an exception to trigger restart
                        throw new EdgeConnectorForKVSException("Fatal error reported");
                    }
                    Thread.sleep(WAIT_TIME_BEFORE_POLLING_IN_MILLISECS);
                }
            } catch (final EdgeConnectorForKVSException ex) {
                log.error("Start kicking off camera level restart: " +
                        "Failed {}: {}", ex.getClass().getName(), ex.getMessage());
            } catch (final EdgeConnectorForKVSUnrecoverableException ex) {
                log.error("Unrecoverable exception caught, please re-deploy or restart the Component." +
                        "ERROR: Failed {}: {}", ex.getClass().getName(), ex.getMessage());
                retry = false;
            } catch (final Exception ex) {
                log.error("Uncaught exception found, please re-deploy or restart the Component." +
                        "ERROR: Failed {}: {}", ex.getClass().getName(), ex.getMessage());
                retry = false;
            }

            // clear fatalStatus and clean up failed cameras
            clearFatalStatus();
            edgeConnectorForKVSConfigurationList.forEach(configuration -> {
                if (restartNeededConfigurationList.contains(configuration.kinesisVideoStreamName)) {
                    edgeConnectorForKVSService.cleanUpEdgeConnectorForKVSService(configuration);
                }
            });

            if (retry) {
                // If the fatalStatus is true after camera level restart, re-initializing the component
                boolean isFatalStatusSet = edgeConnectorForKVSConfigurationList.stream()
                        .anyMatch(configuration -> configuration.getFatalStatus().get());
                if (isFatalStatusSet) {
                    log.info("---------- Component Re-initializing ----------");
                    edgeConnectorForKVSService.cleanUpEdgeConnectorForKVSService(null);
                    restartNeededConfigurationList.clear();
                    clearFatalStatus();
                    isInitialSetup = true;
                } else {
                    log.info("---------- Camera Re-starting ----------");
                    // wait for a bit before restarting
                }
                Thread.sleep(WAIT_TIME_BEFORE_RESTART_IN_MILLISECS);
            }
        } while (retry);
    }
}
