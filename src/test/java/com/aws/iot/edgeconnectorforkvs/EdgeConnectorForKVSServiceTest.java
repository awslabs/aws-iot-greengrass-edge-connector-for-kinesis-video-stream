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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.KvsClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SecretsClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SiteWiseClient;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.SiteWiseManager;
import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.handler.VideoUploadRequestEvent;
import com.aws.iot.edgeconnectorforkvs.handler.VideoUploadRequestHandler;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploader;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.KvsStreamingException;
import com.google.gson.Gson;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private static final String START_TIME_ALWAYS = "* * * * *";
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
    private StreamManager.StreamManagerBuilder streamManagerBuilder;

    Gson gson = new Gson();

    @BeforeEach
    public void init(@TempDir Path tempDir) {
        when(streamManagerBuilder.build()).thenReturn(streamManager);
        doNothing().when(streamManager).createMessageStream(any());
        doNothing().when(videoUploadRequestHandler).subscribeToMqttTopic(any(), any());
        this.edgeConnectorForKVSService = EdgeConnectorForKVSService.builder()
            .regionName(MOCK_REGION_NAME)
            .siteWiseClient(siteWiseClient)
            .siteWiseManager(siteWiseManager)
            .secretsClient(secretsClient)
            .kvsClient(kvsClient)
            .awsCredentialsProviderV1(awsCredentialsProvider)
            .videoRecordingRootPath(tempDir.toString())
            .streamManagerBuilder(streamManagerBuilder)
            .videoUploadRequestHandler(videoUploadRequestHandler)
            .retryOnFail(false)
            .build();
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
        edgeConnectorForKVSConfigurationList.forEach(configuration ->
            edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(configuration));

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
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);

        //verify
        assertEquals(MOCK_RTSP_STREAM_URL, edgeConnectorForKVSConfigurationList.get(0).getRtspStreamURL());
    }

    @Test
    public void testInitVideoRecorders(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
            .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
            .captureStartTime(START_TIME_ALWAYS)
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
    }

    @Test
    public void testInitVideoRecorders_HasOneRecorderStated(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
            .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
            .captureStartTime(START_TIME_ALWAYS)
            .videoRecordFolderPath(tempDir)
            .recordingRequestsCount(2)
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

        //verify
        assertNull(edgeConnectorForKVSConfiguration.videoRecorder);
        assertNull(edgeConnectorForKVSConfiguration.outputStream);
    }

    @Test
    public void testInitVideoRecorders_startRecordingJob_ProcessLock_TimeOut(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenReturn(false);

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);

        //verify
        Thread.sleep(3000);
        assertEquals(0, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testInitVideoRecorders_startRecordingJob_InterruptedException(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(CAPTURE_START_TIME);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);

        //verify
        assertEquals(0, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testStopRecordingJob_JobStop(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        Pipeline mockedPipeline = Mockito.mock(Pipeline.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
            .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
            .captureStartTime(START_TIME_ALWAYS)
            .videoRecordFolderPath(tempDir)
            .videoRecorder(videoRecorder)
            .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));
        // Mock for videoRecorder status
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STOPPED);
        // Mock for videoRecorder pipeline
        when(videoRecorder.getPipeline()).thenReturn(mockedPipeline);

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(0, edgeConnectorForKVSConfigurationList.get(0).getRecordingRequestsCount());
    }

    @Test
    public void testStopRecordingJob_JobNotStop(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
            .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
            .videoRecordFolderPath(tempDir)
            .videoRecorder(videoRecorder)
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
        Thread.sleep(2000);
        edgeConnectorForKVSConfiguration.setRecordingRequestsCount(2);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(edgeConnectorForKVSConfigurationList.get(0).getRecordingRequestsCount(), 1);
    }

    @Test
    public void testStopRecordingJob_ProcessLock_TimeOut(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_NEVER);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenReturn(false);

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testStopRecordingJob_InterruptedException(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_NEVER);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testInitVideoUploaders(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn("");
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecorder()).thenReturn(videoRecorder);
        when(edgeConnectorForKVSConfiguration.getVideoUploader()).thenReturn(videoUploader);
        when(edgeConnectorForKVSConfiguration.getRecordingRequestsCount()).thenReturn(2);
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        doThrow(new RuntimeException()).when(videoUploader).uploadStream(any(), any(), any(), any());

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);

        //verify
        verify(videoUploader, atLeastOnce()).uploadStream(any(), any(), any(), any());
    }

    @Test
    public void testInitVideoUploaders_ThrowKvsStreamingException(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn("");
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecorder()).thenReturn(videoRecorder);
        when(edgeConnectorForKVSConfiguration.getVideoUploader()).thenReturn(videoUploader);
        when(edgeConnectorForKVSConfiguration.getRecordingRequestsCount()).thenReturn(2);
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        doThrow(new KvsStreamingException(""))
            .doThrow(new RuntimeException())
            .when(videoUploader).uploadStream(any(), any(), any(), any());

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        EdgeConnectorForKVSService service = EdgeConnectorForKVSService.builder()
            .regionName(MOCK_REGION_NAME)
            .siteWiseClient(siteWiseClient)
            .siteWiseManager(siteWiseManager)
            .secretsClient(secretsClient)
            .kvsClient(kvsClient)
            .awsCredentialsProviderV1(awsCredentialsProvider)
            .videoRecordingRootPath(tempDir.toString())
            .streamManagerBuilder(streamManagerBuilder)
            .videoUploadRequestHandler(videoUploadRequestHandler)
            .retryOnFail(false)
            .build();

        //then
        service.setUpSharedEdgeConnectorForKVSService();
        service.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);

        //verify
        verify(videoUploader, atLeast(1)).uploadStream(any(), any(), any(), any());
    }

    @Test
    public void testInitVideoUploaders_ThrowKvsStreamingException_ThenGetProcessLockTimeout(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn("");
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecorder()).thenReturn(videoRecorder);
        when(edgeConnectorForKVSConfiguration.getVideoUploader()).thenReturn(videoUploader);
        when(edgeConnectorForKVSConfiguration.getRecordingRequestsCount()).thenReturn(2);
        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenReturn(false);
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        doThrow(new KvsStreamingException(""))
            .doThrow(new RuntimeException())
            .when(videoUploader).uploadStream(any(), any(), any(), any());

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        EdgeConnectorForKVSService service = EdgeConnectorForKVSService.builder()
            .regionName(MOCK_REGION_NAME)
            .siteWiseClient(siteWiseClient)
            .siteWiseManager(siteWiseManager)
            .secretsClient(secretsClient)
            .kvsClient(kvsClient)
            .awsCredentialsProviderV1(awsCredentialsProvider)
            .videoRecordingRootPath(tempDir.toString())
            .streamManagerBuilder(streamManagerBuilder)
            .videoUploadRequestHandler(videoUploadRequestHandler)
            .retryOnFail(false)
            .build();

        //then
        service.setUpSharedEdgeConnectorForKVSService();
        service.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);

        //verify
        verify(videoUploader, atLeast(1)).uploadStream(any(), any(), any(), any());
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
        assertFalse(edgeConnectorForKVSConfiguration.getProcessLock().isLocked());
    }

    @Test
    public void testInitVideoUploaders_Configure_Not_Init(@TempDir Path tempDir) throws IOException {
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

        //verify
        assertNotNull(edgeConnectorForKVSConfiguration.videoUploader);
        assertNotNull(edgeConnectorForKVSConfiguration.inputStream);
        assertEquals(0, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
    }

    @Test
    public void testInitVideoUploaders_HasOneUploaderStarted(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .liveStreamingStartTime(START_TIME_ALWAYS)
            .videoRecordFolderPath(tempDir)
            .processLock(new ReentrantLock())
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

        //verify
        assertNotNull(edgeConnectorForKVSConfiguration.videoUploader);
        assertNotNull(edgeConnectorForKVSConfiguration.inputStream);
        assertEquals(1, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
    }

    @Test
    public void testInitVideoUploaders_ProcessLock_Timeout(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(CAPTURE_START_TIME);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenReturn(false);

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any())).
            thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);

        //verify
        assertEquals(0, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testInitVideoUploaders_Throws_InterruptedException(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(CAPTURE_START_TIME);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any())).
            thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);

        //verify
        assertEquals(0, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_StopLiveVideoStreaming_JobStop(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);

        PipedInputStream pipedInputStream = Mockito.mock(PipedInputStream.class);
        PipedOutputStream pipedOutputStream = Mockito.mock(PipedOutputStream.class);

        doNothing().when(videoUploader).close();
        doNothing().when(pipedOutputStream).flush();
        doNothing().when(pipedOutputStream).close();
        doNothing().when(pipedInputStream).close();
        doNothing().when(videoRecorder).stopRecording();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(START_TIME_ALWAYS);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecorder()).thenReturn(videoRecorder);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        when(edgeConnectorForKVSConfiguration.getVideoUploader()).thenReturn(videoUploader);
        when(edgeConnectorForKVSConfiguration.getRecordingRequestsCount()).thenReturn(1);
        doNothing().when(edgeConnectorForKVSConfiguration).setInputStream(any());
        when(edgeConnectorForKVSConfiguration.getInputStream()).thenReturn(pipedInputStream);
        doNothing().when(edgeConnectorForKVSConfiguration).setOutputStream(any());
        when(edgeConnectorForKVSConfiguration.getOutputStream()).thenReturn(pipedOutputStream);

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(0, edgeConnectorForKVSConfigurationList.get(0).getLiveStreamingRequestsCount());
    }

    @Test
    public void test_StopLiveVideoStreaming_ProcessLock_Timeout(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);

        PipedInputStream pipedInputStream = Mockito.mock(PipedInputStream.class);
        PipedOutputStream pipedOutputStream = Mockito.mock(PipedOutputStream.class);

        doNothing().when(videoUploader).close();
        doNothing().when(pipedOutputStream).flush();
        doNothing().when(pipedOutputStream).close();
        doNothing().when(pipedInputStream).close();
        doNothing().when(videoRecorder).stopRecording();

        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(START_TIME_ALWAYS);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecorder()).thenReturn(videoRecorder);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        when(edgeConnectorForKVSConfiguration.getVideoUploader()).thenReturn(videoUploader);
        when(edgeConnectorForKVSConfiguration.getRecordingRequestsCount()).thenReturn(1);
        doNothing().when(edgeConnectorForKVSConfiguration).setInputStream(any());
        when(edgeConnectorForKVSConfiguration.getInputStream()).thenReturn(pipedInputStream);
        doNothing().when(edgeConnectorForKVSConfiguration).setOutputStream(any());
        when(edgeConnectorForKVSConfiguration.getOutputStream()).thenReturn(pipedOutputStream);

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenReturn(false);

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_StopLiveVideoStreaming_InterruptedException(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);

        PipedInputStream pipedInputStream = Mockito.mock(PipedInputStream.class);
        PipedOutputStream pipedOutputStream = Mockito.mock(PipedOutputStream.class);

        doNothing().when(videoUploader).close();
        doNothing().when(pipedOutputStream).flush();
        doNothing().when(pipedOutputStream).close();
        doNothing().when(pipedInputStream).close();
        doNothing().when(videoRecorder).stopRecording();

        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(START_TIME_ALWAYS);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoRecorder(any());
        when(edgeConnectorForKVSConfiguration.getVideoRecorder()).thenReturn(videoRecorder);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        when(edgeConnectorForKVSConfiguration.getVideoUploader()).thenReturn(videoUploader);
        when(edgeConnectorForKVSConfiguration.getRecordingRequestsCount()).thenReturn(1);
        doNothing().when(edgeConnectorForKVSConfiguration).setInputStream(any());
        when(edgeConnectorForKVSConfiguration.getInputStream()).thenReturn(pipedInputStream);
        doNothing().when(edgeConnectorForKVSConfiguration).setOutputStream(any());
        when(edgeConnectorForKVSConfiguration.getOutputStream()).thenReturn(pipedOutputStream);

        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());

        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(2000);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(true, edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_StopLiveVideoStreaming_JobNotStop(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .liveStreamingStartTime(LIVE_STREAMING_START_TIME)
            .liveStreamingDurationInMinutes(LIVE_STREAMING_DURATION_IN_MINUTES)
            .captureStartTime(START_TIME_ALWAYS)
            .videoRecordFolderPath(tempDir)
            .videoRecorder(videoRecorder)
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
        Thread.sleep(2000);
        edgeConnectorForKVSConfiguration.setLiveStreamingRequestsCount(2);
        edgeConnectorForKVSService.schedulerStopTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertEquals(1, edgeConnectorForKVSConfigurationList.get(0).getLiveStreamingRequestsCount());
    }

    @Test
    public void test_InitScheduler(@TempDir Path tempDir) throws IOException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(START_TIME_ALWAYS);
        when(edgeConnectorForKVSConfiguration.getCaptureStartTime()).thenReturn(CAPTURE_START_TIME);
        when(edgeConnectorForKVSConfiguration.getLiveStreamingStartTime()).thenReturn(LIVE_STREAMING_START_TIME);
        when(edgeConnectorForKVSConfiguration.getVideoRecordFolderPath()).thenReturn(tempDir);
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        doNothing().when(edgeConnectorForKVSConfiguration).setVideoUploader(any());
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any())).
            thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
    }

    @Test
    public void test_SchedulerStartTaskCallback_LiveStreaming(@TempDir Path tempDir) throws IOException,
        InterruptedException {
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
        Thread.sleep(2000);
        edgeConnectorForKVSConfiguration.setLiveStreamingRequestsCount(2);
        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertNotNull(edgeConnectorForKVSConfiguration.videoUploader);
        assertNotNull(edgeConnectorForKVSConfiguration.inputStream);
        assertEquals(3, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
    }

    @Test
    public void test_SchedulerStartTaskCallback_StartRecordingJob(@TempDir Path tempDir)
        throws IOException, InterruptedException {
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

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(3000);
        edgeConnectorForKVSConfiguration.setRecordingRequestsCount(2);
        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertNotNull(edgeConnectorForKVSConfiguration.videoUploader);
        assertNotNull(edgeConnectorForKVSConfiguration.inputStream);
        assertEquals(3, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
    }

    @Test
    public void test_SchedulerStartTaskCallback_StartRecordingJob_ThrowException(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        // mock
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        setUpMockConfiguration(edgeConnectorForKVSConfiguration);

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(3000);
        edgeConnectorForKVSConfiguration.setRecordingRequestsCount(2);
        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LOCAL_VIDEO_CAPTURE,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_SchedulerStartTaskCallback_StartLiveStreamingJob_ThrowException(@TempDir Path tempDir)
        throws IOException, InterruptedException {
        // mock
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
            spy(EdgeConnectorForKVSConfiguration.class);
        setUpMockConfiguration(edgeConnectorForKVSConfiguration);

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(3000);
        edgeConnectorForKVSConfiguration.setRecordingRequestsCount(2);
        edgeConnectorForKVSService.schedulerStartTaskCallback(Constants.JobType.LIVE_VIDEO_STREAMING,
            MOCK_KINESIS_VIDEO_STREAM_NAME);
        Thread.sleep(1000);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void test_initMQTTSubscription_onStartLive(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .captureStartTime(START_TIME_NEVER)
            .liveStreamingStartTime(START_TIME_NEVER)
            .videoUploadRequestMqttTopic(VIDEO_UPLOAD_REQUEST_MQTT_TOPIC)
            .videoRecordFolderPath(tempDir)
            .recordingRequestsCount(0)
            .liveStreamingRequestsCount(0)
            .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        doAnswer(invocationOnMock -> {
            VideoUploadRequestEvent event = invocationOnMock.getArgument(1);
            event.onStart(IS_LIVE_TRUE, EVENT_TIMESTAMP, START_TIME, END_TIME);
            return null;
        }).when(videoUploadRequestHandler).subscribeToMqttTopic(any(), any());

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(3000);


        assertEquals(1, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(1, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
    }

    @Test
    public void test_initMQTTSubscription_onStartHistorical(@TempDir Path tempDir) throws IOException,
        InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .captureStartTime(START_TIME_NEVER)
            .liveStreamingStartTime(START_TIME_NEVER)
            .videoUploadRequestMqttTopic(VIDEO_UPLOAD_REQUEST_MQTT_TOPIC)
            .videoRecordFolderPath(tempDir)
            .recordingRequestsCount(0)
            .liveStreamingRequestsCount(0)
            .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        doAnswer(invocationOnMock -> {
            VideoUploadRequestEvent event = invocationOnMock.getArgument(1);
            event.onStart(IS_LIVE_FALSE, EVENT_TIMESTAMP, START_TIME, END_TIME);
            return null;
        }).when(videoUploadRequestHandler).subscribeToMqttTopic(any(), any());

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(3000);

        assertEquals(0, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(0, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
    }

    @Test
    public void test_initMQTTSubscription_onError(@TempDir Path tempDir) throws IOException, InterruptedException {
        //when
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .captureStartTime(START_TIME_NEVER)
            .liveStreamingStartTime(START_TIME_NEVER)
            .videoUploadRequestMqttTopic(VIDEO_UPLOAD_REQUEST_MQTT_TOPIC)
            .videoRecordFolderPath(tempDir)
            .recordingRequestsCount(0)
            .liveStreamingRequestsCount(0)
            .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        doAnswer(invocationOnMock -> {
            VideoUploadRequestEvent event = invocationOnMock.getArgument(1);
            event.onError("Test Error");
            return null;
        }).when(videoUploadRequestHandler).subscribeToMqttTopic(any(), any());

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(3000);

        assertEquals(0, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(0, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
    }

    @Test
    public void test_cleanUp_singleCamera_happyCase(@TempDir Path tempDir) throws IOException, InterruptedException {
        // mock
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);
        PipedOutputStream pipedOutputStream = Mockito.mock(PipedOutputStream.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .captureStartTime(START_TIME_NEVER)
            .liveStreamingStartTime(START_TIME_NEVER)
            .videoUploadRequestMqttTopic(VIDEO_UPLOAD_REQUEST_MQTT_TOPIC)
            .videoRecordFolderPath(tempDir)
            .recordingRequestsCount(0)
            .liveStreamingRequestsCount(0)
            .videoRecorder(videoRecorder)
            .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);

        // when
        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        doNothing().when(videoUploader).close();
        doNothing().when(pipedOutputStream).flush();
        doNothing().when(pipedOutputStream).close();
        doNothing().when(videoRecorder).stopRecording();
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STOPPED);

        doAnswer(invocationOnMock -> {
            VideoUploadRequestEvent event = invocationOnMock.getArgument(1);
            event.onStart(IS_LIVE_TRUE, EVENT_TIMESTAMP, START_TIME, END_TIME);
            return null;
        }).when(videoUploadRequestHandler).subscribeToMqttTopic(any(), any());

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        Thread.sleep(3000);

        assertEquals(1, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(1, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());

        edgeConnectorForKVSService.cleanUpEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);

        // verify
        assertEquals(0, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(0, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
    }

    @Test
    public void test_cleanUp_allCameras_happyCase(@TempDir Path tempDir) throws IOException, InterruptedException {
        // mock
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        VideoRecorder videoRecorder = Mockito.mock(VideoRecorder.class);
        VideoUploader videoUploader = Mockito.mock(VideoUploader.class);
        PipedOutputStream pipedOutputStream = Mockito.mock(PipedOutputStream.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
            .captureStartTime(START_TIME_NEVER)
            .liveStreamingStartTime(START_TIME_NEVER)
            .videoUploadRequestMqttTopic(VIDEO_UPLOAD_REQUEST_MQTT_TOPIC)
            .videoRecordFolderPath(tempDir)
            .recordingRequestsCount(0)
            .liveStreamingRequestsCount(0)
            .videoRecorder(videoRecorder)
            .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);
        // Add another one
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration2 = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME + "RESTART_TEST")
            .captureStartTime(START_TIME_NEVER)
            .liveStreamingStartTime(START_TIME_NEVER)
            .videoUploadRequestMqttTopic(VIDEO_UPLOAD_REQUEST_MQTT_TOPIC)
            .videoRecordFolderPath(tempDir)
            .recordingRequestsCount(0)
            .liveStreamingRequestsCount(0)
            .videoRecorder(videoRecorder)
            .build();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration2);

        // when
        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));

        doNothing().when(videoUploader).close();
        doNothing().when(pipedOutputStream).flush();
        doNothing().when(pipedOutputStream).close();
        doNothing().when(videoRecorder).stopRecording();
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STOPPED);

        doAnswer(invocationOnMock -> {
            VideoUploadRequestEvent event = invocationOnMock.getArgument(1);
            event.onStart(IS_LIVE_TRUE, EVENT_TIMESTAMP, START_TIME, END_TIME);
            return null;
        }).when(videoUploadRequestHandler).subscribeToMqttTopic(any(), any());

        //then
        edgeConnectorForKVSService.setUpSharedEdgeConnectorForKVSService();
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration);
        edgeConnectorForKVSService.setUpCameraLevelEdgeConnectorForKVSService(edgeConnectorForKVSConfiguration2);
        Thread.sleep(3000);

        assertEquals(1, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(1, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
        assertEquals(1, edgeConnectorForKVSConfiguration2.getLiveStreamingRequestsCount());
        assertEquals(1, edgeConnectorForKVSConfiguration2.getRecordingRequestsCount());

        // This will clean up for all cameras
        edgeConnectorForKVSService.cleanUpEdgeConnectorForKVSService(null);

        // verify
        assertEquals(0, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
        assertEquals(0, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
        assertEquals(0, edgeConnectorForKVSConfiguration2.getLiveStreamingRequestsCount());
        assertEquals(0, edgeConnectorForKVSConfiguration2.getRecordingRequestsCount());
    }

    private void setUpMockConfiguration(EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration) throws InterruptedException {
        // mock
        List<EdgeConnectorForKVSConfiguration> edgeConnectorForKVSConfigurationList = new ArrayList();
        edgeConnectorForKVSConfigurationList.add(edgeConnectorForKVSConfiguration);
        ReentrantLock processLock = Mockito.mock(ReentrantLock.class);

        // when
        doNothing().when(edgeConnectorForKVSConfiguration).setProcessLock(any());
        when(edgeConnectorForKVSConfiguration.getProcessLock()).thenReturn(processLock);
        when(processLock.tryLock(anyLong(), any())).thenThrow(new RuntimeException());
        // Mock for initConfiguration
        when(siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(any()))
            .thenReturn(edgeConnectorForKVSConfigurationList);
        // Mock for initSecretsManager
        when(secretsClient.getSecretValue(any())).thenReturn(gson.toJson(secretMap));
        when(edgeConnectorForKVSConfiguration.getKinesisVideoStreamName()).thenReturn(MOCK_KINESIS_VIDEO_STREAM_NAME);
    }
}
