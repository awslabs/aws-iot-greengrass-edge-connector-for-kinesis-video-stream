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

package com.aws.iot.edgeconnectorforkvs.videorecorder.module.branch;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderBranchBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.Config;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Data branch for user callback.
 */
@Slf4j
public class RecorderBranchApp extends RecorderBranchBase {
    /**
     * Streaming Notification Callback.
     */
    public interface BranchAppCallback {
        /**
         * Callback for streaming data.
         *
         * @param buff buffer
         */
        void onData(ByteBuffer buff);
    }

    private Element muxer;
    private AppSink appSink;
    private GstDao gstCore;
    private Pipeline pipeline;
    private ContainerType containerType;
    private Lock appNotifyMtx;
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private boolean notificationOn;
    private AppSink.NEW_SAMPLE onNewSample;
    private BranchAppCallback dataNotifier;

    /**
     * RecorderBranchApp constructor.
     *
     * @param type multimedia container type
     * @param dao GStreamer data access object
     * @param pipeline GStreamer pipeline
     */
    public RecorderBranchApp(ContainerType type, GstDao dao, Pipeline pipeline) {
        super(Config.APP_PATH_CAPABILITY, dao, pipeline);
        this.gstCore = this.getGstCore();
        this.pipeline = this.getPipeline();

        this.containerType = type;
        this.appNotifyMtx = new ReentrantLock();
        this.notificationOn = false;
        this.onNewSample = (sink) -> {
            FlowReturn ret = FlowReturn.OK;

            this.appNotifyMtx.lock();
            try {
                if (this.isNotificationOn()) {
                    Sample smp = sink.pullSample();

                    if (smp != null) {
                        ByteBuffer bBuff = smp.getBuffer().map(false);

                        this.dataNotifier.onData(bBuff);

                        smp.getBuffer().unmap();
                        smp.dispose();
                    } else {
                        log.error("AppBranch fails to pull sample.");
                        ret = FlowReturn.ERROR;
                    }
                } else {
                    log.warn("Skip AppBranch data because notification is OFF.");
                }
            } finally {
                this.appNotifyMtx.unlock();
            }

            return ret;
        };
    }

    /**
     * Register callback for new data.
     *
     * @param listener callback
     */
    public void registerNewSample(BranchAppCallback listener) {
        this.dataNotifier = listener;
    }

    @Override
    protected synchronized void onBind() {
        this.appNotifyMtx.lock();
        try {
            this.muxer = this.getMuxerFromType(this.containerType, false);
            this.appSink = (AppSink) this.gstCore.newElement("appsink");

            this.gstCore.setElement(appSink, "emit-signals", true);

            // Signals
            this.gstCore.connectAppSink(this.appSink, this.onNewSample);

            // add elements
            this.gstCore.addPipelineElements(this.pipeline, this.muxer, this.appSink);
            this.gstCore.linkManyElement(this.muxer, this.appSink);

            this.gstCore.playElement(this.muxer);
            this.gstCore.playElement(this.appSink);

            this.setNotificationOn(true);
        } finally {
            this.appNotifyMtx.unlock();
        }
    }

    @Override
    protected synchronized void onUnbind() {
        this.appNotifyMtx.lock();
        try {
            this.setNotificationOn(false);
            this.gstCore.sendElementEvent(this.muxer, this.gstCore.newEosEvent());
            this.gstCore.setElement(appSink, "emit-signals", false);
            this.gstCore.stopElement(this.muxer);
            this.gstCore.stopElement(this.appSink);
            this.gstCore.unlinkElements(this.muxer, this.appSink);
            this.gstCore.removePipelineElements(this.pipeline, this.muxer, this.appSink);
            this.muxer = null;
            this.appSink = null;
        } finally {
            this.appNotifyMtx.unlock();
        }
    }

    @Override
    protected synchronized Pad getEntryAudioPad() {
        log.debug("New AppBranch audio entry pad.");
        return this.gstCore.getElementRequestPad(this.muxer, "audio_%u");
    }

    @Override
    protected synchronized Pad getEntryVideoPad() {
        log.debug("New AppBranch video entry pad.");
        return this.gstCore.getElementRequestPad(this.muxer, "video_%u");
    }

    @Override
    protected synchronized void relEntryAudioPad(Pad pad) {
        log.debug("Rel AppBranch audio entry pad.");
        this.gstCore.relElementRequestPad(this.muxer, pad);
    }

    @Override
    protected synchronized void relEntryVideoPad(Pad pad) {
        log.debug("Rel AppBranch video entry pad.");
        this.gstCore.relElementRequestPad(this.muxer, pad);
    }
}
