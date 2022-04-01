package com.aws.iot.edgeconnectorforkvs.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videouploader.VideoUploaderClient;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.StatusChangedCallBack;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.UploadCallBack;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.VideoUploaderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

@ExtendWith(MockitoExtension.class)
public class HistoricalVideoControllerTest {

    private final static long startTime = 1646875503L;
    private final static long endTime = 1646875505L;
    @Mock
    private VideoUploaderClient videoUploader;
    @Mock
    VideoUploaderClient.VideoUploaderClientBuilder videoUploaderClientBuilder;

    @Test
    public void testStartHistoricalVideoUploading_Success() {
        //when
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
                spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getVideoUploaderClientBuilder()).thenReturn(videoUploaderClientBuilder);
        when(videoUploaderClientBuilder.build()).thenReturn(videoUploader);
        doNothing().when(videoUploader).uploadHistoricalVideo(any(),any(), any(), any());

        ArgumentCaptor<Date> startTimeArgumentCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> endTimeArgumentCaptor = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<StatusChangedCallBack> statusChangedCallBackArgumentCaptor =
                ArgumentCaptor.forClass(StatusChangedCallBack.class);
        ArgumentCaptor<UploadCallBack> uploadCallBackArgumentCaptor =
                ArgumentCaptor.forClass(UploadCallBack.class);

        //then
        HistoricalVideoController.startHistoricalVideoUploading(edgeConnectorForKVSConfiguration,
                startTime, endTime);
        //verify
        verify(videoUploader).uploadHistoricalVideo(startTimeArgumentCaptor.capture(),
                endTimeArgumentCaptor.capture(),
                statusChangedCallBackArgumentCaptor.capture(),
                uploadCallBackArgumentCaptor.capture());
        assertEquals(startTimeArgumentCaptor.getValue().getTime(), startTime);
        assertEquals(endTimeArgumentCaptor.getValue().getTime(), endTime);
    }

    @Test
    public void testStartHistoricalVideoUploading_Fail() {
        //when
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
                spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getVideoUploaderClientBuilder()).thenReturn(videoUploaderClientBuilder);
        when(videoUploaderClientBuilder.build()).thenReturn(videoUploader);
        // Throw exception the first time and then do nothing the second time
        doThrow(new VideoUploaderException("")).doNothing().when(videoUploader)
                .uploadHistoricalVideo(any(),any(), any(), any());

        //then
        HistoricalVideoController.startHistoricalVideoUploading(edgeConnectorForKVSConfiguration,
                startTime, endTime);
        //verify
        verify(videoUploader, times(2)).uploadHistoricalVideo(any(),any(),any(),any());
    }

    @Test
    public void testStartHistoricalVideoUploading_FailWithOutRetry() {
        //when
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = Mockito.
                spy(EdgeConnectorForKVSConfiguration.class);

        when(edgeConnectorForKVSConfiguration.getVideoUploaderClientBuilder()).thenReturn(videoUploaderClientBuilder);
        when(videoUploaderClientBuilder.build()).thenReturn(videoUploader);
        // Throw exception the first time and then do nothing the second time
        doThrow(new VideoUploaderException("")).when(videoUploader)
                .uploadHistoricalVideo(any(),any(), any(), any());
        HistoricalVideoController.setRetryOnFail(false);

        //then
        HistoricalVideoController.startHistoricalVideoUploading(edgeConnectorForKVSConfiguration,
                startTime, endTime);
        //verify
        verify(videoUploader, times(1)).uploadHistoricalVideo(any(),any(),any(),any());
    }
}
