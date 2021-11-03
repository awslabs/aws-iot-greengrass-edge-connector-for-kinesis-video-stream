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

package com.aws.iot.edgeconnectorforkvs.videorecorder;

import java.io.OutputStream;
import java.util.concurrent.RejectedExecutionException;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderBranchBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.AppDataCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.CameraType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Pipeline;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Builder for configuring and construct recorders.
 */
@Slf4j
public class VideoRecorderBuilder {
    private VideoRecorder recorder;
    private boolean hasCamera;
    private boolean hasFileBranch;
    private boolean hasCallbackBranch;
    private boolean hasStreamBranch;
    private boolean hasCustomBranch;

    /**
     * Builder constructor.
     *
     * @param statusCallback a callback is used to receive notifications of status.
     */
    public VideoRecorderBuilder(StatusCallback statusCallback) {
        this(new GstDao(), statusCallback);
    }

    /**
     * Get used GstDao. It can be used when building customized cameras or branches.
     *
     * @return GstDao instance
     */
    public GstDao getGstDao() {
        return this.recorder.getGstCore();
    }

    /**
     * Get used Pipeline. It can be used when building customized cameras or branches.
     *
     * @return Pipeline instance
     */
    public Pipeline getPipeline() {
        return this.recorder.getPipeline();
    }

    /**
     * Register a customized camera module to recorder module.
     *
     * @param cameraSrc camera module
     * @return true if success
     */
    public boolean registerCustomCamera(RecorderCameraBase cameraSrc) {
        boolean canRegister = false;

        if (!this.hasCamera) {
            canRegister = this.recorder.registerCamera(cameraSrc);
            this.hasCamera |= canRegister;
        }

        return canRegister;
    }

    /**
     * Register a camera to recorder module by type.
     *
     * @param type camera type
     * @param sourceUrl source Url
     * @return true if success
     */
    public boolean registerCamera(CameraType type, String sourceUrl) {
        boolean canRegister = false;

        if (!this.hasCamera) {
            canRegister = this.recorder.registerCamera(type, sourceUrl);
            this.hasCamera |= canRegister;
        }

        return canRegister;
    }

    /**
     * Set GStreamer properties for camera source.
     *
     * @param property camera properties
     * @param data value to be set
     * @return true if success
     */
    public boolean setCameraProperty(String property, Object data) {
        return this.recorder.setCameraProperty(property, data);
    }

    /**
     * Register a customized branch module to recorder module.
     *
     * @param branch customized branch
     * @param branchName branch name
     * @return true if success
     */
    public boolean registerCustomBranch(RecorderBranchBase branch, String branchName) {
        boolean canRegister = this.recorder.registerBranch(branch, branchName);

        this.hasCustomBranch |= canRegister;

        return canRegister;
    }

    /**
     * Setup File path and container type for storing.
     *
     * @param containerType Container type that will be used to store media data
     * @param recorderFilePath File path where RTSP media data will be stored. It does not contain
     *        file name extension because recorder will append extension automatically.
     * @return True if splitmuxsink is added
     */
    public boolean registerFileSink(ContainerType containerType, String recorderFilePath)
            throws IllegalArgumentException {
        boolean canRegister = false;

        if (!this.hasFileBranch) {
            canRegister = this.recorder.registerFileSink(containerType, recorderFilePath);
            this.hasFileBranch |= canRegister;
        }

        return canRegister;
    }

    /**
     * Set GStreamer properties for splitmuxsink.
     *
     * @param property splitmuxsink properties
     * @param data value to be set
     * @return true if success
     */
    public boolean setFilePathProperty(String property, Object data) {
        boolean result = false;

        if (!this.hasFileBranch) {
            log.error("SetProperty fails because file sink is not registered.");
        } else {
            result = this.recorder.setFilePathProperty(property, data);
        }

        return result;
    }

    /**
     * Register callback for receiving streaming data.
     *
     * @param type Container type that will be used for media streaming
     * @param notifier A callback is used to receive data of new streaming samples
     * @return True if notifier is registered successfully.
     */
    public boolean registerAppDataCallback(ContainerType type, @NonNull AppDataCallback notifier)
            throws IllegalArgumentException {
        boolean canRegister = false;

        if (!this.hasCallbackBranch) {
            canRegister = this.recorder.registerAppDataCallback(type, notifier);
            this.hasCallbackBranch |= canRegister;
        }

        return canRegister;
    }

    /**
     * Register OutputStream for receiving streaming data.
     *
     * @param type Container type that will be used for media streaming
     * @param outputStream A OutputStream is used to receive data of new streaming samples
     * @return True if notifier is registered successfully.
     */
    public boolean registerAppDataOutputStream(ContainerType type,
            @NonNull OutputStream outputStream) throws IllegalArgumentException {
        boolean canRegister = false;

        if (!this.hasStreamBranch) {
            canRegister = this.recorder.registerAppDataOutputStream(type, outputStream);
            this.hasStreamBranch |= canRegister;
        }

        return canRegister;
    }

    /**
     * Construct a VideoRecorder instance.
     *
     * @return a recorder instance
     */
    public VideoRecorder construct() throws RejectedExecutionException {
        if (!this.hasCamera) {
            throw new RejectedExecutionException("A recorder should have a camera.");
        } else if (!this.hasFileBranch && !this.hasCallbackBranch && !this.hasStreamBranch
                && !this.hasCustomBranch) {
            throw new RejectedExecutionException("A recorder should have a data branch.");
        }
        return this.recorder;
    }

    /**
     * Friendly builder for UT.
     *
     * @param dao Gst API data access object
     * @param statusCallback a callback is used to receive notifications of status.
     */
    VideoRecorderBuilder(GstDao dao, StatusCallback statusCallback) {
        this.recorder = new VideoRecorder(dao, statusCallback);
        this.hasCamera = false;
        this.hasFileBranch = false;
        this.hasCallbackBranch = false;
        this.hasStreamBranch = false;
        this.hasCustomBranch = false;
    }
}
