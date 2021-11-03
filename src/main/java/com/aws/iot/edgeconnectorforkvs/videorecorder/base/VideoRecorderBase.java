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

package com.aws.iot.edgeconnectorforkvs.videorecorder.base;

import com.aws.iot.edgeconnectorforkvs.videorecorder.callback.StatusCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderStatus;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    // Synchronization
    private ReadWriteLock rwLock;
    private Lock condLock;
    private Condition stopRunningLoop;

    // Dao
    @Getter
    private GstDao gstCore;

    // Recorder basics
    private RecorderStatus currStatus;
    private StatusCallback statusCallback;

    // GStreamer basics
    private Bus bus;
    @Getter
    private Pipeline pipeline;

    // Camera
    private RecorderCameraBase cameraSource;
    private RecorderCameraBase.CapabilityListener cameraCapListener;
    private RecorderCameraBase.NewPadListener cameraPadListener;
    private RecorderCameraBase.ErrorListener cameraErrListener;

    // Tee
    private ArrayList<Element> teeVideos;
    private ArrayList<Element> teeAudios;
    private AtomicInteger teeVideoIdx;
    private AtomicInteger teeAudioIdx;

    // Branches
    private HashMap<String, RecorderBranchBase> branches;

    /**
     * VideoRecorderBase constructor.
     *
     * @param dao Gst API data access object
     * @param statusCallback a callback is used to receive notifications of status.
     */
    public VideoRecorderBase(GstDao dao, StatusCallback statusCallback) {
        this.rwLock = new ReentrantReadWriteLock();
        this.condLock = new ReentrantLock();
        this.stopRunningLoop = this.condLock.newCondition();
        this.gstCore = dao;
        this.currStatus = RecorderStatus.STOPPED;
        this.statusCallback = statusCallback;
        this.teeVideos = new ArrayList<>();
        this.teeAudios = new ArrayList<>();
        this.teeVideoIdx = new AtomicInteger(0);
        this.teeAudioIdx = new AtomicInteger(0);
        this.branches = new HashMap<>();

        this.gstCore.initContext();

        // Pipeline
        this.pipeline = this.gstCore.newPipeline();
        this.bus = this.gstCore.getPipelineBus(this.pipeline);

        // Signal bus
        this.gstCore.connectBus(this.bus, (Bus.WARNING) (gstObject, i, s) -> log
                .warn("WARN " + i + " " + s + " " + gstObject));

        this.gstCore.connectBus(this.bus, (Bus.ERROR) (source, code, message) -> {
            log.error("ERROR " + code + " " + message + " " + source);
            this.willStopRecording(false, message);
        });

        this.gstCore.connectBus(this.bus, (Bus.EOS) source -> {
            log.info("EOS " + source);
            this.willStopRecording(true, "End of stream (EOS)");
        });
    }

    /**
     * This method is to start recording, and the current thread is blocking until recording is
     * stopped or failed.
     */
    public void startRecording() {
        if (this.getStatus() == RecorderStatus.STOPPED
                || this.getStatus() == RecorderStatus.FAILED) {
            this.teeVideoIdx.set(0);
            this.teeAudioIdx.set(0);
            this.gstCore.playElement(this.pipeline);
            this.setStatus(RecorderStatus.STARTED, "Recording starts");

            this.condLock.lock();
            try {
                while (this.getStatus() == RecorderStatus.STARTED) {
                    this.stopRunningLoop.await();
                }
            } catch (Exception e) {
                log.error(String.format("startRecording fails: %s", e.getMessage()));
                this.setStatus(RecorderStatus.STOPPING_ABNORMAL,
                        String.format("startRecording fails: %s", e.getMessage()));
            } finally {
                this.condLock.unlock();
            }

            log.info("Leave recording loop");
            this.gstCore.stopElement(this.pipeline);
            log.info("notify stop");

            if (this.getStatus() == RecorderStatus.STOPPING_ABNORMAL) {
                this.setStatus(RecorderStatus.FAILED, "Recording Stopped by failure");
            } else {
                this.setStatus(RecorderStatus.STOPPED, "Recording Stopped normally");
            }
        } else {
            log.warn("Cannot start recording because current state is " + this.getStatus());
        }
    }

    /**
     * This method is to stop recording.
     */
    public void stopRecording() {
        this.gstCore.sendElementEvent(this.pipeline, this.gstCore.newEosEvent());
        this.gstCore.postBusMessage(this.bus, this.gstCore.newEosMessage(this.pipeline));
    }

    /**
     * Get registered branch by name.
     *
     * @param branchName branch name
     * @return branch instance
     */
    public RecorderBranchBase getBranch(String branchName) {
        RecorderBranchBase branch = null;

        if (!this.branches.containsKey(branchName)) {
            log.warn(String.format("Branch %s doesn't exist.", branchName));
        } else {
            branch = this.branches.get(branchName);
        }

        return branch;
    }

    /**
     * Get current status of this recorder.
     *
     * @return recording status
     */
    public RecorderStatus getStatus() {
        this.rwLock.readLock().lock();
        try {
            return this.currStatus;
        } finally {
            this.rwLock.readLock().unlock();
        }
    }

    /**
     * Register a camera module to the recorder.
     *
     * @param cameraSrc camera module
     * @return true if success
     */
    public boolean registerCamera(@NonNull RecorderCameraBase cameraSrc) {
        if (this.cameraSource != null) {
            log.error("Camera is already registered.");
            return false;
        }

        // Camera capability listener
        this.cameraCapListener = (audioCnt, videoCnt) -> {
            this.bindBranches(audioCnt, videoCnt);
        };

        // Camera pad added listener
        this.cameraPadListener = (cap, newPad) -> {
            this.bindCameraToTee(cap, newPad);
        };

        // Camera error listener
        this.cameraErrListener = (description) -> {
            this.willStopRecording(false, "Camera error: " + description);
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
     * Register a branch module to the recorder.
     *
     * @param branch branch module
     * @param name unique branch name
     * @return true if success
     */
    public boolean registerBranch(RecorderBranchBase branch, String name) {
        if (this.branches.containsKey(name)) {
            log.warn("Branch is already registered: " + name);
            return false;
        }

        this.branches.put(name, branch);

        return true;
    }

    private void bindBranchToTees(boolean bindVideo, boolean bindAudio) {
        for (Map.Entry<String, RecorderBranchBase> branch : this.branches.entrySet()) {
            RecorderCapability branchCap = branch.getValue().getCapability();
            ArrayList<Element> videoArr = null;
            ArrayList<Element> audioArr = null;

            // bind teeVideos
            if (bindVideo && (branchCap == RecorderCapability.VIDEO_AUDIO
                    || RecorderCapability.VIDEO_ONLY == branchCap)) {
                videoArr = this.teeVideos;
            }
            // bind teeAudios
            if (bindAudio && (branchCap == RecorderCapability.VIDEO_AUDIO
                    || RecorderCapability.AUDIO_ONLY == branchCap)) {
                audioArr = this.teeAudios;
            }

            branch.getValue().bindPaths(videoArr, audioArr);
        }
    }

    private void createTees(int numTrack, ArrayList<Element> teeArray) {
        for (int i = 0; i < numTrack; ++i) {
            Element tee = gstCore.newElement("tee");
            this.gstCore.setElement(tee, "allow-not-linked", true);
            this.gstCore.addPipelineElements(this.pipeline, tee);
            this.gstCore.syncElementParentState(tee);
            teeArray.add(tee);
        }
    }

    private void bindBranches(int audioCnt, int videoCnt) {
        boolean bindVideo = true;
        boolean bindAudio = true;

        // Prepare video capability
        if (this.teeVideos.size() > 0) {
            log.info("Ignore binding video pads because branches are already linked.");
            bindVideo = false;
        } else {
            // We support only 1 video track.
            videoCnt = Math.min(1, videoCnt);
            createTees(videoCnt, this.teeVideos);
        }

        // Prepare audio capability
        if (this.teeAudios.size() > 0) {
            log.info("Ignore binding audio pads because branches are already linked.");
            bindAudio = false;
        } else {
            createTees(audioCnt, this.teeAudios);
        }

        this.bindBranchToTees(bindVideo, bindAudio);
    }

    private void bindCameraToTee(@NonNull RecorderCapability cap, Pad newPad) {
        Element teeElm = null;

        switch (cap) {
            case AUDIO_ONLY:
                if (this.teeAudioIdx.get() < this.teeAudios.size()) {
                    teeElm = this.teeAudios.get(this.teeAudioIdx.getAndIncrement());
                } else {
                    log.error("Get audio tee out of range");
                    this.willStopRecording(false, "Get audio tee out of range");
                    return;
                }
                break;

            case VIDEO_ONLY:
                if (this.teeVideoIdx.get() < this.teeVideos.size()) {
                    teeElm = this.teeVideos.get(this.teeVideoIdx.getAndIncrement());
                } else {
                    log.error("Get video tee out of range");
                    this.willStopRecording(false, "Get video tee out of range");
                    return;
                }
                break;

            default:
                throw new RejectedExecutionException("Invalid capability from RecorderCamera");
        }

        Pad teeSink = this.gstCore.getElementStaticPad(teeElm, "sink");

        if (this.gstCore.isPadLinked(teeSink)) {
            log.info("Unbind camera and tee before relinking.");
            Pad camPad = this.gstCore.getPadPeer(teeSink);
            this.gstCore.unlinkPad(camPad, teeSink);
        }
        try {
            this.gstCore.linkPad(newPad, teeSink);
            log.debug("Camera and tee linked.");
        } catch (PadLinkException ex) {
            log.error("Camera and tee link failed: " + ex.getLinkResult());
            this.willStopRecording(false, "Camera and tee link failed: " + ex.getLinkResult());
        }
    }

    private void setStatus(RecorderStatus st, String description) {
        boolean needNotify = false;

        this.rwLock.writeLock().lock();
        if (this.currStatus != st) {
            this.currStatus = st;
            needNotify = true;
        }
        this.rwLock.writeLock().unlock();

        if (needNotify) {
            this.statusCallback.notifyStatus(this, st, description);
        }
    }

    private void willStopRecording(boolean isNormalCase, String description) {
        if (this.getStatus() == RecorderStatus.FAILED) {
            this.setStatus(RecorderStatus.STOPPED, description);
        } else if (this.getStatus() != RecorderStatus.STOPPED) {
            if (isNormalCase) {
                this.setStatus(RecorderStatus.STOPPING_NORMAL, description);
            } else {
                this.setStatus(RecorderStatus.STOPPING_ABNORMAL, description);
            }
            this.condLock.lock();
            try {
                this.stopRunningLoop.signal();
            } finally {
                this.condLock.unlock();
            }
        }
    }
}
