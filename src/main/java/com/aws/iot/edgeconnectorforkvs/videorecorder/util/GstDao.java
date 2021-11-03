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

package com.aws.iot.edgeconnectorforkvs.videorecorder.util;

import com.sun.jna.Pointer;
import org.apache.commons.lang3.RandomStringUtils;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.StateChangeReturn;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.Pad.PROBE;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.freedesktop.gstreamer.event.Event;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import org.freedesktop.gstreamer.message.EOSMessage;
import org.freedesktop.gstreamer.message.Message;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * GStreamer data object.
 */
@Slf4j
public class GstDao {
    /**
     * Gst context init.
     */
    public void initContext() {
        Gst.init(this.getSatisfyVersion(this.getRequestVersion(), this.getLocalVersion()));
    }

    /**
     * Gst new element.
     *
     * @param factoryName element name
     * @return element
     */
    public Element newElement(String factoryName) {
        final String nameSuffix = RandomStringUtils.randomAlphanumeric(4);
        return ElementFactory.make(factoryName, factoryName + "_" + nameSuffix);
    }

    /**
     * Set element property.
     *
     * @param elm element
     * @param property property
     * @param data value
     */
    public void setElement(@NonNull Element elm, String property, Object data) {
        elm.set(property, data);
    }

    /**
     * Set element property.
     *
     * @param elm element
     * @param property property
     * @param data value
     */
    public void setAsStringElement(@NonNull Element elm, String property, String data) {
        elm.setAsString(property, data);
    }

    /**
     * Add listener.
     *
     * @param elm element
     * @param listener listener
     */
    public void connectElement(@NonNull Element elm, Element.PAD_ADDED listener) {
        elm.connect(listener);
    }

    /**
     * Add listener.
     *
     * @param elm element
     * @param signal signal
     * @param cb callback
     */
    public void connectElement(@NonNull Element elm, String signal, GstCallback cb) {
        elm.connect(signal, Object.class, null, cb);
    }

    /**
     * Get request pad.
     *
     * @param elm element
     * @param name pad name
     * @return pad
     */
    public Pad getElementRequestPad(@NonNull Element elm, String name) {
        return elm.getRequestPad(name);
    }

    /**
     * Release request pad.
     *
     * @param elm element
     * @param pad pad
     */
    public void relElementRequestPad(@NonNull Element elm, Pad pad) {
        elm.releaseRequestPad(pad);
    }

    /**
     * Get static pad.
     *
     * @param elm element
     * @param padName pad name
     * @return pad
     */
    public Pad getElementStaticPad(@NonNull Element elm, String padName) {
        return elm.getStaticPad(padName);
    }

    /**
     * Link elements.
     *
     * @param elements elements
     * @return true if success
     */
    public boolean linkManyElement(Element... elements) {
        return Element.linkMany(elements);
    }

    /**
     * Set element status to playing.
     *
     * @param elm element
     * @return true if success
     */
    public StateChangeReturn playElement(@NonNull Element elm) {
        return elm.play();
    }

    /**
     * Set element status to null.
     *
     * @param elm element
     * @return true if success
     */
    public StateChangeReturn stopElement(@NonNull Element elm) {
        return elm.stop();
    }

    /**
     * Sync element status with parent.
     *
     * @param elm element
     * @return true if success
     */
    public boolean syncElementParentState(@NonNull Element elm) {
        return elm.syncStateWithParent();
    }

    /**
     * Set event to element.
     *
     * @param elm element
     * @param ev event
     * @return true if success
     */
    public boolean sendElementEvent(@NonNull Element elm, Event ev) {
        return elm.sendEvent(ev);
    }

    /**
     * Set event to pad.
     *
     * @param p pad
     * @param ev event
     * @return true if success
     */
    public boolean sendPadEvent(@NonNull Pad p, Event ev) {
        return p.sendEvent(ev);
    }

    /**
     * Gst pipeline new.
     *
     * @return pipeline
     */
    public Pipeline newPipeline() {
        return new Pipeline();
    }

    /**
     * Get bus.
     *
     * @param p pipeline
     * @return bus
     */
    public Bus getPipelineBus(@NonNull Pipeline p) {
        return p.getBus();
    }

    /**
     * Add elements to pipeline.
     *
     * @param p pipeline
     * @param elements elements
     */
    public void addPipelineElements(@NonNull Pipeline p, Element... elements) {
        p.addMany(elements);
    }

    /**
     * Get caps.
     *
     * @param p pad
     * @return caps
     */
    public Caps getPadCaps(@NonNull Pad p) {
        return p.getCurrentCaps();
    }

    /**
     * Check pad linkage.
     *
     * @param p pad
     * @return true if the pad is linked.
     */
    public boolean isPadLinked(@NonNull Pad p) {
        return p.isLinked();
    }

    /**
     * Get peer of the given pad.
     *
     * @param p pad
     * @return peer pad
     */
    public Pad getPadPeer(@NonNull Pad p) {
        return p.getPeer();
    }

    /**
     * Link pads.
     *
     * @param src src pad
     * @param sink sink pad
     * @throws PadLinkException pad linking failed exception
     */
    public void linkPad(@NonNull Pad src, Pad sink) throws PadLinkException {
        src.link(sink);
    }

    /**
     * Unlink src pad from sink pad.
     *
     * @param src src pad
     * @param sink sink pad
     * @return true if success.
     */
    public boolean unlinkPad(@NonNull Pad src, Pad sink) {
        return src.unlink(sink);
    }

    /**
     * Add probe to a pad.
     *
     * @param p pad
     * @param mask probe type
     * @param callback callback
     */
    public void addPadProbe(@NonNull Pad p, PadProbeType mask, PROBE callback) {
        p.addProbe(mask, callback);
    }

    /**
     * Remove probe from a pad.
     *
     * @param p pad
     * @param callback callback
     */
    public void removePadProbe(@NonNull Pad p, PROBE callback) {
        p.removeProbe(callback);
    }

    /**
     * Get structure.
     *
     * @param c caps
     * @param index index of structures
     * @return structure
     */
    public Structure getCapsStructure(@NonNull Caps c, int index) {
        return c.getStructure(index);
    }

    /**
     * Get structure name.
     *
     * @param s structure
     * @return name
     */
    public String getStructureName(@NonNull Structure s) {
        return s.getName();
    }

    /**
     * Check field of a structure.
     *
     * @param s structure
     * @param fieldName name
     * @return true if the name exists
     */
    public boolean hasStructureField(@NonNull Structure s, String fieldName) {
        return s.hasField(fieldName);
    }

    /**
     * Get name of the field.
     *
     * @param s structure
     * @param fieldName field name
     * @return value
     */
    public String getStructureString(@NonNull Structure s, String fieldName) {
        return s.getString(fieldName);
    }

    /**
     * Post message to bus.
     *
     * @param b bus
     * @param msg message
     * @return true if success
     */
    public boolean postBusMessage(@NonNull Bus b, Message msg) {
        return b.post(msg);
    }

    /**
     * Add listener.
     *
     * @param b bus
     * @param listener listener
     */
    public void connectBus(@NonNull Bus b, Bus.ERROR listener) {
        b.connect(listener);
    }

    /**
     * Add listener.
     *
     * @param b bus
     * @param listener listener
     */
    public void connectBus(@NonNull Bus b, Bus.WARNING listener) {
        b.connect(listener);
    }

    /**
     * Add listener.
     *
     * @param b bus
     * @param listener listener
     */
    public void connectBus(@NonNull Bus b, Bus.EOS listener) {
        b.connect(listener);
    }

    /**
     * Add listener.
     *
     * @param appSink app sink
     * @param listener listener
     */
    public void connectAppSink(@NonNull AppSink appSink, AppSink.NEW_SAMPLE listener) {
        appSink.connect(listener);
    }

    /**
     * Create EOS event.
     *
     * @return EOSEvent object
     */
    public EOSEvent newEosEvent() {
        return new EOSEvent();
    }

    /**
     * Create EOS message.
     *
     * @param src source object
     * @return EOSMessage object
     */
    public EOSMessage newEosMessage(GstObject src) {
        return new EOSMessage(src);
    }

    /**
     * Invoke GLib g_strdup.
     *
     * @param str String to duplicate.
     * @return Native pointer of the gchararray
     */
    public Pointer invokeGLibStrdup(String str) {
        return GLibUtilAPI.GLIB_API.g_strdup(str);
    }

    Version getRequestVersion() {
        return new Version(Config.GST_VER_MAJOR, Config.GST_VER_MINOR, Config.GST_VER_MICRO,
                Config.GST_VER_NANO);
    }

    Version getLocalVersion() {
        return Gst.getVersion();
    }

    Version getSatisfyVersion(Version requested, Version local) {
        if (!local.checkSatisfies(requested)) {
            String msg = "The requested version of GStreamer is not available. "
                    + String.format("Requested : %s, Available : %s ", requested, local);
            log.warn(msg);
            requested = local;
        }

        return requested;
    }
}
