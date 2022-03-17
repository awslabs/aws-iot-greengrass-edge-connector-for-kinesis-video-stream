package com.aws.iot.edgeconnectorforkvs.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.util.StreamUtils;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploader;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.KvsStreamingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@ExtendWith(MockitoExtension.class)
public class LiveVideoStreamingControllerTest {

    private static final String MOCK_KINESIS_VIDEO_STREAM_NAME = "kinesisVideoStreamName";

    @Mock
    VideoRecorder videoRecorder;

    @Mock
    VideoUploader videoUploader;

    @Test
    public void testStartLiveVideoStreaming_Without_OtherLiveStreaming_Enabled() throws InterruptedException, IOException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .liveStreamingRequestsCount(0)
                .processLock(new ReentrantLock())
                .fatalStatus(new AtomicBoolean(false))
                .build();

        try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.startRecordingJob(any(), any()))
                    .thenAnswer((Answer<Void>) invocation -> null);

            when(videoRecorder.toggleAppDataOutputStream(anyBoolean())).thenReturn(true);
            when(videoRecorder.setAppDataOutputStream(any())).thenReturn(true);
            doNothing().when(videoUploader).uploadStream(any(), any(), any(), any());
            LiveVideoStreamingController.setRetryOnFail(false);
            //then
            LiveVideoStreamingController.startLiveVideoStreaming(edgeConnectorForKVSConfiguration, recorderService);

            //verify
            assertEquals(edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount(), 1);
            verify(videoUploader, times(1)).uploadStream(any(), any(), any(), any());
        }
    }

    @Test
    public void testStartLiveVideoStreaming_With_Recorder_Enabled() throws InterruptedException, IOException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .liveStreamingRequestsCount(1)
                .processLock(new ReentrantLock())
                .fatalStatus(new AtomicBoolean(false))
                .build();

        try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.startRecordingJob(any(), any()))
                    .thenAnswer((Answer<Void>) invocation -> null);

            LiveVideoStreamingController.setRetryOnFail(false);
            //then
            LiveVideoStreamingController.startLiveVideoStreaming(edgeConnectorForKVSConfiguration, recorderService);

            //verify
            assertEquals(2, edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount());
            verify(videoUploader, times(0)).uploadStream(any(), any(), any(), any());
        }
    }

    @Test
    public void testStartLiveVideoStreaming_GetProcessLockTimeout()
            throws IOException, InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ReentrantLock mockLock = mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .liveStreamingRequestsCount(0)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        when(mockLock.tryLock(anyLong(), any())).thenReturn(false);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.startRecordingJob(any(), any()))
                    .thenAnswer((Answer<Void>) invocation -> null);

            when(videoRecorder.toggleAppDataOutputStream(anyBoolean())).thenReturn(true);
            when(videoRecorder.setAppDataOutputStream(any())).thenReturn(true);
            doNothing().when(videoUploader).uploadStream(any(), any(), any(), any());
            LiveVideoStreamingController.setRetryOnFail(false);
            //then
            LiveVideoStreamingController.startLiveVideoStreaming(edgeConnectorForKVSConfiguration, recorderService);

            //verify
            verify(mockLock, atLeast(1)).unlock();
        }
    }

    @Test
    public void testStartLiveVideoStreaming_GetProcessLock_Exception()
            throws IOException, InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ReentrantLock mockLock = mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .liveStreamingRequestsCount(0)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        when(mockLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.startRecordingJob(any(), any()))
                    .thenAnswer((Answer<Void>) invocation -> null);

            when(videoRecorder.toggleAppDataOutputStream(anyBoolean())).thenReturn(true);
            when(videoRecorder.setAppDataOutputStream(any())).thenReturn(true);
            doNothing().when(videoUploader).uploadStream(any(), any(), any(), any());
            LiveVideoStreamingController.setRetryOnFail(false);
            //then
            LiveVideoStreamingController.startLiveVideoStreaming(edgeConnectorForKVSConfiguration, recorderService);

            //verify
            assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
            verify(mockLock, atLeast(1)).unlock();
        }
    }

    @Test
    public void testStartLiveVideoStreaming_Throws_Uploading_Exceptions() throws InterruptedException, IOException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ReentrantLock mockLock = mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .liveStreamingRequestsCount(0)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.startRecordingJob(any(), any()))
                    .thenAnswer((Answer<Void>) invocation -> null);

            when(videoRecorder.toggleAppDataOutputStream(anyBoolean())).thenReturn(true);
            when(videoRecorder.setAppDataOutputStream(any())).thenReturn(true);
            doThrow(new KvsStreamingException("")).when(videoUploader).uploadStream(any(), any(), any(), any());
            LiveVideoStreamingController.setRetryOnFail(false);

            when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
            when(mockLock.isHeldByCurrentThread()).thenReturn(true);
            doNothing().when(mockLock).unlock();

            //then
            LiveVideoStreamingController.startLiveVideoStreaming(edgeConnectorForKVSConfiguration, recorderService);

            //verify
            assertEquals(edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount(), 1);
            verify(videoUploader, times(1)).uploadStream(any(), any(), any(), any());
        }
    }

    @Test
    public void testStartLiveVideoStreaming_Throws_Uploading_Exceptions_GetProcessLock_Exception() throws InterruptedException, IOException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ReentrantLock mockLock = mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .liveStreamingRequestsCount(0)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
            mockRecordingController.when(() -> RecordingController.startRecordingJob(any(), any()))
                    .thenAnswer((Answer<Void>) invocation -> null);

            when(videoRecorder.toggleAppDataOutputStream(anyBoolean())).thenReturn(true);
            when(videoRecorder.setAppDataOutputStream(any())).thenReturn(true);
            doThrow(new KvsStreamingException("")).when(videoUploader).uploadStream(any(), any(), any(), any());
            LiveVideoStreamingController.setRetryOnFail(false);

            when(mockLock.tryLock(anyLong(), any())).thenReturn(false);
            when(mockLock.isHeldByCurrentThread()).thenReturn(true);
            doNothing().when(mockLock).unlock();

            //then
            LiveVideoStreamingController.startLiveVideoStreaming(edgeConnectorForKVSConfiguration, recorderService);

            //verify
            assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
        }
    }

    @Test
    public void testStopLiveVideoStreaming() throws InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .inputStream(new PipedInputStream())
                .outputStream(new PipedOutputStream())
                .liveStreamingRequestsCount(1)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        when(videoRecorder.toggleAppDataOutputStream(anyBoolean())).thenReturn(true);
        doNothing().when(videoUploader).close();
        try (MockedStatic<StreamUtils> mockStreamUtils = mockStatic(StreamUtils.class)) {
            mockStreamUtils.when(() -> StreamUtils.flushInputStream(any()))
                    .thenAnswer((Answer<Void>) invocation -> null);

            try (MockedStatic<RecordingController> mockRecordingController = mockStatic(RecordingController.class)) {
                mockRecordingController.when(() -> RecordingController.stopRecordingJob(any()))
                        .thenAnswer((Answer<Void>) invocation -> null);

                //then
                LiveVideoStreamingController.stopLiveVideoStreaming(edgeConnectorForKVSConfiguration);
                //verify
                assertEquals(edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount(), 0);
            }
        }
    }

    @Test
    public void testStopLiveVideoStreaming_HasMoreThanOneLiveVideoStreaming() throws InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .videoRecorder(videoRecorder)
                .videoUploader(videoUploader)
                .inputStream(new PipedInputStream())
                .outputStream(new PipedOutputStream())
                .liveStreamingRequestsCount(2)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        //then
        LiveVideoStreamingController.stopLiveVideoStreaming(edgeConnectorForKVSConfiguration);

        //verify
        assertEquals(edgeConnectorForKVSConfiguration.getLiveStreamingRequestsCount(), 1);
    }

    @Test
    public void testStopLiveVideoStreaming_GetProcessLock_TimeOut() throws InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(false);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        //then
        LiveVideoStreamingController.stopLiveVideoStreaming(edgeConnectorForKVSConfiguration);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testStopLiveVideoStreaming_GetProcessLock_Exception() throws InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        //then
        LiveVideoStreamingController.stopLiveVideoStreaming(edgeConnectorForKVSConfiguration);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testGetStopLiveStreamingTask() {
        //when
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        //then
        Runnable stopLiveStreamingTask = LiveVideoStreamingController.getStopLiveStreamingTask(edgeConnectorForKVSConfiguration);
        stopLiveStreamingTask.run();

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }
}
