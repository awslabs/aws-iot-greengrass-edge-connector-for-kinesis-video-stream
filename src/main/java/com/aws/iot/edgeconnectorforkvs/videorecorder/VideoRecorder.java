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

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import com.aws.iot.edgeconnectorforkvs.monitor.Monitor;
import com.aws.iot.edgeconnectorforkvs.monitor.callback.CheckCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.VideoRecorderBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.AppDataCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.CameraType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch.RecorderBranchApp;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch.RecorderBranchFile;
import com.aws.iot.edgeconnectorforkvs.videorecorder.module.camera.RecorderCameraRtsp;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.Config;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Pipeline;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

/**
 * Video Recorder Builder class.
 */
@Slf4j
public class VideoRecorder extends VideoRecorderBase {
    class RecorderBranchAppMonitor extends RecorderBranchApp {
        private static final long MONITOR_PERIOD = Config.APP_PATH_MONITOR_PERIOD;
        private String monitorSubject;
        private CheckCallback monitorCheck;
        private AtomicInteger appCallbackDataCntOld;
        private AtomicInteger appCallbackDataCntNew;

        RecorderBranchAppMonitor(ContainerType type, GstDao dao, Pipeline pipeline, String subj) {
            super(type, dao, pipeline);
            this.monitorSubject = subj;
            this.appCallbackDataCntNew = new AtomicInteger();
            this.appCallbackDataCntOld = new AtomicInteger();
            this.monitorCheck = (monitor, subject, userData) -> {
                if (this.appCallbackDataCntNew.get() == this.appCallbackDataCntOld.get()) {
                    willStop(false, String.format("%s doesn't receive new data in %d ms.", subject,
                            RecorderBranchAppMonitor.MONITOR_PERIOD));
                } else {
                    this.appCallbackDataCntOld.set(this.appCallbackDataCntNew.get());
                    Monitor.getMonitor().add(subject, this.monitorCheck,
                            RecorderBranchAppMonitor.MONITOR_PERIOD, null);
                }
            };
        }

        @Override
        protected void onBindBegin() {
            this.appCallbackDataCntNew.set(0);
            this.appCallbackDataCntOld.set(0);
            super.onBindBegin();
        }

        @Override
        protected void onBindEnd() {
            super.onBindEnd();
            Monitor.getMonitor().add(this.monitorSubject, this.monitorCheck,
                    RecorderBranchAppMonitor.MONITOR_PERIOD, null);
        }

        @Override
        protected void onUnbindBegin() {
            Monitor.getMonitor().remove(this.monitorSubject);
            super.onUnbindBegin();
        }

        public void increaseAppCallbackDataCnt() {
            this.appCallbackDataCntNew.incrementAndGet();
        }

        CheckCallback getMonitorCheck() {
            return this.monitorCheck;
        }
    }

    class RecorderBranchFileMonitor extends RecorderBranchFile {
        private static final long MONITOR_PERIOD = Config.FILE_PATH_MONITOR_PERIOD;
        private String monitorSubject;
        private CheckCallback monitorCheck;
        private String filePathOld;
        private String filePathNew;

        RecorderBranchFileMonitor(ContainerType type, GstDao dao, Pipeline pipeline,
                String filePath, String subj) {
            super(type, dao, pipeline, filePath);

            this.monitorSubject = subj;
            this.filePathOld = null;
            this.filePathNew = null;
            this.monitorCheck = (monitor, subject, userData) -> {
                this.filePathNew = this.getCurrentFilePath();

                if (this.filePathOld.equals(this.filePathNew)) {
                    willStop(false, String.format("%s doesn't rotate files in %d ms.", subject,
                            RecorderBranchFileMonitor.MONITOR_PERIOD));
                } else {
                    this.filePathOld = this.filePathNew;
                    Monitor.getMonitor().add(subject, this.monitorCheck,
                            RecorderBranchFileMonitor.MONITOR_PERIOD, null);
                }
            };
        }

        @Override
        protected void onBindBegin() {
            this.filePathOld = "";
            this.filePathNew = "";
            super.onBindBegin();
        }

        @Override
        protected void onBindEnd() {
            super.onBindEnd();
            Monitor.getMonitor().add(this.monitorSubject, this.monitorCheck,
                    RecorderBranchFileMonitor.MONITOR_PERIOD, null);
        }

        @Override
        protected void onUnbindBegin() {
            Monitor.getMonitor().remove(this.monitorSubject);
            super.onUnbindBegin();
        }

        CheckCallback getMonitorCheck() {
            return this.monitorCheck;
        }
    }

    private final Object appCallbackBranchLock = new Object[0];
    private final Object appOStreamBranchLock = new Object[0];
    private RecorderBranchFileMonitor fileBranch;
    private RecorderBranchAppMonitor callbackBranch;
    private RecorderBranchAppMonitor streamBranch;
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
        } else if (toEnable) {
            result = this.bindBranch(Config.CALLBACK_PATH);
        } else {
            result = this.unbindBranch(Config.CALLBACK_PATH);
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
            if (!this.callbackBranch.isAutoBind()) {
                this.appCallback = notifier;
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
        } else if (toEnable) {
            result = this.bindBranch(Config.OSTREAM_PATH);
        } else {
            result = this.unbindBranch(Config.OSTREAM_PATH);
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
            if (!this.streamBranch.isAutoBind()) {
                this.appOutputStream = outputStream;
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

    boolean addCameraSource(CameraType type, String sourceUrl) {
        boolean result = false;

        if (type == CameraType.RTSP) {
            RecorderCameraRtsp cameraSrc =
                    new RecorderCameraRtsp(this.getGstCore(), this.getPipeline(), sourceUrl);
            result = this.addCameraSource(cameraSrc);
        } else {
            throw new IllegalArgumentException("Unsupported camera source type: " + type);
        }

        return result;
    }

    boolean registerFileSink(ContainerType containerType, String recorderFilePath)
            throws IllegalArgumentException {
        this.fileBranch = new RecorderBranchFileMonitor(containerType, this.getGstCore(),
                this.getPipeline(), recorderFilePath, this.recorderName + "_" + Config.FILE_PATH);

        return this.addBranch(this.fileBranch, Config.FILE_PATH, true);
    }

    /**
     * Set properties to the splitmuxsink in the fileBranch.
     *
     * @param property property name
     * @param data value
     * @return true if the property is set successfully or anynced
     */
    public boolean setFilePathProperty(String property, Object data) {
        boolean ret = true;

        if (this.fileBranch == null) {
            log.error("SetProperty fails because file sink is not registered.");
            ret = false;
        } else {
            this.fileBranch.setProperty(property, data);
        }

        return ret;
    }

    @Synchronized("appCallbackBranchLock")
    boolean registerAppDataCallback(ContainerType type, AppDataCallback notifier)
            throws IllegalArgumentException {
        this.callbackBranch = new RecorderBranchAppMonitor(type, this.getGstCore(),
                this.getPipeline(), this.recorderName + "_" + Config.CALLBACK_PATH);
        this.appCallback = notifier;

        this.callbackBranch.registerNewSample(bBuff -> {
            this.callbackBranch.increaseAppCallbackDataCnt();
            this.appCallback.newSample(this, bBuff);
        });

        return this.addBranch(this.callbackBranch, Config.CALLBACK_PATH, false);
    }

    @Synchronized("appOStreamBranchLock")
    boolean registerAppDataOutputStream(ContainerType type, OutputStream outputStream)
            throws IllegalArgumentException {
        this.streamBranch = new RecorderBranchAppMonitor(type, this.getGstCore(),
                this.getPipeline(), this.recorderName + "_" + Config.OSTREAM_PATH);
        this.appOutputStream = outputStream;

        this.streamBranch.registerNewSample(bBuff -> {
            byte[] array = new byte[bBuff.remaining()];

            bBuff.get(array);
            try {
                this.streamBranch.increaseAppCallbackDataCnt();
                this.appOutputStream.write(array);
                this.appOutputStream.flush();
            } catch (IOException e) {
                log.error("fail to write OutputStream: {}.", e.getMessage());
            }
        });

        return this.addBranch(this.streamBranch, Config.OSTREAM_PATH, false);
    }
}
