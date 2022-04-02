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

package com.aws.iot.edgeconnectorforkvs.videorecorder.module.camera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

/**
 * Camera module for RTSP.
 */
@Slf4j
public class RecorderCameraRtsp extends RecorderCameraBase {
    private Element rtspSrc;
    private SdpCallback sdpListener;
    private Element.PAD_ADDED padAddListener;
    private GstDao gstCore;
    private Pipeline pipeline;
    private String sourceUrl;
    private ArrayList<ArrayList<Element>> rtpAudioPaths;
    private ArrayList<ArrayList<Element>> rtpVideoPaths;
    private HashMap<String, Object> propertySet;

    /**
     * Interface for sdp.
     */
    public interface SdpCallback extends GstCallback {
        /**
         * Callback for sdp.
         *
         * @param rtspElm rtspsrc
         * @param sdp sdp message
         * @param userData user data
         */
        void callback(Element rtspElm, SDPMessage sdp, GPointer userData);
    }

    /**
     * RecorderCameraRtsp constructor.
     *
     * @param dao GStreamer data access object
     * @param pipeline GStreamer pipeline
     * @param sourceUrl RTSP source address
     */
    public RecorderCameraRtsp(GstDao dao, Pipeline pipeline, String sourceUrl) {
        super(dao, pipeline);
        this.gstCore = this.getGstCore();
        this.pipeline = this.getPipeline();
        this.sourceUrl = sourceUrl;
        this.rtpAudioPaths = new ArrayList<>();
        this.rtpVideoPaths = new ArrayList<>();
        this.propertySet = new HashMap<>();

        this.sdpListener = (rtspElm, sdp, userData) -> {
            String msg = sdp.toString();
            int audioCnt = StringUtils.countMatches(msg, "m=audio");
            int videoCnt = StringUtils.countMatches(msg, "m=video");

            log.debug(msg.toString());
            log.info("RTSP has {} video and {} audio track(s).", videoCnt, audioCnt);

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
                    log.info("RTSP and depay are linked.");
                } catch (PadLinkException ex) {
                    log.error("RTSP and depay link failed: {}.", ex.getLinkResult());
                    this.getErrListener()
                            .onError("cameraSource pad link failed: " + ex.getLinkResult());
                }
            }
        };
    }

    /**
     * Set properties to the rtsp camera.
     *
     * @param property property name
     * @param data value
     */
    @Override
    public synchronized void setProperty(String property, Object data)
            throws IllegalArgumentException {
        this.propertySet.put(property, data);

        if (this.rtspSrc != null) {
            try {
                this.gstCore.setElement(this.rtspSrc, property, data);
            } catch (IllegalArgumentException e) {
                log.error("Setting rtspCamera fails for the invalid property {}.", property);
                this.propertySet.remove(property);
            }
        } else {
            log.warn("The rtspCamera will set properties later.");
        }
    }

    /**
     * Notify for being bound.
     */
    @Override
    public synchronized void onBind() {
        ArrayList<String> invalidProp = new ArrayList<>();

        log.debug("RtspCamera onBind");

        this.rtspSrc = this.gstCore.newElement("rtspsrc");
        this.gstCore.setAsStringElement(this.rtspSrc, "location", this.sourceUrl);
        this.gstCore.setElement(this.rtspSrc, "do-rtcp", true);
        this.gstCore.setElement(this.rtspSrc, "latency", 0);
        this.gstCore.setElement(this.rtspSrc, "short-header", true);

        // Delayed property setting
        for (Map.Entry<String, Object> property : this.propertySet.entrySet()) {
            try {
                this.gstCore.setElement(this.rtspSrc, property.getKey(), property.getValue());
            } catch (IllegalArgumentException e) {
                log.error("Setting rtspCamera skips the invalid property {}.", property.getKey());
                invalidProp.add(property.getKey());
            }
        }
        for (String prop : invalidProp) {
            this.propertySet.remove(prop);
        }

        // Signals
        this.gstCore.connectElement(this.rtspSrc, "on-sdp", this.sdpListener);
        this.gstCore.connectElement(this.rtspSrc, this.padAddListener);

        this.gstCore.addPipelineElements(this.pipeline, this.rtspSrc);
    }

    /**
     * Notify for being unbound.
     */
    @Override
    public synchronized void onUnbind() {
        log.debug("RtspCamera onUnbind");

        this.relRtpPath(RecorderCapability.AUDIO_ONLY);
        this.relRtpPath(RecorderCapability.VIDEO_ONLY);

        this.gstCore.stopElement(this.rtspSrc);
        this.gstCore.removePipelineElements(this.pipeline, this.rtspSrc);
        this.rtspSrc = null;
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
                log.debug("Ignore type {} which is not x-rtp.", padTypeName);
            }
        }

        padCaps.dispose();

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
                log.warn("Unsupported RTP media type: {}.", media);
            } else if (!ConfigRtp.DEPAY_INFO.containsKey(encode)) {
                log.warn("Unsupported RTP encode: {}.", encode);
            } else {
                RecorderCapability recCap = null;
                ArrayList<ArrayList<Element>> rtpPaths = null;
                ArrayList<Element> rtpPath = new ArrayList<>();
                Element depayElm = this.gstCore.newElement(ConfigRtp.DEPAY_INFO.get(encode));

                if (media.equals("video")) {
                    recCap = RecorderCapability.VIDEO_ONLY;
                    rtpPaths = this.rtpVideoPaths;
                } else {
                    recCap = RecorderCapability.AUDIO_ONLY;
                    rtpPaths = this.rtpAudioPaths;
                }

                this.gstCore.addPipelineElements(this.pipeline, depayElm);
                rtpPath.add(depayElm);

                if (ConfigCodec.PARSE_INFO.containsKey(encode)) {
                    Element parseElm = this.gstCore.newElement(ConfigCodec.PARSE_INFO.get(encode));
                    Pad parsePad = this.gstCore.getElementStaticPad(parseElm, "src");

                    this.gstCore.addPipelineElements(this.pipeline, parseElm);
                    this.gstCore.linkManyElement(depayElm, parseElm);
                    this.getPadListener().onNotify(recCap, parsePad);
                    this.gstCore.playElement(parseElm);
                    rtpPath.add(parseElm);
                } else {
                    Pad depayPad = this.gstCore.getElementStaticPad(depayElm, "src");
                    this.getPadListener().onNotify(recCap, depayPad);
                }

                rtpPaths.add(rtpPath);

                this.gstCore.playElement(depayElm);
                rtpDepaySinkPad = this.gstCore.getElementStaticPad(depayElm, "sink");
            }
        }

        return rtpDepaySinkPad;
    }

    private void relRtpPath(RecorderCapability cap) {
        ArrayList<ArrayList<Element>> rtpPaths = null;

        if (cap == RecorderCapability.AUDIO_ONLY) {
            rtpPaths = this.rtpAudioPaths;
        } else {
            rtpPaths = this.rtpVideoPaths;
        }

        log.info("RrspCamera release path: {}.", cap);

        for (ArrayList<Element> path : rtpPaths) {
            int num = path.size();

            for (int i = num - 1; i > 0; --i) {
                this.gstCore.unlinkElements(path.get(i - 1), path.get(i));
                this.gstCore.stopElement(path.get(i));
                this.gstCore.removePipelineElements(this.pipeline, path.get(i));
            }

            this.gstCore.unlinkElements(this.rtspSrc, path.get(0));
            this.gstCore.stopElement(path.get(0));
            this.gstCore.removePipelineElements(this.pipeline, path.get(0));
        }

        rtpPaths.clear();
    }
}
