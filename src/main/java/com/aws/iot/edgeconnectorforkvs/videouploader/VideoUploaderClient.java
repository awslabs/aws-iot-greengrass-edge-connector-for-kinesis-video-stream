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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoAsyncClient;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoPutMediaClient;
import com.amazonaws.services.kinesisvideo.PutMediaAckResponseHandler;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.AckEvent;
import com.amazonaws.services.kinesisvideo.model.AckEventType;
import com.amazonaws.services.kinesisvideo.model.FragmentTimecodeType;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import com.amazonaws.services.kinesisvideo.model.PutMediaRequest;

import com.aws.iot.edgeconnectorforkvs.util.Constants;
import com.aws.iot.edgeconnectorforkvs.util.VideoRecordVisitor;
import com.aws.iot.edgeconnectorforkvs.videouploader.callback.UploadCallBack;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvFilesInputStream;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvInputStream;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.VideoFile;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.KvsStreamingException;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.VideoUploaderException;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CountDownLatch;

/**
 * Client implementation class for the use of {@link AmazonKinesisVideoPutMedia}. To create, obtain an instance of the
 * builder via builder() and call build() after configuring desired options.
 */
@Slf4j
public class VideoUploaderClient implements VideoUploader {

    /* Default connection timeout of put media data endpoint. */
    private static final int CONNECTION_TIMEOUT_IN_MILLIS = 10_000;

    /* AWS credentials provider to use. */
    private AWSCredentialsProvider awsCredentialsProvider;

    /* AWS region to use. */
    private Region region;

    /* A folder that stores all videos files. */
    private VideoRecordVisitor videoRecordVisitor;

    /* KVS stream name to use. */
    private String kvsStreamName;

    /* The data endpoint of put media. */
    private String dataEndpoint;

    /* KVS frontend client for query and describe stream. */
    private AmazonKinesisVideo kvsFrontendClient;

    /* KVS data client for uploading video. */
    private AmazonKinesisVideoPutMedia kvsDataClient;

    private final Object taskStatusLock = new Object();

    /* Indicate if we are doing an uploading task. */
    private boolean isTaskOnGoing;

    /* Indicate we are about to terminating a task. */
    private boolean isTaskTerminating;

    /* A latch to wait or terminate a put media action. */
    private CountDownLatch putMediaLatch;

    private KvsStreamingException lastKvsStreamingException = null;

    /**
     * The factory creator of VideoUploaderClient.
     *
     * @param awsCredentialsProvider AWS credential provider
     * @param region                 Region
     * @param recordFilePath         Record path that contain videos
     * @param kvsStreamName          KVS stream name
     * @return Video uploader client
     */
    @Builder
    public static VideoUploaderClient create(@NonNull AWSCredentialsProvider awsCredentialsProvider,
                                             @NonNull Region region,
                                             @NonNull String recordFilePath,
                                             @NonNull String kvsStreamName) {
        VideoUploaderClient vuc = new VideoUploaderClient();
        vuc.awsCredentialsProvider = awsCredentialsProvider;
        vuc.region = region;
        vuc.kvsStreamName = kvsStreamName;
        vuc.videoRecordVisitor = VideoRecordVisitor.builder()
                .recordFilePath(recordFilePath)
                .build();
        vuc.kvsFrontendClient = AmazonKinesisVideoAsyncClient.builder()
                .withCredentials(awsCredentialsProvider)
                .withRegion(region.getName())
                .build();
        vuc.isTaskOnGoing = false;
        return vuc;
    }

    /**
     * Upload all videos that its date is between start time and end time.
     *
     * @param videoUploadingStartTime Video upload start time
     * @param videoUploadingEndTime   Video upload end time
     * @param statusChangedCallBack   A callback for updating status
     * @param uploadCallBack          A callback for task completes or fails
     * @throws IllegalArgumentException The value for this input parameter is invalid
     * @throws VideoUploaderException   Throw this exception if there is already an on going task
     */
    @Override
    public void uploadHistoricalVideo(@NonNull Date videoUploadingStartTime, @NonNull Date videoUploadingEndTime,
                                      Runnable statusChangedCallBack, UploadCallBack uploadCallBack)
            throws IllegalArgumentException, VideoUploaderException, KvsStreamingException {
        if (videoUploadingEndTime.before(videoUploadingStartTime)) {
            throw new IllegalArgumentException("Invalid time period");
        }

        taskStart();
        doUploadHistoricalVideo(videoUploadingStartTime, videoUploadingEndTime, statusChangedCallBack, uploadCallBack);
        taskEnd();
    }

    private void doUploadHistoricalVideo(Date videoUploadingStartTime, Date videoUploadingEndTime,
                                         Runnable statusChangedCallBack, UploadCallBack uploadCallBack)
            throws KvsStreamingException {
        if (dataEndpoint == null) {
            dataEndpoint = getDataEndpoint();
        }

        List<VideoFile> videoFiles = videoRecordVisitor.listFilesToUpload(videoUploadingStartTime,
                videoUploadingEndTime);
        if (uploadCallBack != null) {
            uploadCallBack.setVideoFiles(videoFiles);
        }

        ListIterator<VideoFile> filesToUpload = videoFiles.listIterator();

        while (filesToUpload.hasNext() && !isTaskTerminating) {
            final Date videoStartTime = filesToUpload.next().getVideoDate();
            if (dataEndpoint == null) {
                uploadCallBack.setDateBegin(videoStartTime);
            }
            MkvFilesInputStream mkvFilesInputStream = new MkvFilesInputStream(filesToUpload);
            filesToUpload.previous();
            doUploadStream(mkvFilesInputStream, videoStartTime, statusChangedCallBack, uploadCallBack);
        }

        if (isTaskTerminating) {
            log.info("Quit uploading historical video because task is terminating");
        }

        log.info("No more video files to upload");
    }

    /**
     * Upload a video from {@link InputStream}.
     *
     * @param inputStream             The input stream
     * @param videoUploadingStartTime The start time of the given input stream
     * @param statusChangedCallBack   A callback for updating status
     * @param uploadCallBack          A callback for task completes or fails
     */
    @Override
    public void uploadStream(@NonNull InputStream inputStream, @NonNull Date videoUploadingStartTime,
                             Runnable statusChangedCallBack, UploadCallBack uploadCallBack)
            throws KvsStreamingException {
        taskStart();
        doUploadStream(new MkvInputStream(inputStream), videoUploadingStartTime, statusChangedCallBack, uploadCallBack);
        taskEnd();
    }

    private void doUploadStream(InputStream inputStream, Date videoUploadingStartTime, Runnable statusChangedCallBack,
                                UploadCallBack uploadCallBack) throws KvsStreamingException {
        if (dataEndpoint == null) {
            dataEndpoint = getDataEndpoint();
        }

        putMediaLatch = new CountDownLatch(1);
        PutMediaAckResponseHandler rspHandler = createResponseHandler(putMediaLatch, statusChangedCallBack,
                uploadCallBack);

        if (kvsDataClient == null) {
            kvsDataClient = AmazonKinesisVideoPutMediaClient.builder()
                    .withRegion(region.getName())
                    .withEndpoint(URI.create(dataEndpoint))
                    .withCredentials(awsCredentialsProvider)
                    .withConnectionTimeoutInMillis(CONNECTION_TIMEOUT_IN_MILLIS)
                    .withNumberOfThreads(1)
                    .build();
        }

        log.info("Uploading from input stream, timestamp: " + videoUploadingStartTime.getTime());
        kvsDataClient.putMedia(new PutMediaRequest()
                        .withStreamName(kvsStreamName)
                        .withFragmentTimecodeType(FragmentTimecodeType.RELATIVE)
                        .withPayload(inputStream)
                        .withProducerStartTimestamp(videoUploadingStartTime),
                rspHandler);

        try {
            putMediaLatch.await();
            log.info("putMedia end from latch");
        } catch (InterruptedException e) {
            log.debug("Put media is interrupted");
        }

        if (lastKvsStreamingException == null && isTaskTerminating) {
            /* It's ending from close request, let's wait a little to receive ACKs. */
            try {
                inputStream.close();
                Thread.sleep(Constants.UPLOADER_WAIT_FOR_ACKS_DELAY_MILLI_SECONDS);
            } catch (IOException exception) {
                log.error(exception.getMessage());
            } catch (InterruptedException exception) {
                log.error(exception.getMessage());
            }
        }

        kvsDataClient.close();
        kvsDataClient = null;
    }

    /**
     * Closes current task and releases all resources.
     */
    @Override
    public void close() {
        synchronized (taskStatusLock) {
            if (isTaskOnGoing) {
                isTaskTerminating = true;
                if (putMediaLatch != null) {
                    putMediaLatch.countDown();
                }
            }
        }
    }

    /**
     * Check if there is an on-going task.
     *
     * @return True if there is on-going task, or false otherwise
     */
    public boolean isOpen() {
        synchronized (taskStatusLock) {
            return isTaskOnGoing;
        }
    }

    private void taskStart() throws VideoUploaderException {
        synchronized (taskStatusLock) {
            if (isTaskOnGoing) {
                throw new VideoUploaderException("There is an on going task");
            } else {
                isTaskOnGoing = true;
                isTaskTerminating = false;
                lastKvsStreamingException = null;
            }
        }
    }

    private void taskEnd() {
        synchronized (taskStatusLock) {
            isTaskOnGoing = false;
            if (isTaskTerminating) {
                /* Task is terminated by request. Ignore exceptions. */
                lastKvsStreamingException = null;
            }
            if (lastKvsStreamingException != null) {
                throw lastKvsStreamingException;
            }
        }
    }

    /**
     * Get PUT MEDIA data endpoint of KVS.
     *
     * @return The data endpoint of PUT MEDIA REST API.
     */
    public String getDataEndpoint() {
        return kvsFrontendClient.getDataEndpoint(
                new GetDataEndpointRequest()
                        .withStreamName(kvsStreamName)
                        .withAPIName(APIName.PUT_MEDIA)).getDataEndpoint();
    }

    /**
     * Create a {@link PutMediaAckResponseHandler} that can handle messages while doing put media.
     *
     * @param latch A latch for handling asynchronous interrupt
     * @return a {@link PutMediaAckResponseHandler}
     */
    private PutMediaAckResponseHandler createResponseHandler(CountDownLatch latch,
                                                             @SuppressWarnings("unused") Runnable statusChangedCallBack,
                                                             UploadCallBack uploadCallBack) {
        return new PutMediaAckResponseHandler() {
            @Override
            public void onAckEvent(AckEvent event) {
                log.trace("onAckEvent " + event);
                if (AckEventType.Values.PERSISTED.equals(event.getAckEventType().getEnumValue())) {
                    log.info("Fragment pushed to KVS " + event.getFragmentNumber());
                    if (uploadCallBack != null) {
                        updateUploadCallbackStatus(uploadCallBack, event);
                    }
                }
                if (AckEventType.Values.ERROR.equals(event.getAckEventType().getEnumValue())) {
                    lastKvsStreamingException = new KvsStreamingException(event.toString());
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.info("onFailure");
                lastKvsStreamingException = new KvsStreamingException(t.getMessage());
                latch.countDown();
            }

            @Override
            public void onComplete() {
                log.info("onComplete");
                if (uploadCallBack != null) {
                    uploadCallBack.onComplete();
                }
                latch.countDown();
            }
        };
    }

    private void updateUploadCallbackStatus(UploadCallBack uploadCallBack, AckEvent event) {
        uploadCallBack.addPersistedFragmentTimecode(event.getFragmentTimecode());
    }
}
