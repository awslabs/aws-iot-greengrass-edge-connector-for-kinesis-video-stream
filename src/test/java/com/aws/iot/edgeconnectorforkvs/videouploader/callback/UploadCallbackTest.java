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

package com.aws.iot.edgeconnectorforkvs.videouploader.callback;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.dataaccessor.StreamManager;
import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.VideoFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class UploadCallbackTest {

    private static final Long TEST_TIME = 1600000000000L;

    private Instant instantNow;

    private UploadCallBack uploadCallBack;

    @Mock
    private EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration;

    @Mock
    private StreamManager streamManager;

    @BeforeEach
    public void setupForAll() {
        instantNow = Instant.ofEpochMilli(TEST_TIME);
    }

    @Test
    public void addPersistedFragmentTimecode_addValidTimecode_fileStatusUpdated() throws InterruptedException {
        //when
        when(this.edgeConnectorForKVSConfiguration.getStreamManager()).thenReturn(streamManager);
        when(streamManager.pushData(any(), any(), any(), any())).thenReturn(0L);
        List<VideoFile> videoFiles = new ArrayList<>();
        VideoFile videoFile1 = new VideoFile("video_" + instantNow.toEpochMilli() + ".mkv");
        VideoFile videoFile2 = new VideoFile("video_" + (instantNow.toEpochMilli() + 10) + ".mkv");
        VideoFile videoFile3 = new VideoFile("video_" + (instantNow.toEpochMilli() + 20) + ".mkv");
        VideoFile videoFile4 = new VideoFile("video_" + (instantNow.toEpochMilli() + 30) + ".mkv");
        VideoFile videoFile5 = new VideoFile("video_" + (instantNow.toEpochMilli() + 40) + ".mkv");
        videoFiles.add(videoFile1);
        videoFiles.add(videoFile2);
        videoFiles.add(videoFile3);
        videoFiles.add(videoFile4);
        videoFiles.add(videoFile5);

        uploadCallBack = new UploadCallBack(Date.from(instantNow), edgeConnectorForKVSConfiguration);
        uploadCallBack.setVideoFiles(videoFiles);
        //then
        uploadCallBack.addPersistedFragmentTimecode(11L);
        uploadCallBack.addPersistedFragmentTimecode(12L);
        uploadCallBack.addPersistedFragmentTimecode(31L);
        uploadCallBack.onComplete();

        //verify
        Thread.sleep(2000);
        VideoFile uploadedFile = uploadCallBack.getUploadedFile();
        VideoFile uploadingFile = uploadCallBack.getUploadingFile();
        Assertions.assertNotNull(uploadingFile);
        Assertions.assertEquals(uploadingFile, videoFile4);
        Assertions.assertEquals(uploadedFile, videoFile2);
        Assertions.assertTrue(videoFile1.isUploaded());
        Assertions.assertTrue(videoFile2.isUploaded());
        Assertions.assertTrue(videoFile3.isUploaded());
        Assertions.assertTrue(videoFile4.isUploaded());
        Assertions.assertTrue(videoFile5.isUploaded());
    }

    @Test
    public void addPersistedFragmentTimecode_addInvalidTimecode_noFileStatusUpdated() throws InterruptedException {
        //when
        when(this.edgeConnectorForKVSConfiguration.getStreamManager()).thenReturn(streamManager);
        when(streamManager.pushData(any(), any(), any(), any())).thenReturn(0L);
        List<VideoFile> videoFiles = new ArrayList<>();
        VideoFile videoFile1 = new VideoFile(new File("video_" + instantNow.toEpochMilli() + ".mkv"));
        videoFiles.add(videoFile1);

        File nullFile = null;
        VideoFile videoFile2 = new VideoFile(nullFile);
        Assertions.assertEquals(0, videoFile2.getVideoDate().getTime());

        uploadCallBack = new UploadCallBack(Date.from(instantNow), edgeConnectorForKVSConfiguration);
        uploadCallBack.setVideoFiles(videoFiles);
        uploadCallBack.addPersistedFragmentTimecode(-1L);
        //verify
        Thread.sleep(2000);
        VideoFile uploadedFile = uploadCallBack.getUploadingFile();
        Assertions.assertNull(uploadedFile);
        Assertions.assertFalse(uploadCallBack.getPersistedFragmentTimecodes().isEmpty());
        Assertions.assertFalse(uploadCallBack.getVideoFiles().isEmpty());
    }

    @Test
    public void onComplete_noVideoFile_noFileIsProcessing() {
        List<VideoFile> videoFiles = new ArrayList<>();


        uploadCallBack = new UploadCallBack(Date.from(instantNow), edgeConnectorForKVSConfiguration);
        uploadCallBack.setVideoFiles(videoFiles);
        uploadCallBack.onComplete();

        Assertions.assertNull(uploadCallBack.getUploadingFile());
        Assertions.assertNull(uploadCallBack.getUploadedFile());
    }

    @Test
    public void testSetDateBegin() {
        //when
        Date testTime = new Date(TEST_TIME);
        //then
        uploadCallBack = new UploadCallBack(new Date(), edgeConnectorForKVSConfiguration);
        uploadCallBack.setDateBegin(testTime);
    }
}
