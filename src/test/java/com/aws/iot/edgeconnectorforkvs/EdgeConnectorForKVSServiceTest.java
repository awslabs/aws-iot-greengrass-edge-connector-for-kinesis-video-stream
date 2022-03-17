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

import static com.aws.iot.edgeconnectorforkvs.util.Constants.START_TIME_EXPR_ALWAYS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;

import com.aws.iot.edgeconnectorforkvs.controller.LiveVideoStreamingController;
import com.aws.iot.edgeconnectorforkvs.controller.RecordingController;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.KvsClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SecretsClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SiteWiseClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SiteWiseManager;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.handler.VideoUploadRequestHandler;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EdgeConnectorForKVSServiceTest {

    private EdgeConnectorForKVSService edgeConnectorForKVSService;

    private static final String MOCK_REGION_NAME = "us-east-1";
    private static final String MOCK_KINESIS_VIDEO_STREAM_NAME = "kinesisVideoStreamName";
    private static final String MOCK_RTSP_STREAM_URL = "rtspStreamURL";
    private static final String LIVE_STREAMING_START_TIME = "*/30 * 1 * ?";
    private static final int LIVE_STREAMING_DURATION_IN_MINUTES = 10;
    private static final String CAPTURE_START_TIME = "*/30 * 1 * ?";
    private static final int CAPTURE_DURATION_IN_MINUTES = 10;
    private static final String START_TIME_NEVER = "-";
    private static final Map secretMap = Collections.singletonMap(Constants.SECRETS_MANAGER_SECRET_KEY,
            MOCK_RTSP_STREAM_URL);
    private static final String VIDEO_UPLOAD_REQUEST_MQTT_TOPIC = "$aws/sitewise/asset-models/123/" +
            "assets/456/properties/789";
    private static final long START_TIME = 1630985820;
    private static final long END_TIME = 1630985920;
    private static final long EVENT_TIMESTAMP = 1630985924;
    private static final boolean IS_LIVE_TRUE = true;
    private static final boolean IS_LIVE_FALSE = false;

    @Mock
    SiteWiseClient siteWiseClient;
    @Mock
    SiteWiseManager siteWiseManager;
    @Mock
    SecretsClient secretsClient;
    @Mock
    AWSCredentialsProvider awsCredentialsProvider;
    @Mock
    KvsClient kvsClient;
    @Mock
    VideoRecorder videoRecorder;
    @Mock
    StreamManager streamManager;
    @Mock
    VideoUploadRequestHandler videoUploadRequestHandler;
    @Mock
    StreamManager.StreamManagerBuilder streamManagerBuilder;

    Gson gson = new Gson();

    @BeforeEach
    public void init(@TempDir Path tempDir) {
        this.edgeConnectorForKVSService = spy(new EdgeConnectorForKVSService("us-east-1", videoUploadRequestHandler));
        when(streamManagerBuilder.build()).thenReturn(streamManager);
        doNothing().when(streamManager).createMessageStream(any());
        edgeConnectorForKVSService.setRegionName(MOCK_REGION_NAME);
        edgeConnectorForKVSService.setSiteWiseClient(siteWiseClient);
        edgeConnectorForKVSService.setSiteWiseManager(siteWiseManager);
        edgeConnectorForKVSService.setSecretsClient(secretsClient);
        edgeConnectorForKVSService.setKvsClient(kvsClient);
        edgeConnectorForKVSService.setAwsCredentialsProviderV1(awsCredentialsProvider);
        edgeConnectorForKVSService.setVideoRecordingRootPath(tempDir.toString());
        edgeConnectorForKVSService.setStreamManagerBuilder(streamManagerBuilder);
    }

    @AfterEach
    public void cleanUp() {
        if (edgeConnectorForKVSService != null && edgeConnectorForKVSService.getJobScheduler() != null) {
            edgeConnectorForKVSService.getJobScheduler().stopAllCameras();
            edgeConnectorForKVSService = null;
        }
    }

    @Test
    public void testInitConfiguration(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
                .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);
        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();

        //verify
        assertNotNull(edgeConnectorForKVSConfigurationList.get(0).getRtspStreamURL());
    }

    @Test
    public void testInitSecretsManager(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
                .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any())).thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();

        //verify
        assertEquals(MOCK_RTSP_STREAM_URL, edgeConnectorForKVSConfigurationList.get(0).getRtspStreamURL());
    }

    @Test
    public void testStreamManagers(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();

        //verify
        assertNotNull(edgeConnectorForKVSConfiguration.getStreamManager());
    }

    @Test
    public void testInitKinesisVideoStream(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
                .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any())).thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();

        //verify
        verify(kvsClient, times(1)).createStream(any());
    }

    @Test
    public void testInitScheduler(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .captureStartTime(CAPTURE_START_TIME)
                .captureDurationInMinutes(CAPTURE_DURATION_IN_MINUTES)
                .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
                .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any())).thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);

        //verify
        assertNotNull(edgeConnectorForKVSService.getJobScheduler());
    }

    @Test
    public void testInitVideoRecorders(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .captureStartTime(START_TIME_EXPR_ALWAYS)
                .videoRecordFolderPath(tempDir)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.startRecordingJob(any(), any()))
                    .then(invocationOnMock -> null);
            //then
            edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
            edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        }
    }

    @Test
    public void testInitVideoUploaders(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .liveStreamingStartTime(START_TIME_EXPR_ALWAYS)
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any())).thenReturn(edgeConnectorForKVSConfigurationList);
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        try (MockedStatic<LiveVideoStreamingController> mockLiveVideoStreamingController =
                     mockStatic(LiveVideoStreamingController.class)) {
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController
                    .startLiveVideoStreaming(any(), any())).thenAnswer((Answer<Void>) invocation -> null);
            //then
            edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
            edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
            //verify
            assertNotNull(edgeConnectorForKVSConfiguration.getVideoUploader());
        }
    }

    @Test
    public void test_SchedulerStartTaskCallback_LiveStreaming(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .liveStreamingStartTime("")
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
                MOCK_KINESIS_VIDEO_STREAM_NAME);

        //verify
        assertNotNull(edgeConnectorForKVSConfiguration.videoUploader);
        assertNotNull(edgeConnectorForKVSConfiguration.inputStream);
    }

    @Test
    public void test_SchedulerStartTaskCallback_LiveStreaming_ThrowException(@TempDir Path tempDir)
            throws IOException, InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenThrow(new RuntimeException());

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .liveStreamingStartTime(START_TIME_EXPR_ALWAYS)
                .videoRecordFolderPath(tempDir)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);

        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
                MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(2000);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_SchedulerStartTaskCallback_StartRecordingJob(@TempDir Path tempDir)
            throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .captureStartTime("")
                .liveStreamingStartTime("")
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then and verify
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE
                , MOCK_KINESIS_VIDEO_STREAM_NAME);

        //verify
        assertNotNull(edgeConnectorForKVSConfiguration.videoUploader);
        assertNotNull(edgeConnectorForKVSConfiguration.inputStream);
    }

    @Test
    public void test_SchedulerStartTaskCallback_StartRecordingJob_ThrowException(@TempDir Path tempDir)
            throws IOException, InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenThrow(new RuntimeException());

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .liveStreamingStartTime(START_TIME_EXPR_ALWAYS)
                .videoRecordFolderPath(tempDir)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);

        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE,
                MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(2000);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_SchedulerStopTaskCallback_Stop_LiveVideoStreaming(@TempDir Path tempDir)
            throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then and verify
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING
                , MOCK_KINESIS_VIDEO_STREAM_NAME);
    }

    @Test
    public void test_SchedulerStopTaskCallback_Stop_LiveVideoStreaming_ThrowException(@TempDir Path tempDir)
            throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenThrow(new RuntimeException());

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecordFolderPath(tempDir)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING
                , MOCK_KINESIS_VIDEO_STREAM_NAME);
        //verify
        Thread.sleep(2000);
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_SchedulerStopTaskCallback_Stop_LocalVideoCapture(@TempDir Path tempDir)
            throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecordFolderPath(tempDir)
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        try (MockedStatic<RecordingController> mockRecordingController =
                     mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.stopRecordingJob(any()))
                    .thenAnswer((Answer<Void>) invocation -> null);
            edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
            edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
            edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE
                    , MOCK_KINESIS_VIDEO_STREAM_NAME);
            //verify
            mockRecordingController.verify(
                    ()-> RecordingController.stopRecordingJob(any()),
                    times(1)
            );
        }
    }

    @Test
    public void test_cleanUp_singleCamera_happyCase(@TempDir Path tempDir) throws IOException, InterruptedException {
        // mock
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecordFolderPath(tempDir)
                .videoRecorder(videoRecorder)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // when
        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STOPPED);

        try (MockedStatic<LiveVideoStreamingController> mockLiveVideoStreamingController =
                     mockStatic(LiveVideoStreamingController.class)) {
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController.stopLiveVideoStreaming(any()))
                    .thenAnswer((Answer<Void>) invocation -> null);
            try (MockedStatic<RecordingController> mockRecordingController =
                         mockStatic(RecordingController.class)) {
                mockRecordingController.when(() -> RecordingController.deInitVideoRecorders(any()))
                        .thenAnswer((Answer<Void>) invocation -> null);
                //then
                edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
                edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
                edgeConnectorForKVSService.cleanUpEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
                //verify
                assertFalse(edgeConnectorForKVSConfiguration.getFatalStatus().get());
            }
        }
    }

    @Test
    public void test_cleanUp_allCameras_happyCase(@TempDir Path tempDir) throws IOException, InterruptedException {
        // mock
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecordFolderPath(tempDir)
                .videoRecorder(videoRecorder)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // when
        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
                .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STOPPED);

        try (MockedStatic<LiveVideoStreamingController> mockLiveVideoStreamingController =
                     mockStatic(LiveVideoStreamingController.class)) {
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController.stopLiveVideoStreaming(any()))
                    .thenAnswer((Answer<Void>) invocation -> null);
            try (MockedStatic<RecordingController> mockRecordingController =
                         mockStatic(RecordingController.class)) {
                mockRecordingController.when(() -> RecordingController.deInitVideoRecorders(any()))
                        .thenAnswer((Answer<Void>) invocation -> null);
                //then
                edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
                edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
                edgeConnectorForKVSService.cleanUpEdgeConnectorForKVSService(null);
                //verify
                assertFalse(edgeConnectorForKVSConfiguration.getFatalStatus().get());
            }
        }
    }
}
