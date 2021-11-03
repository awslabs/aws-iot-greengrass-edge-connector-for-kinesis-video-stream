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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.ConfigMuxer;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.MuxerProperty;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Recorder pipeline branch base class.
 */
@Slf4j
public abstract class RecorderBranchBase {
    @Getter
    private RecorderCapability capability;
    @Getter(AccessLevel.PROTECTED)
    private GstDao gstCore;
    @Getter(AccessLevel.PROTECTED)
    private Pipeline pipeline;
    private HashMap<Element, Element> teeSrc2Que;
    private HashMap<Pad, Element> teeSrcPad2Tee;
    @Getter(AccessLevel.PROTECTED)
    private Pad.PROBE teeBlockProbe;
    private Lock condLock;
    private Condition padProbeUnlink;
    private AtomicInteger detachCnt;
    private Lock bindLock;
    private AtomicBoolean attachExplicitly;
    private AtomicBoolean branchAttached;

    /**
     * Create and get the entry pad of audio path.
     *
     * @return audio entry pad
     */
    public abstract Pad getEntryAudioPad();

    /**
     * Create and get the entry pad of video path.
     *
     * @return video entry pad
     */
    public abstract Pad getEntryVideoPad();

    /**
     * Constructor for RecorderBranchBase.
     *
     * @param cap branch capability
     * @param dao GStreamer data access object
     * @param pipeline GStreamer pipeline
     */
    public RecorderBranchBase(RecorderCapability cap, GstDao dao, Pipeline pipeline) {
        this.capability = cap;
        this.gstCore = dao;
        this.pipeline = pipeline;
        this.teeSrc2Que = new HashMap<>();
        this.teeSrcPad2Tee = new HashMap<>();
        this.condLock = new ReentrantLock();
        this.padProbeUnlink = this.condLock.newCondition();
        this.detachCnt = new AtomicInteger(0);
        this.bindLock = new ReentrantLock();
        this.branchAttached = new AtomicBoolean();
        this.attachExplicitly = new AtomicBoolean();

        this.teeBlockProbe = (teePadSrc, info) -> {
            Pad quePadSink = this.gstCore.getPadPeer(teePadSrc);

            if (this.gstCore.isPadLinked(teePadSrc)) {
                this.gstCore.unlinkPad(teePadSrc, quePadSink);
                log.info("queue is detached");
            }

            this.gstCore.sendPadEvent(quePadSink, this.gstCore.newEosEvent());

            if (this.detachCnt.incrementAndGet() == this.teeSrc2Que.size()) {
                this.condLock.lock();
                try {
                    this.padProbeUnlink.signal();
                } finally {
                    this.condLock.unlock();
                }
            }

            return PadProbeReturn.REMOVE;
        };
    }

    /**
     * Check if the branch will be attached explicitly.
     * @return true if user triggers attachment
     */
    public boolean isAttachedExplicitly() {
        return this.attachExplicitly.get();
    }

    /**
     * Check if the branch is attached.
     * @return true if branch is attached
     */
    public boolean isBranchAttached() {
        return this.branchAttached.get();
    }

    /**
     * Link a given Pad to this branch.
     *
     * @param recorderElmSrc an element of recorder is going to link to this branch
     * @param capsToBind selection for linking video or audio pad of this branch
     */
    private void bindPath(Element recorderElmSrc, RecorderCapability capsToBind)
            throws IllegalArgumentException {
        Pad entryPadSink = null;

        // Only a video or a audio pad can be bound at each request
        if (capsToBind == RecorderCapability.AUDIO_ONLY) {
            if (this.capability == RecorderCapability.AUDIO_ONLY
                    || this.capability == RecorderCapability.VIDEO_AUDIO) {
                entryPadSink = this.getEntryAudioPad();
            } else {
                log.warn("Not supported capability to bind branch: " + capsToBind);
            }
        }
        if (capsToBind == RecorderCapability.VIDEO_ONLY) {
            if (this.capability == RecorderCapability.VIDEO_ONLY
                    || this.capability == RecorderCapability.VIDEO_AUDIO) {
                entryPadSink = this.getEntryVideoPad();
            } else {
                log.warn("Not supported capability to bind branch: " + capsToBind);
            }
        }

        if (entryPadSink != null) {
            Pad recorderSrcPad = this.gstCore.getElementRequestPad(recorderElmSrc, "src_%u");
            Element queueElm = this.gstCore.newElement("queue");
            Pad quePadSrc = this.gstCore.getElementStaticPad(queueElm, "src");
            Pad quePadSink = this.gstCore.getElementStaticPad(queueElm, "sink");

            this.gstCore.setElement(queueElm, "flush-on-eos", true);
            this.gstCore.setElement(queueElm, "leaky", 2);

            // Link elements
            this.gstCore.addPipelineElements(this.pipeline, queueElm);
            this.gstCore.linkPad(recorderSrcPad, quePadSink);
            this.gstCore.linkPad(quePadSrc, entryPadSink);

            // Add to hash map
            this.teeSrc2Que.put(recorderElmSrc, queueElm);
            this.teeSrcPad2Tee.put(recorderSrcPad, recorderElmSrc);

            this.gstCore.syncElementParentState(queueElm);
        }
    }

    /**
     * Link a given tees to this branch.
     *
     * @param teeVideos video tees of recorder are going to link to this branch
     * @param teeAudios audio tees of recorder are going to link to this branch
     */
    public void bindPaths(ArrayList<Element> teeVideos, ArrayList<Element> teeAudios) {
        this.bindLock.lock();
        try {
            // bind teeVideos
            if (teeVideos != null) {
                for (int i = 0; i < teeVideos.size(); ++i) {
                    this.bindPath(teeVideos.get(i), RecorderCapability.VIDEO_ONLY);
                }
            }

            // bind teeAudios
            if (teeAudios != null) {
                for (int i = 0; i < teeAudios.size(); ++i) {
                    this.bindPath(teeAudios.get(i), RecorderCapability.AUDIO_ONLY);
                }
            }

            // set branch attached
            this.branchAttached.set(true);
        } finally {
            this.bindLock.unlock();
        }
    }

    protected void detach() {
        this.bindLock.lock();
        try {
            this.detachCnt.set(0);

            for (Map.Entry<Element, Element> queue : this.teeSrc2Que.entrySet()) {
                Element queueElm = queue.getValue();
                Pad quePadSink = this.gstCore.getElementStaticPad(queueElm, "sink");
                Pad teePadSrc = this.gstCore.getPadPeer(quePadSink);
                this.gstCore.addPadProbe(teePadSrc, PadProbeType.IDLE, this.teeBlockProbe);
            }

            log.info("waiting for queues detaching");
            this.condLock.lock();
            try {
                while (this.detachCnt.get() != this.teeSrc2Que.size()) {
                    this.padProbeUnlink.await();
                }
            } catch (Exception e) {
                log.error(String.format("detach fails: %s", e.getMessage()));
            } finally {
                this.condLock.unlock();
            }
            log.info("all queues are detached");

            // release tee pads
            for (Map.Entry<Pad, Element> pads : this.teeSrcPad2Tee.entrySet()) {
                Element teeSrc = pads.getValue();
                Pad teePadSrc = pads.getKey();
                this.gstCore.relElementRequestPad(teeSrc, teePadSrc);
            }
            this.teeSrcPad2Tee.clear();

            this.branchAttached.set(false);
        } finally {
            this.bindLock.unlock();
        }
    }

    protected void attach() {
        this.bindLock.lock();
        try {
            this.attachExplicitly.set(true);

            for (Map.Entry<Element, Element> queue : this.teeSrc2Que.entrySet()) {
                Element que = queue.getValue();
                Element recorderElmSrc = queue.getKey();

                if (!this.gstCore.isPadLinked(this.gstCore.getElementStaticPad(que, "sink"))) {
                    Pad newSrcPad = this.gstCore.getElementRequestPad(recorderElmSrc, "src_%u");

                    this.teeSrcPad2Tee.put(newSrcPad, recorderElmSrc);

                    this.gstCore.linkPad(newSrcPad, this.gstCore.getElementStaticPad(que, "sink"));
                    this.gstCore.syncElementParentState(que);
                    log.info("teePadSrc is linked with que");
                } else {
                    log.warn("tee and que are already linked");
                }

                this.branchAttached.set(true);
            }
        } finally {
            this.bindLock.unlock();
        }
    }

    /**
     * Helper function to create a new muxer by the given type.
     *
     * @param type container type
     * @param isFilePath selection muxer properties for file path or app path
     * @return a muxer element
     * @throws IllegalArgumentException if type is not supported
     */
    protected Element getMuxerFromType(ContainerType type, boolean isFilePath)
            throws IllegalArgumentException {

        Element muxer = null;

        if (ConfigMuxer.CONTAINER_INFO.containsKey(type)) {
            MuxerProperty conf = ConfigMuxer.CONTAINER_INFO.get(type);
            ArrayList<HashMap<String, Object>> propList = new ArrayList<>();

            muxer = this.gstCore.newElement(conf.getGstElmName());

            propList.add(conf.getGeneralProp());
            if (isFilePath) {
                propList.add(conf.getFilePathProp());
            } else {
                propList.add(conf.getAppPathProp());
            }

            for (HashMap<String, Object> properties : propList) {
                for (Map.Entry<String, Object> property : properties.entrySet()) {
                    this.gstCore.setElement(muxer, property.getKey(), property.getValue());
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported muxer container type: " + type);
        }

        return muxer;
    }

    /**
     * Helper function to extension name by the given type.
     *
     * @param type container type
     * @return file extension name
     * @throws IllegalArgumentException if type is not supported
     */
    protected String getFileExtensionFromType(ContainerType type) throws IllegalArgumentException {
        if (ConfigMuxer.CONTAINER_INFO.containsKey(type)) {
            return ConfigMuxer.CONTAINER_INFO.get(type).getFileExt();
        } else {
            throw new IllegalArgumentException("Unsupported extension container type: " + type);
        }
    }
}
