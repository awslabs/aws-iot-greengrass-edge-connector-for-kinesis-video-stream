/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.aws.iot.edgeconnectorforkvs.videorecorder.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.CapabilityListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.ErrorListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase.NewPadListener;
import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.Pipeline;

/**
 * Video Recorder Builder class.
 */
@Slf4j
public class VideoRecorderBase {
    private static AtomicInteger recorderSeqNum = new AtomicInteger(0);

    /**
     * Name for current recorder instance.
     */
    public final String recorderName;

    // Synchronization
    private Lock condLock;
    private Condition stopRunningLoop;

    // Dao
    @Getter
    private GstDao gstCore;

    // Recorder basics
    private AtomicReference<RecorderStatus> currStatus;
    private StatusCallback statusCallback;

    // GStreamer basics
    private Bus bus;
    @Getter
    private Pipeline pipeline;

    // Camera
    private RecorderCameraBase cameraSource;
    private CapabilityListener cameraCapListener;
    private NewPadListener cameraPadListener;
    private ErrorListener cameraErrListener;

    // Tee
    private ArrayList<Element> videoDuplicator;
    private ArrayList<Element> audioDuplicator;
    private AtomicInteger cameraVideoCnt;
    private AtomicInteger cameraAudioCnt;

    // Branches
    private HashMap<String, RecorderBranchBase> branchPool;

    private Bus.WARNING busWarnListener;
    private Bus.ERROR busErrListener;
    private Bus.EOS busEosListener;

    private AtomicBoolean isCamCapSetAlready;
    private AtomicBoolean isCamCapNotified;

    private void addBusCall(Bus bus, boolean toAdd) {
        if (toAdd) {
            // Signal bus
            this.gstCore.connectBus(bus, this.busWarnListener);
            this.gstCore.connectBus(bus, this.busErrListener);
            this.gstCore.connectBus(bus, this.busEosListener);
        } else {
            // Unsignal bus
            this.gstCore.disconnectBus(bus, this.busWarnListener);
            this.gstCore.disconnectBus(bus, this.busErrListener);
            this.gstCore.disconnectBus(bus, this.busEosListener);
        }
    }

    /**
     * VideoRecorderBase constructor.
     *
     * @param dao Gst API data access object
     * @param statusCallback a callback is used to receive notifications of status.
     */
    public VideoRecorderBase(GstDao dao, StatusCallback statusCallback) {
        this.recorderName =
                String.format("Recorder%d", VideoRecorderBase.recorderSeqNum.getAndIncrement());
        this.condLock = new ReentrantLock();
        this.stopRunningLoop = this.condLock.newCondition();
        this.gstCore = dao;
        this.currStatus = new AtomicReference<RecorderStatus>(RecorderStatus.STOPPED);
        this.statusCallback = statusCallback;
        this.videoDuplicator = new ArrayList<>();
        this.audioDuplicator = new ArrayList<>();
        this.cameraVideoCnt = new AtomicInteger(0);
        this.cameraAudioCnt = new AtomicInteger(0);
        this.branchPool = new HashMap<>();
        this.isCamCapSetAlready = new AtomicBoolean(false);
        this.isCamCapNotified = new AtomicBoolean(false);

        this.busWarnListener = (gstObject, i, s) -> log.warn("{} receives GST bus WARN: {} {} {}.",
                this.recorderName, i, s, gstObject);
        this.busErrListener = (source, code, message) -> {
            log.error("{} receives GST bus ERROR from {}. Code {}: {}.", this.recorderName, source,
                    code, message);
            this.willStop(false, message);
        };
        this.busEosListener = source -> {
            log.info("{} receives GST bus EOS : {}.", this.recorderName, source);
            this.willStop(true, "End of stream (EOS)");
        };

        log.debug("RecorderBase constructs.");
        this.gstCore.initContext();

        // Pipeline
        this.pipeline = this.gstCore.newPipeline();
        this.bus = this.gstCore.getPipelineBus(this.pipeline);
    }

    /**
     * This method is to start recording, and the current thread is blocking until recording is
     * stopped or failed.
     */
    public void start() {
        if (this.getStatus() == RecorderStatus.STOPPED) {
            log.info("{} starts.", this.recorderName);

            // Add bus watch
            this.addBusCall(this.bus, true);

            // Setup camera for binding
            if (this.cameraSource != null) {
                this.cameraSource.onBind();
            }

            this.gstCore.playElement(this.pipeline);
            this.setStatus(RecorderStatus.STARTED, "Recording starts");

            this.condLock.lock();
            try {
                while (this.getStatus() == RecorderStatus.STARTED) {
                    this.stopRunningLoop.await();
                }
            } catch (Exception e) {
                log.error("{} start fails: {}", this.recorderName, e.getMessage());
                this.setStatus(RecorderStatus.STOPPING_ABNORMAL,
                        String.format("start fails: %s", e.getMessage()));
            } finally {
                this.condLock.unlock();
            }

            log.info("{} loop leaved.", this.recorderName);

            // Remove bus watch
            this.addBusCall(this.bus, false);

            // unbind branches and remove duplicators
            this.unbindAllBranches();

            // unbind camera
            this.unbindCamera(RecorderCapability.AUDIO_ONLY);
            this.unbindCamera(RecorderCapability.VIDEO_ONLY);
            if (cameraSource != null) {
                cameraSource.onUnbind();
                this.isCamCapNotified.set(false);
            }

            this.gstCore.stopElement(this.pipeline);

            if (this.getStatus() == RecorderStatus.STOPPING_NORMAL) {
                this.setStatus(RecorderStatus.STOPPED, "Recording Stopped normally");
            } else {
                this.setStatus(RecorderStatus.FAILED, "Recording Stopped by failure");
            }
        } else {
            log.warn("{} starts failed because current state is {}.", this.recorderName,
                    this.getStatus());
        }
    }

    /**
     * This method is to stop recording.
     */
    public void stop() {
        log.info("{} is asked to stop.", this.recorderName);
        this.willStop(true, "Recorder is asked to stop.");
    }

    /**
     * Get registered branch by name.
     *
     * @param branchName branch name
     * @return branch instance
     */
    public RecorderBranchBase getBranch(String branchName) {
        RecorderBranchBase branch = null;

        if (!this.branchPool.containsKey(branchName)) {
            log.warn(String.format("{} cannot find branch %s to bind.", this.recorderName,
                    branchName));
        } else {
            branch = this.branchPool.get(branchName);
        }

        return branch;
    }

    /**
     * Bind a branch to the recorder.
     *
     * @param name branch name
     * @return true if success
     */
    public synchronized boolean bindBranch(String name) {
        boolean ret = true;
        RecorderBranchBase branch = this.getBranch(name);

        if (branch != null) {
            log.info("{} is going to bind branch {}.", this.recorderName, name);
            branch.setAutoBind(true);
            if (this.isCamCapSetAlready.get()) {
                this.bindBranchToTees(branch);
            } else {
                log.info("{} will bind branch {} after getting camera capability.",
                        this.recorderName, name);
            }
        } else {
            log.error("{} cannot find branch {} to bind.", this.recorderName, name);
            ret = false;
        }

        return ret;
    }

    /**
     * Unbind a brnach from the recorder.
     *
     * @param name branch name
     * @return true if success
     */
    public synchronized boolean unbindBranch(String name) {
        boolean ret = true;
        RecorderBranchBase branch = this.getBranch(name);

        if (branch != null) {
            log.info("{} is unbinding branch {}.", this.recorderName, name);
            branch.setAutoBind(false);
            branch.unbind();
        } else {
            log.error("{} cannot find branch {} to unbind.", this.recorderName, name);
            ret = false;
        }

        return ret;
    }

    /**
     * Get current status of this recorder.
     *
     * @return recording status
     */
    public RecorderStatus getStatus() {
        return this.currStatus.get();
    }

    /**
     * Add a camera module to the recorder.
     *
     * @param cameraSrc camera module
     * @return true if success
     */
    public boolean addCameraSource(@NonNull RecorderCameraBase cameraSrc) {
        if (this.cameraSource != null) {
            log.error("{} cannot add new camera because cameras are added.", this.recorderName);
            return false;
        }

        // Camera capability listener
        this.cameraCapListener = (audioCnt, videoCnt) -> {
            if (this.isCamCapNotified.compareAndSet(false, true)) {
                log.info("{} camera capability is notified: audio {} video {}.", this.recorderName,
                        audioCnt, videoCnt);
                this.bindAutoBranches(audioCnt, videoCnt);
            } else {
                log.warn("{} camera capability is notified but already notified.",
                        this.recorderName);
            }
        };

        // Camera pad added listener
        this.cameraPadListener = (cap, newPad) -> {
            this.bindCamera(cap, newPad);
        };

        // Camera error listener
        this.cameraErrListener = (description) -> {
            this.willStop(false, "Camera error: " + description);
        };

        this.cameraSource = cameraSrc;
        this.cameraSource.registerListener(this.cameraCapListener, this.cameraPadListener,
                this.cameraErrListener);

        return true;
    }

    /**
     * Set properties to a registered camera module.
     *
     * @param property camera property
     * @param data value
     * @return true if success
     */
    public boolean setCameraProperty(String property, Object data) {
        boolean result = false;

        if (this.cameraSource != null) {
            try {
                this.cameraSource.setProperty(property, data);
                result = true;
            } catch (IllegalArgumentException e) {
                log.error("SetCamera fails because property is invalid.");
            }
        } else {
            log.error("SetProperty fails because camera is not registered.");
        }

        return result;
    }

    /**
     * Add a branch module to the recorder.
     *
     * @param branch branch module
     * @param name unique branch name
     * @return true if success
     */
    public boolean addBranch(RecorderBranchBase branch, String name, boolean autoBind) {
        if (this.branchPool.containsKey(name)) {
            log.warn("{} cannot add branch {} because it is already added.", this.recorderName,
                    name);
            return false;
        }

        branch.setAutoBind(autoBind);
        this.branchPool.put(name, branch);

        return true;
    }

    private void bindBranchToTees(RecorderBranchBase branch) {
        RecorderCapability cap = branch.getCapability();
        ArrayList<Element> videoTees = null, audioTees = null;

        if (cap == RecorderCapability.VIDEO_AUDIO || cap == RecorderCapability.VIDEO_ONLY) {
            videoTees = videoDuplicator;
        }

        if (cap == RecorderCapability.VIDEO_AUDIO || cap == RecorderCapability.AUDIO_ONLY) {
            audioTees = audioDuplicator;
        }

        branch.bind(videoTees, audioTees);
    }

    private void createTees(int numTrack, ArrayList<Element> teeArray) {
        for (int i = 0; i < numTrack; ++i) {
            Element tee = gstCore.newElement("tee");

            this.gstCore.setElement(tee, "allow-not-linked", true);
            this.gstCore.addPipelineElements(this.pipeline, tee);
            this.gstCore.playElement(tee);
            teeArray.add(tee);
        }
    }

    private void releaseTees(ArrayList<Element> teeArray) {
        for (int i = 0; i < teeArray.size(); ++i) {
            Element tee = teeArray.get(i);

            this.gstCore.stopElement(tee);
            this.gstCore.removePipelineElements(this.pipeline, tee);
        }

        teeArray.clear();
    }

    private synchronized void bindAutoBranches(int audioCnt, int videoCnt) {
        // Prepare video capability, and we support only 1 video track.
        this.createTees(Math.min(1, videoCnt), this.videoDuplicator);

        // Prepare audio capability
        this.createTees(audioCnt, this.audioDuplicator);

        for (Map.Entry<String, RecorderBranchBase> branchEntry : this.branchPool.entrySet()) {
            RecorderBranchBase branch = branchEntry.getValue();

            // Only those branches with autoBind flag are bound
            if (branch.isAutoBind()) {
                this.bindBranchToTees(branch);
            }
        }

        this.isCamCapSetAlready.set(true);
    }

    private synchronized void unbindAllBranches() {
        this.isCamCapSetAlready.set(false);

        // unbind branches
        for (Map.Entry<String, RecorderBranchBase> branchEntry : this.branchPool.entrySet()) {
            RecorderBranchBase branch = branchEntry.getValue();
            branch.unbind();
        }
    }

    private void bindCamera(@NonNull RecorderCapability cap, Pad newPad) {
        Element teeElm = null;
        Pad teeSink = null;
        String errorMsg = null;

        synchronized (this) {
            switch (cap) {
                case AUDIO_ONLY:
                    if (this.cameraAudioCnt.get() < this.audioDuplicator.size()) {
                        teeElm = this.audioDuplicator.get(this.cameraAudioCnt.getAndIncrement());
                    } else {
                        log.error("{} gets audio tee out of range.", this.recorderName);
                        errorMsg = "Get audio tee out of range";
                    }
                    break;

                case VIDEO_ONLY:
                    if (this.cameraVideoCnt.get() < this.videoDuplicator.size()) {
                        teeElm = this.videoDuplicator.get(this.cameraVideoCnt.getAndIncrement());
                    } else {
                        log.error("{} gets video tee out of range.", this.recorderName);
                        errorMsg = "Get video tee out of range";
                    }
                    break;

                default:
                    log.error("{} receives invalid capability from camera.", this.recorderName);
                    errorMsg = "Invalid camera capability";
            }

            if (errorMsg == null) {
                teeSink = this.gstCore.getElementStaticPad(teeElm, "sink");

                if (this.gstCore.isPadLinked(teeSink)) {
                    log.warn("{} camera and tee shouldn't be linked already.", this.recorderName);
                    Pad camPad = this.gstCore.getPadPeer(teeSink);
                    if (this.gstCore.unlinkPad(camPad, teeSink)) {
                        log.info("{} camera and tee unlinked.", this.recorderName);
                    } else {
                        log.error("{} camera and tee unlink failed.", this.recorderName);
                    }
                }

                try {
                    this.gstCore.linkPad(newPad, teeSink);
                    log.info("{} camera and tee linked.", this.recorderName);
                } catch (PadLinkException ex) {
                    log.error("{} camera and tee link failed: {}", this.recorderName,
                            ex.getLinkResult());
                    errorMsg = "Camera and recorder tee link failed: " + ex.getLinkResult();
                }
            }
        }

        if (errorMsg != null) {
            this.willStop(false, errorMsg);
        }
    }

    private void unbindCamera(RecorderCapability cap) {
        ArrayList<Element> duplicator;
        AtomicInteger trackCnt;

        if (cap == RecorderCapability.AUDIO_ONLY) {
            duplicator = this.audioDuplicator;
            trackCnt = this.cameraAudioCnt;
        } else {
            duplicator = this.videoDuplicator;
            trackCnt = this.cameraVideoCnt;
        }

        for (int i = 0; i < duplicator.size(); ++i) {
            Element teeElm = duplicator.get(i);
            Pad teeSink = this.gstCore.getElementStaticPad(teeElm, "sink");

            if (this.gstCore.isPadLinked(teeSink)) {
                Pad camPad = this.gstCore.getPadPeer(teeSink);

                if (this.gstCore.unlinkPad(camPad, teeSink)) {
                    log.info("{} camera and tee unlinked.", this.recorderName);
                } else {
                    log.error("{} camera and tee unlink failed.", this.recorderName);
                }
            }

            if (0 >= trackCnt.getAndDecrement()) {
                log.error("{} gets trackCnt out of range when unbind camera.", this.recorderName);
            }
        }

        this.releaseTees(duplicator);
    }

    private void setStatus(RecorderStatus st, String description) {
        RecorderStatus oldSt = this.currStatus.getAndSet(st);

        if (oldSt != st) {
            this.statusCallback.notifyStatus(this, st, description);
        }
    }

    protected synchronized void willStop(boolean isNormalCase, String description) {
        boolean isStopping = false;
        RecorderStatus nextSt = RecorderStatus.STOPPED;
        RecorderStatus currSt = this.getStatus();

        if (currSt == RecorderStatus.FAILED) {
            if (!isNormalCase) {
                nextSt = RecorderStatus.FAILED;
            }
        } else if (currSt == RecorderStatus.STARTED) {
            if (isNormalCase) {
                nextSt = RecorderStatus.STOPPING_NORMAL;
            } else {
                nextSt = RecorderStatus.STOPPING_ABNORMAL;
            }
            isStopping = true;
        } else {
            log.info("{} skips willStop because it is stopping or stopped.", this.recorderName);
            return;
        }

        log.info("{} is willing to stop: currSt {}, nextSt {}, and isNormal {}.", this.recorderName,
                currSt, nextSt, isNormalCase);

        this.setStatus(nextSt, description);

        if (isStopping) {
            this.condLock.lock();
            try {
                this.stopRunningLoop.signal();
            } finally {
                this.condLock.unlock();
            }
        }
    }
}
