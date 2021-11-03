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

import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.AppDataCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.CameraType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.Config;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;

/**
 * Video Recorder Builder class.
 */
@Slf4j
public class VideoRecorder extends VideoRecorderBase {
    private final Object appCallbackBranchLock = new Object[0];
    private final Object appOStreamBranchLock = new Object[0];
    private RecorderBranchFile fileBranch;
    private RecorderBranchApp callbackBranch;
    private RecorderBranchApp streamBranch;
    private AppDataCallback appCallback;
    private OutputStream appOutputStream;

    /**
     * Enable or disable receiving notifications of new streaming data.
     *
     * @param toEnable true to enable and false to disable
     * @return true if notification can be toggled
     */
    @Synchronized("appCallbackBranchLock")
    public boolean toggleAppDataCallback(boolean toEnable) {
        boolean result = false;

        if (this.callbackBranch == null) {
            log.warn("App data callback is not registered");
        } else {
            result = this.callbackBranch.toggleEmit(toEnable);
        }

        return result;
    }

    /**
     * Set callback for new streaming data.
     *
     * @param notifier callback
     * @return true if success
     */
    @Synchronized("appCallbackBranchLock")
    public boolean setAppDataCallback(@NonNull AppDataCallback notifier) {
        boolean result = false;

        if (this.callbackBranch == null) {
            log.warn("App data callback is not registered");
        } else {
            if (!this.callbackBranch.isEmitEnabled()) {
                synchronized (this.callbackBranch) {
                    this.appCallback = notifier;
                }
                result = true;
            } else {
                log.warn("Callback should be set when toggling off");
            }
        }

        return result;
    }

    /**
     * Enable or disable writing OutputStream of new streaming data.
     *
     * @param toEnable true to enable and false to disable
     * @return true if writing OutputStream can be toggled
     */
    @Synchronized("appOStreamBranchLock")
    public boolean toggleAppDataOutputStream(boolean toEnable) {
        boolean result = false;

        if (this.streamBranch == null) {
            log.warn("App data OutputStream is not registered");
        } else {
            result = this.streamBranch.toggleEmit(toEnable);
        }

        return result;
    }

    /**
     * Set OutputStream for new streaming data.
     *
     * @param outputStream OutputStream
     * @return true if success
     */
    @Synchronized("appOStreamBranchLock")
    public boolean setAppDataOutputStream(@NonNull OutputStream outputStream) {
        boolean result = false;

        if (this.streamBranch == null) {
            log.warn("App data OutputStream is not registered");
        } else {
            if (!this.streamBranch.isEmitEnabled()) {
                synchronized (this.streamBranch) {
                    this.appOutputStream = outputStream;
                }
                result = true;
            } else {
                log.warn("OutputStream should be set when toggling off");
            }
        }

        return result;
    }

    /**
     * @param dao Gst API data access object
     * @param statusCallback a callback is used to receive notifications of status.
     */
    VideoRecorder(GstDao dao, StatusCallback statusCallback) {
        super(dao, statusCallback);
        this.fileBranch = null;
        this.callbackBranch = null;
        this.streamBranch = null;
    }

    boolean registerCamera(CameraType type, String sourceUrl) {
        boolean result = false;

        if (type == CameraType.RTSP) {
            RecorderCameraRtsp cameraSrc =
                    new RecorderCameraRtsp(this.getGstCore(), this.getPipeline(), sourceUrl);
            result = this.registerCamera(cameraSrc);
        } else {
            throw new IllegalArgumentException("Unsupported camera source type: " + type);
        }

        return result;
    }

    boolean registerFileSink(ContainerType containerType, String recorderFilePath)
            throws IllegalArgumentException {
        this.fileBranch = new RecorderBranchFile(containerType, this.getGstCore(),
                this.getPipeline(), recorderFilePath);

        return this.registerBranch(this.fileBranch, Config.FILE_PATH);
    }

    boolean setFilePathProperty(String property, Object data) {
        return this.fileBranch.setProperty(property, data);
    }

    @Synchronized("appCallbackBranchLock")
    boolean registerAppDataCallback(ContainerType type, AppDataCallback notifier)
            throws IllegalArgumentException {
        this.callbackBranch = new RecorderBranchApp(type, this.getGstCore(), this.getPipeline());
        this.appCallback = notifier;

        this.callbackBranch.registerNewSample(sink -> {
            Sample smp = sink.pullSample();
            ByteBuffer bBuff = smp.getBuffer().map(false);

            synchronized (this.callbackBranch) {
                this.appCallback.newSample(this, bBuff);
            }
            smp.getBuffer().unmap();
            smp.dispose();

            return FlowReturn.OK;
        });

        return this.registerBranch(this.callbackBranch, Config.CALLBACK_PATH);
    }

    @Synchronized("appOStreamBranchLock")
    boolean registerAppDataOutputStream(ContainerType type, OutputStream outputStream)
            throws IllegalArgumentException {
        this.streamBranch = new RecorderBranchApp(type, this.getGstCore(), this.getPipeline());
        this.appOutputStream = outputStream;

        this.streamBranch.registerNewSample(sink -> {
            Sample smp = sink.pullSample();
            ByteBuffer bBuff = smp.getBuffer().map(false);
            byte[] array = new byte[bBuff.remaining()];

            bBuff.get(array);
            synchronized (this.streamBranch) {
                try {
                    this.appOutputStream.write(array);
                    this.appOutputStream.flush();
                } catch (IOException e) {
                    log.error("fail to write OutputStream: " + e.getMessage());
                }
            }
            smp.getBuffer().unmap();
            smp.dispose();

            return FlowReturn.OK;
        });

        return this.registerBranch(this.streamBranch, Config.OSTREAM_PATH);
    }
}
