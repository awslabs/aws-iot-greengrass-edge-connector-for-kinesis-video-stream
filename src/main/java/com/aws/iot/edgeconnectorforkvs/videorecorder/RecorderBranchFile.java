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

import java.time.Instant;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderBranchBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.Config;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import com.sun.jna.Pointer;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.lowlevel.GPointer;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class RecorderBranchFile extends RecorderBranchBase {
    private Element muxer;
    private Element splitMuxSink;
    private GstDao gstCore;
    private Pipeline pipeline;

    interface LocCallback extends GstCallback {
        Pointer callback(Element splitmux, long fragmentId, GPointer uData);
    }

    RecorderBranchFile(ContainerType type, GstDao dao, Pipeline pipeline, String filePath) {
        super(Config.FILE_PATH_CAPABILITY, dao, pipeline);
        this.gstCore = this.getGstCore();
        this.pipeline = this.getPipeline();

        // Split mux elements
        String fileExtension = this.getFileExtensionFromType(type);
        this.muxer = this.getMuxerFromType(type, true);
        this.splitMuxSink = this.gstCore.newElement("splitmuxsink");
        this.gstCore.setElement(this.splitMuxSink, "muxer", muxer);
        this.gstCore.setAsStringElement(this.splitMuxSink, "location",
                filePath + "." + fileExtension);
        this.gstCore.setElement(this.splitMuxSink, "max-size-time",
                Config.DEFAULT_FILE_ROTATION_IN_NS);
        this.gstCore.setElement(this.splitMuxSink, "send-keyframe-requests", true);

        // add elements
        this.gstCore.addPipelineElements(this.pipeline, this.splitMuxSink);

        // Signals
        this.gstCore.connectElement(this.splitMuxSink, "format-location",
                (LocCallback) (elm, fId, uData) -> {
                    String path = String.format("%s_%d.%s", filePath, Instant.now().toEpochMilli(),
                            fileExtension);
                    log.debug("LocCallback: " + path);
                    return this.gstCore.invokeGLibStrdup(path);
                });
    }

    public boolean setProperty(String property, Object data) {
        boolean result = false;

        try {
            this.gstCore.setElement(this.splitMuxSink, property, data);
            result = true;
        } catch (IllegalArgumentException e) {
            log.error("Set file branch fails because property is invalid.");
        }

        return result;
    }

    @Override
    public Pad getEntryAudioPad() {
        return this.gstCore.getElementRequestPad(this.splitMuxSink, "audio_%u");
    }

    @Override
    public Pad getEntryVideoPad() {
        return this.gstCore.getElementRequestPad(this.splitMuxSink, "video");
    }
}
