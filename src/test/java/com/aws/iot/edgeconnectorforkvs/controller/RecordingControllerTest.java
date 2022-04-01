package com.aws.iot.edgeconnectorforkvs.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videorecorder.VideoRecorder;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class RecordingControllerTest {
    private static final String MOCK_KINESIS_VIDEO_STREAM_NAME = "kinesisVideoStreamName";

    @Test
    public void testStartRecordingJob_With_OtherRecorder_Enabled(@TempDir Path tempDir) throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .recordingRequestsCount(1)
                .videoRecordFolderPath(tempDir)
                .processLock(mockLock)
                .build();

        //then
        RecordingController.startRecordingJob(edgeConnectorForKVSConfiguration, recorderService);
        //verify
        assertEquals(2, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
    }

    @Test
    public void testStartRecordingJob_GetProcessLockTimeout()
            throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ReentrantLock mockLock = mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        when(mockLock.tryLock(anyLong(), any())).thenReturn(false);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        //then
        RecordingController.startRecordingJob(edgeConnectorForKVSConfiguration, recorderService);

        //verify
        assertNull(edgeConnectorForKVSConfiguration.getVideoRecorder());
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testStartRecordingJob_GetProcessLock_Exception()
            throws InterruptedException {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ReentrantLock mockLock = mock(ReentrantLock.class);


        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        when(mockLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        //then
        RecordingController.startRecordingJob(edgeConnectorForKVSConfiguration, recorderService);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
        verify(mockLock, atLeast(1)).unlock();
    }

    @Test
    public void testStopRecordingJob_With_Single_Recorder_Running() throws InterruptedException {
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();
        VideoRecorder videoRecorder = mock(VideoRecorder.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STARTED).thenReturn(RecorderStatus.STOPPED);
        doNothing().when(videoRecorder).stop();
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        doNothing().when(pipeline).close();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .videoRecorder(videoRecorder)
                .recordingRequestsCount(1)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        //then
        RecordingController.stopRecordingJob(edgeConnectorForKVSConfiguration);

        //verify
        assertFalse(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testStopRecordingJob_With_Multiple_Recorder_Running() throws InterruptedException {
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .recordingRequestsCount(2)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        //then
        RecordingController.stopRecordingJob(edgeConnectorForKVSConfiguration);

        //verify
        assertEquals(1, edgeConnectorForKVSConfiguration.getRecordingRequestsCount());
    }

    @Test
    public void testStopRecordingJob_With_MaxRetry_Reached() throws InterruptedException {
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();
        VideoRecorder videoRecorder = mock(VideoRecorder.class);
        Pipeline pipeline = mock(Pipeline.class);
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STARTED);
        doNothing().when(videoRecorder).stop();
        when(videoRecorder.getPipeline()).thenReturn(pipeline);
        doNothing().when(pipeline).close();
        RecordingController.setRestartSleepTime(0);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .videoRecorder(videoRecorder)
                .recordingRequestsCount(1)
                .fatalStatus(new AtomicBoolean(false))
                .build();

        //then
        RecordingController.stopRecordingJob(edgeConnectorForKVSConfiguration);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testStopRecordingJob_GetProcessLockTimeout()
            throws InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        when(mockLock.tryLock(anyLong(), any())).thenReturn(false);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        //then
        RecordingController.stopRecordingJob(edgeConnectorForKVSConfiguration);

        //verify
        assertNull(edgeConnectorForKVSConfiguration.getVideoRecorder());
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }

    @Test
    public void testStopRecordingJob_GetProcessLock_Exception()
            throws InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        when(mockLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        //then
        RecordingController.stopRecordingJob(edgeConnectorForKVSConfiguration);

        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
        verify(mockLock, atLeast(1)).unlock();
    }

    @Test
    public void testDeInitVideoRecorders() throws InterruptedException {
        //when
        ReentrantLock mockLock = mock(ReentrantLock.class);
        when(mockLock.tryLock(anyLong(), any())).thenReturn(false);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doNothing().when(mockLock).unlock();

        VideoRecorder videoRecorder = mock(VideoRecorder.class);
        when(videoRecorder.getStatus()).thenReturn(RecorderStatus.STARTED).thenReturn(RecorderStatus.STARTED);

        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
                .kinesisVideoStreamName(MOCK_KINESIS_VIDEO_STREAM_NAME)
                .processLock(mockLock)
                .fatalStatus(new AtomicBoolean(false))
                .videoRecorder(videoRecorder)
                .build();

        //then
        RecordingController.deInitVideoRecorders(edgeConnectorForKVSConfiguration);
        //verify
        assertTrue(edgeConnectorForKVSConfiguration.getFatalStatus().get());
    }
}
