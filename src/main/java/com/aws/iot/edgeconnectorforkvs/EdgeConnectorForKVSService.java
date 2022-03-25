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

import com.amazonaws.internal.CredentialsEndpointProvider;
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
import com.aws.iot.edgeconnectorforkvs.handler.VideoUploadRequestHandler;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSUnrecoverableException;
import com.aws.iot.edgeconnectorforkvs.scheduler.JobScheduler;
import com.aws.iot.edgeconnectorforkvs.scheduler.SchedulerCallback;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import com.aws.iot.edgeconnectorforkvs.util.JSONUtils;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorderBuilder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.GStreamerAppDataCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.CameraType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploader;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploaderClient;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.StatusChangedCallBack;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.UploadCallBack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.WORK_DIR_ROOT_PATH;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.ENV_VAR_REGION;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.EXTRA_THREADS_PER_POOL;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.INIT_LOCK_TIMEOUT_IN_SECONDS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.LIVE_STREAMING_STOP_TIMER_DELAY_IN_SECONDS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.PATH_DELIMITER;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.RECORDING_JOB_WAIT_TIME_IN_SECS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.SECRETS_MANAGER_SECRET_KEY;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.START_TIME_EXPR_ALWAYS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.START_TIME_EXPR_NEVER;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.WAIT_TIME_BEFORE_POLLING_IN_MILLISECS;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.WAIT_TIME_BEFORE_RESTART_IN_MILLISECS;

@Slf4j
@Builder
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
    private final int restartSleepTime = RECORDER_RESTART_TIME_GAP_MILLI_SECONDS;
    private StreamManager.StreamManagerBuilder streamManagerBuilder;
    private VideoUploadRequestHandler videoUploadRequestHandler;
    private boolean retryOnFail;

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
        this.retryOnFail = true;
    }

    private com.amazonaws.auth.ContainerCredentialsProvider getContainerCredentialsProviderV1() {
        return new com.amazonaws.auth.ContainerCredentialsProvider(
            new CredentialsEndpointProvider() {
                @Override
                public URI getCredentialsEndpoint() {
                    return URI.create(System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI"));
                }

                @Override
                public Map<String, String> getHeaders() {
                    if (System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN") != null) {
                        return Collections.singletonMap("Authorization",
                            System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN"));
                    }
                    return new HashMap<String, String>();
                }
            });
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

    public void setUpCameraLevelEdgeConnectorForKVSService(
        // Re-add configuration to map
        final EdgeConnectorForKVSConfiguration configuration) {
        if (edgeConnectorForKVSConfigurationMap.get(configuration.kinesisVideoStreamName) == null) {
            edgeConnectorForKVSConfigurationMap.put(configuration.kinesisVideoStreamName, configuration);
        }
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
                startRecordingJob(configuration);
            });
        }

    }

    private void startRecordingJob(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration) {
        VideoRecorder videoRecorder = null;
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Start Recording called for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));
                edgeConnectorForKVSConfiguration.setRecordingRequestsCount(edgeConnectorForKVSConfiguration
                    .getRecordingRequestsCount() + 1);
                if (edgeConnectorForKVSConfiguration.getRecordingRequestsCount() > 1) {
                    log.info("Recording already running. Requests Count: " +
                        edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
                    return;
                }
                VideoRecorderBuilder builder = new VideoRecorderBuilder(new StatusCallback() {
                    @Override
                    public void notifyStatus(VideoRecorderBase recorder, RecorderStatus status, String description) {
                        String pipeLineName = recorder.getPipeline().getName();
                        log.info("Recorder[" + pipeLineName + "] status changed callback: " + status);
                        // Do not restart recorder when recordingRequestCount is less than 0
                        // Camera level restart is in progress
                        if (status.equals(RecorderStatus.FAILED) &&
                            edgeConnectorForKVSConfiguration.getRecordingRequestsCount() > 0) {
                            log.warn("Recorder failed due to errors. Pipeline name: " + pipeLineName);
                            log.warn("Trying restart recorder");
                            recorderService.submit(() -> {
                                restartRecorder(recorder);
                            });
                        }
                    }

                    private void restartRecorder(@NonNull VideoRecorderBase recorder) {
                        recorder.stopRecording();
                        try {
                            Thread.sleep(restartSleepTime);
                        } catch (InterruptedException e) {
                            log.error("Thread sleep interrupted.");
                        }
                        recorder.startRecording();
                        log.info("Restart Recording for pipeline recorder " + recorder.getPipeline().getName());
                    }
                });
                PipedOutputStream outputStream = new PipedOutputStream();
                builder.registerCamera(CameraType.RTSP, edgeConnectorForKVSConfiguration.getRtspStreamURL());
                builder.registerFileSink(ContainerType.MATROSKA,
                    videoRecordingRootPath + edgeConnectorForKVSConfiguration.getSiteWiseAssetId()
                        + PATH_DELIMITER + "video");
                builder.registerAppDataCallback(ContainerType.MATROSKA, new GStreamerAppDataCallback());
                builder.registerAppDataOutputStream(ContainerType.MATROSKA, outputStream);
                videoRecorder = builder.construct();
                edgeConnectorForKVSConfiguration.setVideoRecorder(videoRecorder);
                edgeConnectorForKVSConfiguration.setOutputStream(outputStream);
                log.info("Recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName() +
                    " has been initialized");
            } else {
                // Recorder cannot init. Will retry in the method end
                log.error("Fail to init recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            }
        } catch (InterruptedException e) {
            log.error("Init recorder process for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
        }

        if (videoRecorder != null) {
            log.info("Start recording for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            videoRecorder.startRecording();
        } else {
            log.error("Fail to init recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        }
    }

    private void stopRecordingJob(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration) {
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(
                INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Stop Recording called for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));
                VideoRecorder videoRecorder = edgeConnectorForKVSConfiguration.getVideoRecorder();
                // Stop video recording provided there's no live streaming / scheduled recording in progress
                edgeConnectorForKVSConfiguration.setRecordingRequestsCount(edgeConnectorForKVSConfiguration
                    .getRecordingRequestsCount() - 1);
                if (edgeConnectorForKVSConfiguration.getRecordingRequestsCount() > 0) {
                    log.info("Recording is requested by multiple tasks. Requests Count " +
                        edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
                    return;
                }
                log.info("Recorder Requests Count is 0. Stopping.");
                int maxRetry = 5;
                while (!videoRecorder.getStatus().equals(RecorderStatus.STOPPED)) {
                    videoRecorder.stopRecording();
                    if (maxRetry > 0) {
                        maxRetry--;
                    } else {
                        log.error("Max retry reached to stop recorder for " +
                            edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                        edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
                        break;
                    }
                    Thread.sleep(WAIT_TIME_BEFORE_RESTART_IN_MILLISECS);
                }
                // Close the pipeline, so we don't have duplicate videos after restart
                if (videoRecorder != null && videoRecorder.getPipeline() != null) {
                    edgeConnectorForKVSConfiguration.getVideoRecorder().getPipeline().close();
                }
            } else {
                log.error("Fail to stop recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } catch (InterruptedException e) {
            log.error("Stop recorder for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
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
                    startLiveVideoStreaming(configuration);
                } catch (Exception ex) {
                    log.error("Start Live Streaming Exception ({}): {}", ex.getClass().getName(),
                        ex.getMessage());
                    configuration.getFatalStatus().set(true);
                }
            });
        }
    }

    private VideoUploader generateVideoUploader(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration) {
        return VideoUploaderClient.builder()
            .awsCredentialsProvider(awsCredentialsProviderV1)
            .region(com.amazonaws.regions.Region.getRegion(Regions.fromName(regionName)))
            .kvsStreamName(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName())
            .recordFilePath(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath().toString())
            .build();
    }

    private void flushInputStream(InputStream inputStream) throws IOException {
        int bytesAvailable = inputStream.available();

        do {
            byte[] b = new byte[bytesAvailable];
            inputStream.read(b);
            bytesAvailable = inputStream.available();
        } while (bytesAvailable > 0);
    }

    private void startHistoricalVideoUploading(EdgeConnectorForKVSConfiguration configuration, long startTime,
                                               long endTime) throws InterruptedException {
        log.info("Start uploading video between " + startTime + " and "
            + endTime + " for stream "
            + configuration.getKinesisVideoStreamName());
        VideoUploader videoUploader = generateVideoUploader(configuration);
        Date dStartTime = new Date(startTime);
        Date dEndTime = new Date(endTime);
        boolean isUploadingFinished = false;

        do {
            try {
                videoUploader.uploadHistoricalVideo(dStartTime, dEndTime,
                    new StatusChangedCallBack(), new UploadCallBack(dStartTime, configuration));
                isUploadingFinished = true;
            } catch (Exception ex) {
                // Log error and retry historical uploading process
                log.error("Failed to upload historical videos: {}", ex.getMessage());
            }
        } while (retryOnFail && !isUploadingFinished);
    }

    private void startLiveVideoStreaming(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration)
        throws IOException, InterruptedException {
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(
                INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Start Live Video Streaming Called for " +
                    edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));
                edgeConnectorForKVSConfiguration.setLiveStreamingRequestsCount(edgeConnectorForKVSConfiguration
                    .getLiveStreamingRequestsCount() + 1);
                if (edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() > 1) {
                    log.info("Live Streaming already running. Requests Count: " +
                        edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
                    return;
                }
            } else {
                log.error("Start uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                    + " timeout, re-init camera to restart the process.");
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } catch (InterruptedException e) {
            log.error("Start uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
        }

        // kick-off recording if it wasn't already started
        Future<?> future = recorderService.submit(() -> {
            startRecordingJob(edgeConnectorForKVSConfiguration);
        });
        try {
            // startRecordingJob is a blocking call, so we wait
            // upto 5 seconds for the recording to start before
            // we start live streaming below
            future.get(RECORDING_JOB_WAIT_TIME_IN_SECS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            log.error("Start Live Streaming Interrupted Exception: " + ex.getMessage());
        } catch (ExecutionException ex) {
            log.error("Start Live Streaming Execution Exception: " + ex.getMessage());
        } catch (TimeoutException ex) {
            // Ignore this exception, it is expected since
            // startRecordingJob is a blocking call
        }

        VideoRecorder videoRecorder = edgeConnectorForKVSConfiguration.getVideoRecorder();
        VideoUploader videoUploader = edgeConnectorForKVSConfiguration.getVideoUploader();

        do {
            PipedOutputStream outputStream = new PipedOutputStream();
            PipedInputStream inputStream = new PipedInputStream();

            // Toggle to false before switching outputStream (may not be required)
            videoRecorder.toggleAppDataOutputStream(false);

            edgeConnectorForKVSConfiguration.setOutputStream(outputStream);
            edgeConnectorForKVSConfiguration.setInputStream(inputStream);
            outputStream.connect(inputStream);
            videoRecorder.setAppDataOutputStream(outputStream);

            log.info("Connected streams for KVS Stream: " +
                edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
            videoRecorder.toggleAppDataOutputStream(true);

            log.info("Turned on outputStream in recorder and start uploading!");
            Date dateNow = new Date();
            try {
                videoUploader.uploadStream(inputStream, dateNow, new StatusChangedCallBack(),
                    new UploadCallBack(dateNow, edgeConnectorForKVSConfiguration));
            } catch (Exception exception) {
                if (processLock.tryLock(INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                    log.error("Failed to upload stream: {}", exception.getMessage());

                    AtomicBoolean isRecorderToggleOff = new AtomicBoolean();
                    Thread toggleRecorderOffThreaed = new Thread(() -> {
                        log.info("Waiting for toggling recorder off");
                        videoRecorder.toggleAppDataOutputStream(false);
                        try {
                            TimeUnit.MILLISECONDS.sleep(2000);
                        } catch (InterruptedException e) {
                            log.error("toggleRecorderOffThread exception: " + e.getMessage());
                        }
                        isRecorderToggleOff.set(true);
                        log.info("Toggling recorder off");
                    });

                    toggleRecorderOffThreaed.start();
                    log.info("InputStream is flushing");
                    try {
                        int bytesAvailable = inputStream.available();
                        while (!isRecorderToggleOff.get() || bytesAvailable > 0) {
                            byte[] b = new byte[bytesAvailable];
                            inputStream.read(b);
                            bytesAvailable = inputStream.available();
                        }
                    } catch (IOException e) {
                        log.error("Exception flush inputStream: " + e.getMessage());
                    } finally {
                        if (processLock.isHeldByCurrentThread()) processLock.unlock();
                    }
                    log.info("InputStream is flushed");

                    outputStream.close();
                    inputStream.close();
                } else {
                    log.error("Restart uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                        + " timeout, re-init camera to restart the process.");
                    edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
                    if (processLock.isHeldByCurrentThread()) processLock.unlock();
                    break;
                }
            }
        } while (retryOnFail && edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() > 0);
    }

    private void stopLiveVideoStreaming(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration)
        throws IOException {
        ReentrantLock processLock = edgeConnectorForKVSConfiguration.getProcessLock();
        try {
            if (processLock.tryLock(
                INIT_LOCK_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS)) {
                log.info("Stop Live Video Streaming Called for " +
                    edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                log.info("Calling function " + Constants.getCallingFunctionName(2));

                // Stop video recording provided there's no scheduled / mqtt based live streaming in progress
                edgeConnectorForKVSConfiguration.setLiveStreamingRequestsCount(
                    edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() - 1);
                if (edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount() > 0) {
                    log.info("Live Streaming is being used by multiple tasks. Requests Count " +
                        edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
                    return;
                }
                log.info("Live Steaming Requests Count is 0. Stopping.");

                VideoRecorder videoRecorder = edgeConnectorForKVSConfiguration.getVideoRecorder();
                VideoUploader videoUploader = edgeConnectorForKVSConfiguration.getVideoUploader();

                log.info("Toggle output stream off for KVS Stream: " +
                    edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                videoRecorder.toggleAppDataOutputStream(false);

                log.info("Close video uploader for KVS Stream: " +
                    edgeConnectorForKVSConfiguration.getKinesisVideoStreamName());
                videoUploader.close();

                // Manually flush input stream
                flushInputStream(edgeConnectorForKVSConfiguration.getInputStream());

                edgeConnectorForKVSConfiguration.getInputStream().close();
                edgeConnectorForKVSConfiguration.getOutputStream().close();
                // Stop recording if it was kicked-off only for this live streaming
                stopRecordingJob(edgeConnectorForKVSConfiguration);
            } else {
                log.error("Stop uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                    + " timeout, re-init camera to restart the process.");
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } catch (InterruptedException e) {
            log.error("Stop uploading for " + edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()
                + " has been interrupted, re-init camera to restart the process.");
            edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
        } finally {
            if (processLock.isHeldByCurrentThread()) processLock.unlock();
        }
    }

    @Override
    public void schedulerStartTaskCallback(Constants.@NonNull JobType jobType, @NonNull String streamName) {
        log.info("Start Scheduled Task - " + jobType.name() + " Stream: " + streamName);
        final EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration =
            edgeConnectorForKVSConfigurationMap.get(streamName);
        if (jobType == Constants.JobType.LIVE_VIDEO_STREAMING) {
            liveStreamingExecutor.submit(() -> {
                try {
                    startLiveVideoStreaming(edgeConnectorForKVSConfiguration);
                } catch (Exception ex) {
                    log.error("Start Live Streaming Exception ({}): {}", ex.getClass().getName(),
                        ex.getMessage());
                    edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
                }
            });
        } else if (jobType == Constants.JobType.LOCAL_VIDEO_CAPTURE) {
            recorderService.submit(() -> {
                try {
                    startRecordingJob(edgeConnectorForKVSConfiguration);
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
            } catch (IOException ex) {
                log.error("Exception on schedulerStopTaskCallback - stopLiveVideoStreaming: " + ex.getMessage());
                edgeConnectorForKVSConfiguration.getFatalStatus().set(true);
            }
        } else if (jobType == Constants.JobType.LOCAL_VIDEO_CAPTURE) {
            stopRecordingJob(edgeConnectorForKVSConfiguration);
        }
    }

    private void initMQTTSubscription(EdgeConnectorForKVSConfiguration configuration) {

        VideoUploadRequestEvent event = new VideoUploadRequestEvent() {
            @Override
            public void onStart(boolean isLive, long updateTimestamp, long startTime, long endTime) {
                if (isLive) {
                    log.info("Received Live Streaming request for stream: "
                        + configuration.getKinesisVideoStreamName());
                    ScheduledFuture<?> future = configuration.getStopLiveStreamingTaskFuture();
                    if (future == null) {
                        // Kick-off Live Streaming for 5 mins
                        // do it only for the first request
                        log.info("Start Live Streaming");
                        liveStreamingExecutor.submit(() -> {
                            try {
                                startLiveVideoStreaming(configuration);
                            } catch (Exception ex) {
                                log.error("Error starting live video streaming." + ex.getMessage());
                                configuration.getFatalStatus().set(true);
                            }
                        });
                    } else {
                        log.info("Live Streaming was already started. Continue Streaming.");
                        // Cancel the previously started scheduled task
                        // and restart the task below
                        future.cancel(false);
                    }
                    Runnable task = getStopLiveStreamingTask(configuration);
                    future = stopLiveStreamingExecutor.schedule(task,
                        LIVE_STREAMING_STOP_TIMER_DELAY_IN_SECONDS, TimeUnit.SECONDS);
                    configuration.setStopLiveStreamingTaskFuture(future);
                    log.info("Schedule Live Streaming to stop after " +
                        LIVE_STREAMING_STOP_TIMER_DELAY_IN_SECONDS + "s for stream: " +
                        configuration.getKinesisVideoStreamName());
                } else {
                    try {
                        startHistoricalVideoUploading(configuration, startTime, endTime);
                    } catch (Exception ex) {
                        log.error("Error starting historical video uploading." + ex.getMessage());
                        configuration.getFatalStatus().set(true);
                    }
                }
            }

            @Override
            public void onError(String errMessage) {
                log.info("MQTT Error " + errMessage + " for stream "
                    + configuration.getKinesisVideoStreamName());
            }
        };
        if (configuration.getVideoUploadRequestMqttTopic() != null) {
            videoUploadRequestHandler.subscribeToMqttTopic(configuration.getVideoUploadRequestMqttTopic(),
                event);
        }
    }

    private Runnable getStopLiveStreamingTask(EdgeConnectorForKVSConfiguration configuration) {
        return () -> {
            try {
                log.info("Stop Live Streaming for stream " +
                    configuration.getKinesisVideoStreamName() +
                    ". Thread's name: " + Thread.currentThread().getName());
                configuration.setStopLiveStreamingTaskFuture(null);
                stopLiveVideoStreaming(configuration);
            } catch (Exception ex) {
                log.error("Error stopping live video streaming." + ex.getMessage());
                configuration.getFatalStatus().set(true);
            }
        };
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
                deInitVideoUploaders(configuration);
                VideoRecorder videoRecorder = configuration.getVideoRecorder();
                // Deinit video recorder if video recorder is not closed by deInitVideoUploaders
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
            deInitVideoUploaders(restartNeededConfiguration);
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

    private void deInitVideoRecorders(EdgeConnectorForKVSConfiguration restartNeededConfiguration) {
        if (!restartNeededConfiguration.getVideoRecorder().getStatus().equals(RecorderStatus.STOPPED)) {
            stopRecordingJob(restartNeededConfiguration);
        }
    }

    private void deInitVideoUploaders(EdgeConnectorForKVSConfiguration restartNeededConfiguration) {
        try {
            stopLiveVideoStreaming(restartNeededConfiguration);
        } catch (IOException e) {
            log.error("Exception when deInitVideoUploaders: " + e.getMessage());
        }
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
            // For windows, we need to remove leading quote and trailing quote
            hubSiteWiseAssetId = hubSiteWiseAssetId.replaceAll("'", "");
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
