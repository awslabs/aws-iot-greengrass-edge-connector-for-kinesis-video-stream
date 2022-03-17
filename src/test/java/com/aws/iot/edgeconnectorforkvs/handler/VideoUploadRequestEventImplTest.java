package com.aws.iot.edgeconnectorforkvs.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.aws.iot.edgeconnectorforkvs.controller.HistoricalVideoController;
import com.aws.iot.edgeconnectorforkvs.controller.LiveVideoStreamingController;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@ExtendWith(MockitoExtension.class)
public class VideoUploadRequestEventImplTest {
    private static final long START_TIME = 1630985820;
    private static final long END_TIME = 1630985920;
    private static final long EVENT_TIMESTAMP = 1630985924;
    private static final boolean IS_LIVE_TRUE = true;
    private static final boolean IS_LIVE_FALSE = false;

    @Test
    public void testOnStart_Live_Success() {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ExecutorService liveStreamingExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService stopLiveStreamingExecutor = Executors.newSingleThreadScheduledExecutor();
        VideoUploadRequestEventImpl videoUploadRequestEventImpl = new VideoUploadRequestEventImpl();
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);

        EdgeConnectorForKVSConfiguration configuration = EdgeConnectorForKVSConfiguration.builder()
                .stopLiveStreamingTaskFuture(scheduledFuture)
                .build();
        Runnable task = new Thread();
        try (MockedStatic<LiveVideoStreamingController> mockLiveVideoStreamingController =
                     mockStatic(LiveVideoStreamingController.class)) {
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController
                    .startLiveVideoStreaming(any(), any())).thenAnswer((Answer<Void>) invocation -> null);
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController
                    .getStopLiveStreamingTask(any())).thenReturn(task);

            //then
            videoUploadRequestEventImpl.onStart(IS_LIVE_TRUE, EVENT_TIMESTAMP, START_TIME, END_TIME, configuration,
                    recorderService, liveStreamingExecutor, stopLiveStreamingExecutor);
            //verify
            assertNotNull(configuration.getStopLiveStreamingTaskFuture());
            verify(scheduledFuture, times(1)).cancel(eq(false));
        }
    }

    @Test
    public void testOnStart_Live_HasOneLiveStreaming_Existing_Success() {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ExecutorService liveStreamingExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService stopLiveStreamingExecutor = Executors.newSingleThreadScheduledExecutor();
        VideoUploadRequestEventImpl videoUploadRequestEventImpl = new VideoUploadRequestEventImpl();
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);

        EdgeConnectorForKVSConfiguration configuration = EdgeConnectorForKVSConfiguration.builder()
                .stopLiveStreamingTaskFuture(null)
                .build();
        Runnable task = new Thread();
        try (MockedStatic<LiveVideoStreamingController> mockLiveVideoStreamingController =
                     mockStatic(LiveVideoStreamingController.class)) {
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController
                    .startLiveVideoStreaming(any(), any())).thenAnswer((Answer<Void>) invocation -> null);
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController
                    .getStopLiveStreamingTask(any())).thenReturn(task);

            //then
            videoUploadRequestEventImpl.onStart(IS_LIVE_TRUE, EVENT_TIMESTAMP, START_TIME, END_TIME, configuration,
                    recorderService, liveStreamingExecutor, stopLiveStreamingExecutor);
            //verify
            assertNotNull(configuration.getStopLiveStreamingTaskFuture());
            verify(scheduledFuture, times(0)).cancel(eq(false));
        }
    }

    @Test
    public void testOnStart_Live_HasOneLiveStreaming_Throws_Exception() {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ExecutorService liveStreamingExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService stopLiveStreamingExecutor = Executors.newSingleThreadScheduledExecutor();
        VideoUploadRequestEventImpl videoUploadRequestEventImpl = new VideoUploadRequestEventImpl();
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);

        EdgeConnectorForKVSConfiguration configuration = EdgeConnectorForKVSConfiguration.builder()
                .stopLiveStreamingTaskFuture(null)
                .fatalStatus(new AtomicBoolean(false))
                .build();
        Runnable task = new Thread();
        try (MockedStatic<LiveVideoStreamingController> mockLiveVideoStreamingController =
                     mockStatic(LiveVideoStreamingController.class)) {
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController
                    .getStopLiveStreamingTask(any())).thenReturn(task);
            mockLiveVideoStreamingController.when(() -> LiveVideoStreamingController
                    .startLiveVideoStreaming(any(), any())).thenThrow(new IOException());

            //then
            videoUploadRequestEventImpl.onStart(IS_LIVE_TRUE, EVENT_TIMESTAMP, START_TIME, END_TIME, configuration,
                    recorderService, liveStreamingExecutor, stopLiveStreamingExecutor);
            //verify
            assertNotNull(configuration.getStopLiveStreamingTaskFuture());
            verify(scheduledFuture, times(0)).cancel(eq(false));
            assertTrue(configuration.getFatalStatus().get());
        }
    }

    @Test
    public void testOnStart_OnDemand_Success() {
        //when
        ExecutorService recorderService = Executors.newSingleThreadExecutor();
        ExecutorService liveStreamingExecutor = Executors.newSingleThreadExecutor();
        ScheduledExecutorService stopLiveStreamingExecutor = Executors.newSingleThreadScheduledExecutor();
        VideoUploadRequestEventImpl videoUploadRequestEventImpl = new VideoUploadRequestEventImpl();

        EdgeConnectorForKVSConfiguration configuration = EdgeConnectorForKVSConfiguration.builder()
                .build();
        try (MockedStatic<HistoricalVideoController> mockHistoricalVideoController =
                     mockStatic(HistoricalVideoController.class)) {
            mockHistoricalVideoController.when(() -> HistoricalVideoController
                    .startHistoricalVideoUploading(any(), anyLong(), anyLong())).thenAnswer((Answer<Void>) invocation -> null);

            //then
            videoUploadRequestEventImpl.onStart(IS_LIVE_FALSE, EVENT_TIMESTAMP, START_TIME, END_TIME, configuration,
                    recorderService, liveStreamingExecutor, stopLiveStreamingExecutor);
            //verify
            mockHistoricalVideoController.verify(
                    () -> HistoricalVideoController.startHistoricalVideoUploading(any(), anyLong(), anyLong()),
                    times(1));
        }
    }

    @Test
    public void testOnError() {
        //when
        EdgeConnectorForKVSConfiguration configuration = EdgeConnectorForKVSConfiguration.builder()
                .fatalStatus(new AtomicBoolean(false))
                .build();
        VideoUploadRequestEventImpl videoUploadRequestEventImpl = new VideoUploadRequestEventImpl();
        //then
        videoUploadRequestEventImpl.onError("mockErrorMessage", configuration);
        //verify
        assertFalse(configuration.getFatalStatus().get());
    }
}
