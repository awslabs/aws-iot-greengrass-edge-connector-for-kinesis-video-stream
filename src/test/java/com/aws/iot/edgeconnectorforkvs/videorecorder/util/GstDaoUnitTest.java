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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.*;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.StateChangeReturn;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.Version;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.freedesktop.gstreamer.lowlevel.GNative;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import org.freedesktop.gstreamer.message.EOSMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.sun.jna.Pointer;

@ExtendWith(MockitoExtension.class)
public class GstDaoUnitTest {
    @Spy
    private GstDao mockGst;
    @Mock
    private Pipeline mockPipe;
    @Mock
    private Bus mockBus;
    @Mock
    private Element mockElm;
    @Mock
    private AppSink mockAppSink;
    @Mock
    private Pad mockPad;
    @Mock
    private Caps mockCaps;
    @Mock
    private Structure mockStruct;
    @Mock
    private Version mockVersion;
    @Spy
    private GLibUtilApiInst mockGutil;

    interface MockSigCallback extends GstCallback {
        void callback();
    }

    static class GLibUtilApiInst implements GLibUtilAPI {
        public Pointer g_strdup(String str) {
            return null;
        }
    }

    @Test
    void gstDaoTest_invokeStaticMethods_noException() {
        // Test: static Gst init/context
        try (MockedStatic<Gst> mockGstStatic = mockStatic(Gst.class)) {
            // Mocking
            willReturn(mockVersion).given(mockGst).getSatisfyVersion(any(), any());
            mockGstStatic.when(() -> Gst.init(any())).thenAnswer((Answer<Void>) invocation -> null);
            mockGstStatic.when(Gst::main).thenAnswer((Answer<Void>) invocation -> null);
            mockGstStatic.when(Gst::quit).thenAnswer((Answer<Void>) invocation -> null);
            // Mocked behavior
            Assertions.assertDoesNotThrow(() -> mockGst.initContext());
        }

        // Test: static new Element
        try (MockedStatic<ElementFactory> mockFactoryStatic = mockStatic(ElementFactory.class)) {
            // Mocking
            mockFactoryStatic.when(() -> ElementFactory.make(anyString(), anyString()))
                    .thenReturn(mockElm);
            // Mocked behavior
            Assertions.assertEquals(mockElm, mockGst.newElement("testElm"));
        }

        // Test: link Elements
        try (MockedStatic<Element> mockElementStatic = mockStatic(Element.class)) {
            // Mocking
            mockElementStatic.when(() -> Element.linkMany(any(Element.class), any(Element.class)))
                    .thenReturn(true);
            // Mocked behavior
            Assertions.assertTrue(mockGst.linkManyElement(mockElm, mockElm));
        }

        // Test: GLib
        try (MockedStatic<GNative> mockGLibStatic = mockStatic(GNative.class)) {
            mockGLibStatic.when(() -> GNative.loadLibrary(any(), any(), any()))
                    .thenReturn(mockGutil);

            Assertions.assertDoesNotThrow(() -> mockGst.invokeGLibStrdup("g_strdup"));
        }
    }

    @Test
    void gstDaoTest_invokeConstructor_noException() {
        // Test: new Pipeline
        try (MockedConstruction<Pipeline> mockPipe = mockConstruction(Pipeline.class)) {

            Pipeline pipe = mockGst.newPipeline();
            Assertions.assertNotNull(pipe);
        }

        // Test: new EOSEvent
        try (MockedConstruction<EOSEvent> mockEosEvent = mockConstruction(EOSEvent.class)) {

            EOSEvent event = mockGst.newEosEvent();
            Assertions.assertNotNull(event);
        }

        // Test: new Pipeline
        try (MockedConstruction<EOSMessage> mockEosMsg = mockConstruction(EOSMessage.class)) {

            EOSMessage msg = mockGst.newEosMessage(null);
            Assertions.assertNotNull(msg);
        }
    }

    @Test
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
    void gstDaoTest_invokeGstMethods_noException() {
        willDoNothing().given(mockGst).initContext();
        willReturn(mockElm).given(mockGst).newElement(anyString());

        // Element
        willReturn(mockPad).given(mockElm).getRequestPad(anyString());
        willReturn(mockPad).given(mockElm).getStaticPad(anyString());
        willReturn(StateChangeReturn.SUCCESS).given(mockElm).play();
        willReturn(StateChangeReturn.SUCCESS).given(mockElm).stop();
        willReturn(true).given(mockElm).sendEvent(any());
        willReturn(true).given(mockElm).syncStateWithParent();
        // Pipeline
        willReturn(mockBus).given(mockPipe).getBus();
        // Pad
        willReturn(mockCaps).given(mockPad).getCurrentCaps();
        willReturn(false).given(mockPad).isLinked();
        willReturn(mockPad).given(mockPad).getPeer();
        willReturn(true).given(mockPad).sendEvent(any());
        // Caps
        willReturn(mockStruct).given(mockCaps).getStructure(anyInt());
        // Struct
        willReturn("mockStruct").given(mockStruct).getName();
        willReturn(true).given(mockStruct).hasField(anyString());
        willReturn("mockStructField").given(mockStruct).getString(anyString());
        // Bus
        willReturn(true).given(mockBus).post(any());

        willReturn(true).given(mockVersion).checkSatisfies(any(Version.class));
        Assertions.assertEquals(mockVersion, mockGst.getSatisfyVersion(mockVersion, mockVersion));
        willReturn(false).given(mockVersion).checkSatisfies(any(Version.class));
        Assertions.assertEquals(mockVersion, mockGst.getSatisfyVersion(mockVersion, mockVersion));

        Assertions.assertDoesNotThrow(() -> mockGst.initContext());

        Assertions.assertEquals(mockElm, mockGst.newElement("testElm"));

        Assertions.assertDoesNotThrow(() -> mockGst.setElement(mockElm, "property", 0));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.setElement(null, "property", 0));

        Assertions.assertDoesNotThrow(() -> mockGst.setAsStringElement(mockElm, "property", "val"));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.setAsStringElement(null, "property", "val"));

        Assertions.assertDoesNotThrow(
                () -> mockGst.connectElement(mockElm, (Element.PAD_ADDED) (elm, pad) -> {
                }));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.connectElement(null, (Element.PAD_ADDED) (elm, pad) -> {
                }));

        Assertions.assertDoesNotThrow(
                () -> mockGst.connectElement(mockElm, "mockElmSig", (MockSigCallback) () -> {
                }));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.connectElement(null, "mockElmSig", (MockSigCallback) () -> {
                }));

        Assertions.assertEquals(mockPad, mockGst.getElementRequestPad(mockElm, "sink"));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.getElementRequestPad(null, "sink"));

        Assertions.assertEquals(mockPad, mockGst.getElementStaticPad(mockElm, "sink"));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.getElementStaticPad(null, "sink"));

        Assertions.assertDoesNotThrow(() -> mockGst.relElementRequestPad(mockElm, mockPad));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.relElementRequestPad(null, mockPad));

        Assertions.assertTrue(mockGst.syncElementParentState(mockElm));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.syncElementParentState(null));

        Assertions.assertEquals(StateChangeReturn.SUCCESS, mockGst.playElement(mockElm));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.playElement(null));

        Assertions.assertEquals(StateChangeReturn.SUCCESS, mockGst.stopElement(mockElm));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.stopElement(null));

        Assertions.assertTrue(mockGst.sendElementEvent(mockElm, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.sendElementEvent(null, null));

        Assertions.assertTrue(mockGst.sendPadEvent(mockPad, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.sendPadEvent(null, null));

        Assertions.assertEquals(mockBus, mockGst.getPipelineBus(mockPipe));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.getPipelineBus(null));

        Assertions.assertDoesNotThrow(() -> mockGst.addPipelineElements(mockPipe, mockElm));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.addPipelineElements(null, mockElm));

        Assertions.assertEquals(mockCaps, mockGst.getPadCaps(mockPad));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.getPadCaps(null));

        Assertions.assertFalse(mockGst.isPadLinked(mockPad));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.isPadLinked(null));

        Assertions.assertEquals(mockPad, mockGst.getPadPeer(mockPad));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.getPadPeer(null));

        Assertions.assertDoesNotThrow(() -> mockGst.linkPad(mockPad, mockPad));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.linkPad(null, mockPad));

        Assertions.assertDoesNotThrow(() -> mockGst.unlinkPad(mockPad, mockPad));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.unlinkPad(null, mockPad));

        Assertions.assertDoesNotThrow(() -> mockGst.addPadProbe(mockPad, PadProbeType.BLOCK, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.addPadProbe(null, PadProbeType.BLOCK, null));

        Assertions.assertDoesNotThrow(() -> mockGst.removePadProbe(mockPad, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.removePadProbe(null, null));

        Assertions.assertEquals(mockStruct, mockGst.getCapsStructure(mockCaps, 0));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.getCapsStructure(null, 0));

        Assertions.assertEquals("mockStruct", mockGst.getStructureName(mockStruct));
        Assertions.assertThrows(NullPointerException.class, () -> mockGst.getStructureName(null));

        Assertions.assertTrue(mockGst.hasStructureField(mockStruct, "field"));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.hasStructureField(null, "field"));

        Assertions.assertEquals("mockStructField", mockGst.getStructureString(mockStruct, "field"));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.getStructureString(null, "field"));

        Assertions.assertTrue(mockGst.postBusMessage(mockBus, null));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.postBusMessage(null, null));

        Assertions.assertDoesNotThrow(
                () -> mockGst.connectBus(mockBus, (Bus.WARNING) (source, code, msg) -> {
                }));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.connectBus(null, (Bus.WARNING) (source, code, msg) -> {
                }));
        Assertions.assertDoesNotThrow(
                () -> mockGst.connectBus(mockBus, (Bus.ERROR) (source, code, msg) -> {
                }));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.connectBus(null, (Bus.ERROR) (source, code, msg) -> {
                }));
        Assertions.assertDoesNotThrow(() -> mockGst.connectBus(mockBus, (Bus.EOS) (source) -> {
        }));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.connectBus(null, (Bus.EOS) (source) -> {
                }));

        Assertions.assertDoesNotThrow(() -> mockGst.connectAppSink(mockAppSink, (appElm) -> {
            return FlowReturn.OK;
        }));
        Assertions.assertThrows(NullPointerException.class,
                () -> mockGst.connectAppSink(null, (appElm) -> {
                    return FlowReturn.OK;
                }));
    }
}
