/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.aws.iot.edgeconnectorforkvs.videouploader;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMedia;
import com.amazonaws.services.kinesisvideo.PutMediaAckResponseHandler;
import com.amazonaws.services.kinesisvideo.model.AckEvent;
import com.amazonaws.services.kinesisvideo.model.AckEventType;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointResult;
import com.amazonaws.services.kinesisvideo.model.PutMediaRequest;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.UploadCallBack;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvInputStream;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.KvsStreamingException;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.VideoUploaderException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Date;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class VideoUploaderClientTest {

    private static final Long TEST_TIME = 1600000000000L;
    private static final Long STATUS_CHANGED_TIME = 80L;
    private static final String RECORD_FILE_PATH = "testRecordFilePath";
    private static final String KVS_STREAM_NAME = "testKvsStreamName";
    private static final String DATA_ENDPOINT = "testDataEndpoint";
    static private Instant instantNow;
    static private Path tempDir;
    static private Path tempVideoNowMinus700s;
    static private Path tempVideoNowMinus500s;
    static private Path tempVideoNowMinus300s;
    static private Path tempVideoNowMinus100s;
    private VideoUploaderClient videoUploaderClient;

    private Region region = Region.getRegion(Regions.US_WEST_2);

    @Mock
    private AWSCredentialsProvider mockAwsCredentialsProvider;
    @Mock
    private AmazonKinesisVideo mockKvsFrontendClient;
    @Mock
    private AmazonKinesisVideoPutMedia mockKvsDataClient;
    @Mock
    private StreamManager streamManager;

    private InputStream inputStream;

    @Mock
    private EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration;

    @BeforeAll
    static void setupForAll() {
        instantNow = Instant.ofEpochMilli(TEST_TIME);
        try {
            tempDir = Files.createTempDirectory("temp");

            // create 4 temp video files with timestamp now-700s, -500s, -300s, -100s
            tempVideoNowMinus700s = Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(700)).getTime() + ".mkv");
            tempVideoNowMinus500s = Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(500)).getTime() + ".mkv");
            tempVideoNowMinus300s = Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(300)).getTime() + ".mkv");
            tempVideoNowMinus100s = Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(100)).getTime() + ".mkv");
            Files.copy(ClassLoader.getSystemResourceAsStream("sample-mkv-file.mkv"), tempVideoNowMinus700s);
            Files.copy(ClassLoader.getSystemResourceAsStream("sample-mkv-file.mkv"), tempVideoNowMinus500s);
            Files.copy(ClassLoader.getSystemResourceAsStream("sample-mkv-file.mkv"), tempVideoNowMinus300s);
            Files.copy(ClassLoader.getSystemResourceAsStream("sample-mkv-file.mkv"), tempVideoNowMinus100s);


            // Clean up these temp files when exit.
            Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 @SuppressWarnings("unused") BasicFileAttributes attrs) {
                    file.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                                                         @SuppressWarnings("unused") BasicFileAttributes attrs) {
                    dir.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            System.out.println("Unable to create temp directory or temp video files!");
        }
    }

    public boolean tempVideoFilesPresent() {
        return tempDir != null && tempVideoNowMinus700s != null && tempVideoNowMinus500s != null && tempVideoNowMinus300s != null && tempVideoNowMinus100s != null;
    }

    @BeforeEach
    public void setupForEach() {
        videoUploaderClient = VideoUploaderClient.builder()
                .awsCredentialsProvider(mockAwsCredentialsProvider)
                .region(region)
                .recordFilePath(tempDir.toString())
                .kvsStreamName(KVS_STREAM_NAME)
                .build();

        byte[] sampleVideo = TestUtil.createSampleVideo(false);
        Assumptions.assumeTrue(sampleVideo != null);
        inputStream = new MkvInputStream(new ByteArrayInputStream(sampleVideo));
    }

    private boolean setPrivateMember(VideoUploaderClient instance, String fieldName, Object value) {
        boolean result = false;
        try {
            Field field = VideoUploaderClient.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
            result = true;
        } catch (NoSuchFieldException exception) {
            System.out.println("Failed to mock " + fieldName + ", NoSuchFieldException");
        } catch (IllegalAccessException exception) {
            System.out.println("Failed to mock " + fieldName + ", IllegalAccessException");
        }
        return result;
    }

    @Test
    public void constructor_nullInputs_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.builder()
                        .awsCredentialsProvider(null)
                        .region(region)
                        .recordFilePath(RECORD_FILE_PATH)
                        .kvsStreamName(KVS_STREAM_NAME)
                        .build());
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.builder()
                        .awsCredentialsProvider(mockAwsCredentialsProvider)
                        .region(null)
                        .recordFilePath(RECORD_FILE_PATH)
                        .kvsStreamName(KVS_STREAM_NAME)
                        .build());
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.builder()
                        .awsCredentialsProvider(mockAwsCredentialsProvider)
                        .region(region)
                        .recordFilePath(null)
                        .kvsStreamName(KVS_STREAM_NAME)
                        .build());
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.builder()
                        .awsCredentialsProvider(mockAwsCredentialsProvider)
                        .region(region)
                        .recordFilePath(RECORD_FILE_PATH)
                        .kvsStreamName(null)
                        .build());
    }

    @Test
    public void constructor_validInputs_doNotThrow() {
        Assertions.assertDoesNotThrow(
                () -> VideoUploaderClient.builder()
                        .awsCredentialsProvider(new DefaultAWSCredentialsProviderChain())
                        .region(Region.getRegion(Regions.US_EAST_1))
                        .recordFilePath(RECORD_FILE_PATH)
                        .kvsStreamName(KVS_STREAM_NAME)
                        .build());
    }

    @Test
    void constructor_invalidInputsFromFactory_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.create(null, region, RECORD_FILE_PATH, KVS_STREAM_NAME));
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.create(mockAwsCredentialsProvider, null, RECORD_FILE_PATH, KVS_STREAM_NAME));
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.create(mockAwsCredentialsProvider, region, null, KVS_STREAM_NAME));
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoUploaderClient.create(mockAwsCredentialsProvider, region, RECORD_FILE_PATH, null));
    }

    @Test
    public void constructor_toString_notNull() {
        Assertions.assertNotNull(VideoUploaderClient.builder().toString());
    }

    @Test
    public void getDataEndpoint_validInputs_returnEndpoint() {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));

        Assertions.assertEquals(DATA_ENDPOINT, videoUploaderClient.getDataEndpoint());
    }

    @Test
    public void uploadStream_invalidInputs_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> videoUploaderClient.uploadStream(null, null, null, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> videoUploaderClient.uploadStream(inputStream, null, null, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> videoUploaderClient.uploadStream(null, Date.from(Instant.now()), null, null));
    }

    @Test
    public void uploadStream_validInputs_runCallbacks() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        final boolean[] isStatusChanged = {false};
        final Runnable statusChangedCallBack = () -> isStatusChanged[0] = true;

        final UploadCallBack uploadCallBack = new UploadCallBack(Date.from(Instant.now()), edgeConnectorForKVSConfiguration);

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        // Since we make putMedia do nothing, so it won't end until we close it.
        new Thread(() -> {
            videoUploaderClient.uploadStream(inputStream, Date.from(Instant.now()), statusChangedCallBack,
                    uploadCallBack);
        }).start();

        // wait until task start
        if (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        videoUploaderClient.close();

        // wait until task end
        if (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(isStatusChanged[0]);
        verify(streamManager,times(0)).pushData(any(), any(), any(), any());
    }

    @Test
    public void uploadStream_validInputsWithNullCallbacks_NoExceptionIsThrown() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        // Since we make putMedia do nothing, so it won't end until we close it.
        new Thread(() -> {
            Assertions.assertDoesNotThrow(() ->
                    videoUploaderClient.uploadStream(inputStream, Date.from(Instant.now()), null, null));
        }).start();

        // wait until task start
        if (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        videoUploaderClient.close();

        // wait until task end
        if (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }
    }

    @Test
    public void uploadStream_mockAckResponseFailure_runCallbacks() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        final boolean[] isStatusChanged = {false};
        final Runnable statusChangedCallBack = () -> isStatusChanged[0] = true;

        final UploadCallBack uploadCallBack = new UploadCallBack(Date.from(Instant.now()), edgeConnectorForKVSConfiguration);

        ArgumentCaptor<PutMediaAckResponseHandler> putMediaAckResponseArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaAckResponseHandler.class);
        ArgumentCaptor<PutMediaRequest> putMediaRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaRequest.class);

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        // Since we make putMedia do nothing, so it won't end until we close it.
        new Thread(() -> {
            Assertions.assertThrows(KvsStreamingException.class, () ->
                    videoUploaderClient.uploadStream(inputStream, Date.from(Instant.now()), statusChangedCallBack,
                            uploadCallBack));
        }).start();

        // wait until task start
        while (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        verify(mockKvsDataClient).putMedia(putMediaRequestArgumentCaptor.capture(), putMediaAckResponseArgumentCaptor.capture());

        AckEvent event = new AckEvent()
                .withAckEventType(AckEventType.Values.ERROR);
        putMediaAckResponseArgumentCaptor.getValue().onAckEvent(event);
        putMediaAckResponseArgumentCaptor.getValue().onFailure(new RuntimeException("Mock failure"));

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(isStatusChanged[0]);
        verify(streamManager,times(0)).pushData(any(), any(), any(), any());
    }

    @Test
    public void uploadStream_mockAckResponseComplete_runCallbacks() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        final boolean[] isStatusChanged = {false};
        final Runnable statusChangedCallBack = () -> isStatusChanged[0] = true;

        final UploadCallBack uploadCallBack = new UploadCallBack(Date.from(Instant.now()), edgeConnectorForKVSConfiguration);

        ArgumentCaptor<PutMediaAckResponseHandler> putMediaAckResponseArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaAckResponseHandler.class);
        ArgumentCaptor<PutMediaRequest> putMediaRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaRequest.class);

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));
        when(this.edgeConnectorForKVSConfiguration.getStreamManager()).thenReturn(streamManager);
        when(streamManager.pushData(any(), any(), any(), any())).thenReturn(0L);

        // Since we make putMedia do nothing, so it won't end until we close it.
        new Thread(() -> {
            videoUploaderClient.uploadStream(inputStream, Date.from(Instant.now()), statusChangedCallBack,
                    uploadCallBack);
        }).start();

        // wait until task start
        while (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        verify(mockKvsDataClient).putMedia(putMediaRequestArgumentCaptor.capture(), putMediaAckResponseArgumentCaptor.capture());

        AckEvent event = new AckEvent()
                .withAckEventType(AckEventType.Values.PERSISTED);
        event.setFragmentTimecode(1L);
        putMediaAckResponseArgumentCaptor.getValue().onAckEvent(event);
        putMediaAckResponseArgumentCaptor.getValue().onComplete();

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(isStatusChanged[0]);
        verify(streamManager,times(1)).pushData(any(), any(), any(), any());
    }

    @Test
    public void uploadStream_mockAckResponseCompleteWithNullCallback_taskClosed() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        ArgumentCaptor<PutMediaAckResponseHandler> putMediaAckResponseArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaAckResponseHandler.class);
        ArgumentCaptor<PutMediaRequest> putMediaRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaRequest.class);

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));

        // Since we make putMedia do nothing, so it won't end until we close it.
        Date dateNow = Date.from(Instant.now());
        new Thread(() -> {
            videoUploaderClient.uploadStream(inputStream, dateNow, null,
                    null);
        }).start();

        // wait until task start
        while (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        verify(mockKvsDataClient).putMedia(putMediaRequestArgumentCaptor.capture(), putMediaAckResponseArgumentCaptor.capture());

        AckEvent event = new AckEvent()
                .withAckEventType(AckEventType.Values.PERSISTED)
                .withFragmentTimecode(dateNow.getTime());
        putMediaAckResponseArgumentCaptor.getValue().onAckEvent(event);
        putMediaAckResponseArgumentCaptor.getValue().onComplete();

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(videoUploaderClient.isOpen());
    }

    @Test
    public void uploadStream_mockAckResponseCompleteWithUploadCallback_taskClosed() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        ArgumentCaptor<PutMediaAckResponseHandler> putMediaAckResponseArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaAckResponseHandler.class);
        ArgumentCaptor<PutMediaRequest> putMediaRequestArgumentCaptor =
                ArgumentCaptor.forClass(PutMediaRequest.class);

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        // Since we make putMedia do nothing, so it won't end until we close it.
        Date dateNow = Date.from(Instant.now());
        final UploadCallBack uploadCallBack = new UploadCallBack(dateNow, edgeConnectorForKVSConfiguration);

        new Thread(() -> {
            videoUploaderClient.uploadStream(inputStream, dateNow, null, uploadCallBack);
        }).start();

        // wait until task start
        while (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        verify(mockKvsDataClient).putMedia(putMediaRequestArgumentCaptor.capture(), putMediaAckResponseArgumentCaptor.capture());

        AckEvent event = new AckEvent()
                .withAckEventType(AckEventType.Values.PERSISTED)
                .withFragmentTimecode(dateNow.getTime());
        putMediaAckResponseArgumentCaptor.getValue().onAckEvent(event);
        putMediaAckResponseArgumentCaptor.getValue().onComplete();

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(videoUploaderClient.isOpen());
    }

    @Test
    public void uploadStream_mockInputs_throwException() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));

        Assumptions.assumeTrue(tempVideoFilesPresent());

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));

        Assertions.assertThrows(SdkClientException.class,
                () -> videoUploaderClient.uploadStream(inputStream, Date.from(Instant.now()), null, null));
    }

    @Test
    public void uploadStream_secondTask_throwVideoUploaderException() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        new Thread(() -> {
            videoUploaderClient.uploadStream(inputStream, Date.from(Instant.now()), null, null);
        }).start();

        if (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertThrows(VideoUploaderException.class,
                () -> videoUploaderClient.uploadHistoricalVideo(Date.from(instantNow.minusSeconds(600)),
                        Date.from(instantNow.minusSeconds(200)), null, null));
    }

    @Test
    public void uploadHistoricalVideo_invalidTimePeriod_throwException() throws InterruptedException {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> videoUploaderClient.uploadHistoricalVideo(Date.from(instantNow.minusSeconds(100)),
                        Date.from(instantNow.minusSeconds(200)), null, null));
    }

    @Test
    public void uploadHistoricalVideo_nullTimeInput_throwException() throws InterruptedException {
        Assertions.assertThrows(NullPointerException.class,
                () -> videoUploaderClient.uploadHistoricalVideo(null,
                        Date.from(instantNow.minusSeconds(200)), null, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> videoUploaderClient.uploadHistoricalVideo(Date.from(instantNow.minusSeconds(100)),
                        null, null, null));
    }

    @Test
    public void uploadHistoricalVideo_filesInTimePeriod_runCallbacks() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        Assumptions.assumeTrue(tempVideoFilesPresent());

        final boolean[] isStatusChanged = {false};
        final Runnable statusChangedCallBack = () -> isStatusChanged[0] = true;

        UploadCallBack uploadCallBack = new UploadCallBack(Date.from(instantNow.minusSeconds(600)),
                edgeConnectorForKVSConfiguration);

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        // Since we make putMedia do nothing, so it won't end until we close it.
        new Thread(() -> {
            videoUploaderClient.uploadHistoricalVideo(Date.from(instantNow.minusSeconds(600)),
                    Date.from(instantNow.minusSeconds(200)),
                    statusChangedCallBack, uploadCallBack);
        }).start();

        // wait until task start
        if (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        videoUploaderClient.close();

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(isStatusChanged[0]);
        Assertions.assertNull(uploadCallBack.getUploadingFile());
    }

    @Test
    public void uploadHistoricalVideo_filesInTimePeriodWithEndpointAlreadySet_runCallbacks()
            throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "dataEndpoint", DATA_ENDPOINT));

        Assumptions.assumeTrue(tempVideoFilesPresent());

        final boolean[] isStatusChanged = {false};
        final Runnable statusChangedCallBack = () -> isStatusChanged[0] = true;

        final UploadCallBack uploadCallBack = new UploadCallBack(Date.from(instantNow.minusSeconds(600)),
                edgeConnectorForKVSConfiguration);

        new Thread(() -> {
            videoUploaderClient.uploadHistoricalVideo(Date.from(instantNow.minusSeconds(600)),
                    Date.from(instantNow.minusSeconds(599)),
                    statusChangedCallBack, uploadCallBack);
        }).start();

        // wait until task start
        if (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        videoUploaderClient.close();

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(isStatusChanged[0]);
        verify(streamManager,times(0)).pushData(any(), any(), any(), any());
    }

    @Test
    public void uploadHistoricalVideo_uploadAndTaskIsTerminating_runCallbacks()
            throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "isTaskTerminating", true));

        Assumptions.assumeTrue(tempVideoFilesPresent());

        final boolean[] isStatusChanged = {false};
        final Runnable statusChangedCallBack = () -> isStatusChanged[0] = true;

        final UploadCallBack uploadCallBack = new UploadCallBack(Date.from(Instant.now()), edgeConnectorForKVSConfiguration);

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        // Since we make putMedia do nothing, so it won't end until we close it.
        new Thread(() -> {
            videoUploaderClient.uploadHistoricalVideo(Date.from(instantNow.minusSeconds(600)),
                    Date.from(instantNow.minusSeconds(200)),
                    statusChangedCallBack, uploadCallBack);
        }).start();

        // wait until task start
        if (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        videoUploaderClient.close();

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(isStatusChanged[0]);
        verify(streamManager,times(0)).pushData(any(), any(), any(), any());
    }

    @Test
    public void uploadHistoricalVideo_filesInTimePeriodWithNullCallbacks_taskClosed() throws InterruptedException {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsFrontendClient", mockKvsFrontendClient));
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "kvsDataClient", mockKvsDataClient));

        Assumptions.assumeTrue(tempVideoFilesPresent());

        when(mockKvsFrontendClient.getDataEndpoint(any(GetDataEndpointRequest.class))).thenReturn(new GetDataEndpointResult().withDataEndpoint(DATA_ENDPOINT));
        doNothing().when(mockKvsDataClient).putMedia(any(PutMediaRequest.class), any(PutMediaAckResponseHandler.class));

        // Since we make putMedia do nothing, so it won't end until we close it.
        new Thread(() -> {
            videoUploaderClient.uploadHistoricalVideo(Date.from(instantNow.minusSeconds(600)),
                    Date.from(instantNow.minusSeconds(200)),
                    null, null);
        }).start();

        // wait until task start
        if (!videoUploaderClient.isOpen()) {
            System.out.println("task is not running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        videoUploaderClient.close();

        // wait until task end
        while (videoUploaderClient.isOpen()) {
            System.out.println("task is running");
            Thread.sleep(STATUS_CHANGED_TIME);
        }

        Assertions.assertFalse(videoUploaderClient.isOpen());
    }

    @Test
    public void close_noTaskOngoing_noException() {
        Assertions.assertDoesNotThrow(() -> videoUploaderClient.close());
    }

    @Test
    public void close_taskOnGoingWithNoLatch_noException() {
        Assumptions.assumeTrue(setPrivateMember(videoUploaderClient, "isTaskOnGoing", true));
        Assertions.assertDoesNotThrow(() -> videoUploaderClient.close());
    }
}
