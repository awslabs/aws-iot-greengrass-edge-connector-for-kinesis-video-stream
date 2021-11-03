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

import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

/**
 * Recorder camera base class.
 */
@Getter(AccessLevel.PROTECTED)
public abstract class RecorderCameraBase {
    private GstDao gstCore;
    private Pipeline pipeline;
    private CapabilityListener capListener;
    private NewPadListener padListener;
    private ErrorListener errListener;

    /**
     * Set properties for the GStreamer element of the camera.
     *
     * @param property GStreamer property
     * @param data Value
     * @throws IllegalArgumentException if invalid property or data
     */
    public abstract void setProperty(String property, Object data) throws IllegalArgumentException;

    /**
     * The listener is used to notify recorder module of camera capability after playing.
     */
    public interface CapabilityListener {
        /**
         * Notify the recorder module for audio/video capability of this camera.
         *
         * @param audioCnt number of audio tracks
         * @param videoCnt number of video tracks
         */
        void onNotify(int audioCnt, int videoCnt);
    }

    /**
     * The listener is used to notify recorder module of new Pads after playing. It should be
     * invoked after notifying capability to recorder module.
     */
    public interface NewPadListener {
        /**
         * Listener.
         *
         * @param cap pad capability
         * @param newPad pad added
         */
        void onNotify(RecorderCapability cap, Pad newPad);
    }

    /**
     * The listener is used to notify recorder module of errors.
     */
    public interface ErrorListener {
        /**
         * Listener.
         *
         * @param description error message
         */
        void onError(String description);
    }

    /**
     * Recorder camera base class constructor.
     *
     * @param dao GStreamer data access object
     * @param pipeline GStreamer pipeline
     */
    public RecorderCameraBase(GstDao dao, Pipeline pipeline) {
        this.gstCore = dao;
        this.pipeline = pipeline;
    }

    /**
     * Register event listeners to camera module.
     *
     * @param capListener capability listener
     * @param padListener pn pad added listener
     * @param errListener on error listener
     */
    public void registerListener(@NonNull CapabilityListener capListener,
            @NonNull NewPadListener padListener, @NonNull ErrorListener errListener) {
        this.capListener = capListener;
        this.padListener = padListener;
        this.errListener = errListener;
    }
}
