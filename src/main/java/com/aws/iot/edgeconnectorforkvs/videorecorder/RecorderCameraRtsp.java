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

import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.RecorderCapability;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.ConfigCodec;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.ConfigRtp;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import org.apache.commons.lang3.StringUtils;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.SDPMessage;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.lowlevel.GPointer;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class RecorderCameraRtsp extends RecorderCameraBase {
    private Element camera;
    private SdpCallback sdpListener;
    private Element.PAD_ADDED padAddListener;
    private GstDao gstCore;
    private Pipeline pipeline;

    interface SdpCallback extends GstCallback {
        void callback(Element rtspElm, SDPMessage sdp, GPointer userData);
    }

    RecorderCameraRtsp(GstDao dao, Pipeline pipeline, String sourceUrl) {

        super(dao, pipeline);
        this.gstCore = this.getGstCore();
        this.pipeline = this.getPipeline();

        this.camera = this.gstCore.newElement("rtspsrc");
        this.gstCore.setAsStringElement(this.camera, "location", sourceUrl);
        this.gstCore.setElement(this.camera, "do-rtcp", true);
        this.gstCore.setElement(this.camera, "latency", 0);
        this.gstCore.setElement(this.camera, "short-header", true);
        this.gstCore.addPipelineElements(this.pipeline, this.camera);

        this.sdpListener = (rtspElm, sdp, userData) -> {
            String msg = sdp.toString();
            int audioCnt = StringUtils.countMatches(msg, "m=audio");
            int videoCnt = StringUtils.countMatches(msg, "m=video");

            log.debug(msg.toString());
            log.debug(String.format("RTSP has %d video and %d audio track(s)", videoCnt, audioCnt));

            if (audioCnt == 0 && videoCnt == 0) {
                log.warn("RTSP camera doesn't contain video and audio on SDP.");
            } else {
                this.getCapListener().onNotify(audioCnt, videoCnt);
            }

            sdp.disown();
        };

        this.padAddListener = (element, newPad) -> {
            Pad sinkPad = buildRtpPath(newPad);

            if (sinkPad != null && !this.gstCore.isPadLinked(sinkPad)) {
                try {
                    this.gstCore.linkPad(newPad, sinkPad);
                    log.debug("RTSP and depay are linked.");
                } catch (PadLinkException ex) {
                    log.error("RTSP and depay link failed : " + ex.getLinkResult());
                    this.getErrListener()
                            .onError("cameraSource pad link failed: " + ex.getLinkResult());
                }
            }
        };

        // Signals
        this.gstCore.connectElement(this.camera, "on-sdp", this.sdpListener);
        this.gstCore.connectElement(this.camera, this.padAddListener);
    }

    @Override
    public void setProperty(String property, Object data) throws IllegalArgumentException {
        this.gstCore.setElement(this.camera, property, data);
    }

    private Pad buildRtpPath(Pad newPad) {
        Pad sinkPad = null;
        Caps padCaps = this.gstCore.getPadCaps(newPad);

        log.debug("rtspsrc pad name/type: " + newPad.getName() + "/" + newPad.getTypeName());

        for (int i = 0; i < padCaps.size(); ++i) {
            Structure capStruct = this.gstCore.getCapsStructure(padCaps, i);
            String padTypeName = this.gstCore.getStructureName(capStruct);

            if (padTypeName.equals("application/x-rtp")) {
                sinkPad = buildRtpDepayPath(capStruct);
                break;
            } else {
                log.debug("Ignore type " + padTypeName + " which is not x-rtp.");
            }
        }

        return sinkPad;
    }

    private Pad buildRtpDepayPath(Structure capStruct) {
        Pad rtpDepaySinkPad = null;
        boolean hasMedia = this.gstCore.hasStructureField(capStruct, "media");
        boolean hasEncode = this.gstCore.hasStructureField(capStruct, "encoding-name");

        if (hasMedia && hasEncode) {
            String media = this.gstCore.getStructureString(capStruct, "media");
            String encode = this.gstCore.getStructureString(capStruct, "encoding-name");

            log.debug("media = " + media + ", encoding = " + encode);

            if (!media.equals("video") && !media.equals("audio")) {
                log.warn("Unsupported RTP media type: " + media);
            } else if (!ConfigRtp.DEPAY_INFO.containsKey(encode)) {
                log.warn("Unsupported RTP encode: " + encode);
            } else {
                RecorderCapability recCap = null;
                Element depayElm = this.gstCore.newElement(ConfigRtp.DEPAY_INFO.get(encode));

                if (media.equals("video")) {
                    recCap = RecorderCapability.VIDEO_ONLY;
                } else {
                    recCap = RecorderCapability.AUDIO_ONLY;
                }

                this.gstCore.addPipelineElements(this.pipeline, depayElm);

                if (ConfigCodec.PARSE_INFO.containsKey(encode)) {
                    Element parseElm = this.gstCore.newElement(ConfigCodec.PARSE_INFO.get(encode));
                    Pad parsePad = this.gstCore.getElementStaticPad(parseElm, "src");

                    this.gstCore.addPipelineElements(this.pipeline, parseElm);
                    this.gstCore.linkManyElement(depayElm, parseElm);
                    this.getPadListener().onNotify(recCap, parsePad);
                    this.gstCore.playElement(parseElm);
                } else {
                    Pad depayPad = this.gstCore.getElementStaticPad(depayElm, "src");
                    this.getPadListener().onNotify(recCap, depayPad);
                }

                this.gstCore.playElement(depayElm);

                rtpDepaySinkPad = this.gstCore.getElementStaticPad(depayElm, "sink");
            }
        }

        return rtpDepaySinkPad;
    }
}
