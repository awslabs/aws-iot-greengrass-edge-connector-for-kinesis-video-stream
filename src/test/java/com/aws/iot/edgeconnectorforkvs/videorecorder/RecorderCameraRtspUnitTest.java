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

import static org.mockito.BDDMockito.*;
import com.aws.iot.edgeconnectorforkvs.videorecorder.RecorderCameraRtsp.SdpCallback;
import com.aws.iot.edgeconnectorforkvs.videorecorder.util.GstDao;
import com.aws.iot.edgeconnectorforkvs.videorecorder.base.RecorderCameraBase;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.SDPMessage;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecorderCameraRtspUnitTest {
    private static final String RTSP_URL = "rtspt://test";
    private static final String RTP_CAP = "application/x-rtp";
    private static final String NRTP_CAP = "application/not-rtp";

    @Mock
    private GstDao mockGst;
    @Mock
    private Pipeline mockPipeline;
    @Mock
    private SDPMessage mockSdp;
    @Mock
    private Element mockRtsp;
    @Mock
    private Pad mockPad;
    @Mock
    private Caps mockCaps;
    @Mock
    private Structure mockProp;

    private RecorderCameraBase.CapabilityListener capListener;
    private RecorderCameraBase.NewPadListener padListener;
    private RecorderCameraBase.ErrorListener errListener;
    private SdpCallback sdpListener;
    private Element.PAD_ADDED padAddListener;

    @BeforeEach
    void setupTest() {
        this.capListener = (audioCnt, videoCnt) -> {
        };

        this.padListener = (cap, newPad) -> {
        };

        this.errListener = desc -> {
        };

        willAnswer(invocation -> {
            this.sdpListener = invocation.getArgument(2);
            return null;
        }).given(this.mockGst).connectElement(any(), eq("on-sdp"), any(GstCallback.class));

        willAnswer(invocation -> {
            this.padAddListener = invocation.getArgument(1);
            return null;
        }).given(this.mockGst).connectElement(any(), any(Element.PAD_ADDED.class));
    }

    @Test
    void onSdpTest_invokeSdpListener_noException() {
        RecorderCameraRtsp camera =
                new RecorderCameraRtsp(this.mockGst, this.mockPipeline, RTSP_URL);

        Assertions.assertDoesNotThrow(() -> camera.setProperty("location", RTSP_URL));
        Assertions.assertDoesNotThrow(() -> camera.registerListener(this.capListener,
                this.padListener, this.errListener));

        // RTSP contains both video and audio
        willReturn("m=audio\nm=video").given(this.mockSdp).toString();
        Assertions.assertDoesNotThrow(
                () -> this.sdpListener.callback(this.mockRtsp, this.mockSdp, null));

        // RTSP contains video only
        willReturn("m=video").given(this.mockSdp).toString();
        Assertions.assertDoesNotThrow(
                () -> this.sdpListener.callback(this.mockRtsp, this.mockSdp, null));

        // RTSP contains audio only
        willReturn("m=audio").given(this.mockSdp).toString();
        Assertions.assertDoesNotThrow(
                () -> this.sdpListener.callback(this.mockRtsp, this.mockSdp, null));

        // RTSP doesn't contain video or audio
        willReturn("").given(this.mockSdp).toString();
        Assertions.assertDoesNotThrow(
                () -> this.sdpListener.callback(this.mockRtsp, this.mockSdp, null));
    }

    @Test
    void onPadAddedTest_invalidRtp_noException() {
        willReturn(this.mockCaps).given(this.mockGst).getPadCaps(eq(this.mockPad));
        willReturn(1).given(this.mockCaps).size();
        willReturn(this.mockProp).given(this.mockGst).getCapsStructure(eq(this.mockCaps), anyInt());

        RecorderCameraRtsp camera =
                new RecorderCameraRtsp(this.mockGst, this.mockPipeline, RTSP_URL);
        camera.registerListener(this.capListener, this.padListener, this.errListener);

        // Test: not x-rtp
        willReturn(NRTP_CAP).given(this.mockGst).getStructureName(eq(this.mockProp));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: x-rtp, No media and No encode
        willReturn(RTP_CAP).given(this.mockGst).getStructureName(eq(this.mockProp));
        willReturn(false).given(this.mockGst).hasStructureField(eq(this.mockProp), eq("media"));
        willReturn(false).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: x-rtp, No media
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: x-rtp, No encode
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp), eq("media"));
        willReturn(false).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test x-rtp, NOT video and NOT audio
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp), eq("media"));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        willReturn("subtitle").given(this.mockGst).getStructureString(eq(this.mockProp),
                eq("media"));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));
    }

    @Test
    void onPadAddedTest_videoH264_noException() {
        willReturn(this.mockCaps).given(this.mockGst).getPadCaps(eq(this.mockPad));
        willReturn(1).given(this.mockCaps).size();
        willReturn(this.mockProp).given(this.mockGst).getCapsStructure(eq(this.mockCaps), anyInt());
        willReturn(this.mockPad).given(this.mockGst).getElementStaticPad(any(), anyString());
        willReturn(RTP_CAP).given(this.mockGst).getStructureName(eq(this.mockProp));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp), eq("media"));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        willReturn("video").given(this.mockGst).getStructureString(eq(this.mockProp), eq("media"));
        willReturn("H264").given(this.mockGst).getStructureString(eq(this.mockProp),
                eq("encoding-name"));

        RecorderCameraRtsp camera =
                new RecorderCameraRtsp(this.mockGst, this.mockPipeline, RTSP_URL);
        camera.registerListener(this.capListener, this.padListener, this.errListener);

        // Test not linked yet
        willReturn(false).given(this.mockGst).isPadLinked(eq(this.mockPad));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: link failed and PadLinkException is handled in recorder PadLinkException
        PadLinkException mockPadLinkEx = mock(PadLinkException.class);
        willThrow(mockPadLinkEx).given(mockGst).linkPad(any(Pad.class), any(Pad.class));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: already linked
        willReturn(true).given(this.mockGst).isPadLinked(eq(this.mockPad));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));
    }

    @Test
    void onPadAddedTest_videoVp8_noException() {
        willReturn(this.mockCaps).given(this.mockGst).getPadCaps(eq(this.mockPad));
        willReturn(1).given(this.mockCaps).size();
        willReturn(this.mockProp).given(this.mockGst).getCapsStructure(eq(this.mockCaps), anyInt());
        willReturn(this.mockPad).given(this.mockGst).getElementStaticPad(any(), anyString());
        willReturn(RTP_CAP).given(this.mockGst).getStructureName(eq(this.mockProp));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp), eq("media"));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        willReturn("video").given(this.mockGst).getStructureString(eq(this.mockProp), eq("media"));
        willReturn("VP8").given(this.mockGst).getStructureString(eq(this.mockProp),
                eq("encoding-name"));

        RecorderCameraRtsp camera =
                new RecorderCameraRtsp(this.mockGst, this.mockPipeline, RTSP_URL);
        camera.registerListener(this.capListener, this.padListener, this.errListener);

        // Test not linked yet
        willReturn(false).given(this.mockGst).isPadLinked(eq(this.mockPad));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));
    }

    @Test
    void onPadAddedTest_audioAAC_noException() {
        willReturn(this.mockCaps).given(this.mockGst).getPadCaps(eq(this.mockPad));
        willReturn(1).given(this.mockCaps).size();
        willReturn(this.mockProp).given(this.mockGst).getCapsStructure(eq(this.mockCaps), anyInt());
        willReturn(this.mockPad).given(this.mockGst).getElementStaticPad(any(), anyString());
        willReturn(RTP_CAP).given(this.mockGst).getStructureName(eq(this.mockProp));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp), eq("media"));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        willReturn("audio").given(this.mockGst).getStructureString(eq(this.mockProp), eq("media"));
        willReturn("MPEG4-GENERIC").given(this.mockGst).getStructureString(eq(this.mockProp),
                eq("encoding-name"));

        RecorderCameraRtsp camera =
                new RecorderCameraRtsp(this.mockGst, this.mockPipeline, RTSP_URL);
        camera.registerListener(this.capListener, this.padListener, this.errListener);

        // Test not linked yet
        willReturn(false).given(this.mockGst).isPadLinked(eq(this.mockPad));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: link failed and PadLinkException is handled in recorder PadLinkException
        PadLinkException mockPadLinkEx = mock(PadLinkException.class);
        willThrow(mockPadLinkEx).given(mockGst).linkPad(any(Pad.class), any(Pad.class));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: already linked
        willReturn(true).given(this.mockGst).isPadLinked(eq(this.mockPad));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));
    }

    @Test
    void onPadAddedTest_unsupportedEncode_noException() {
        willReturn(this.mockCaps).given(this.mockGst).getPadCaps(eq(this.mockPad));
        willReturn(1).given(this.mockCaps).size();
        willReturn(this.mockProp).given(this.mockGst).getCapsStructure(eq(this.mockCaps), anyInt());
        willReturn(RTP_CAP).given(this.mockGst).getStructureName(eq(this.mockProp));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp), eq("media"));
        willReturn(true).given(this.mockGst).hasStructureField(eq(this.mockProp),
                eq("encoding-name"));
        willReturn("unsupported").given(this.mockGst).getStructureString(eq(this.mockProp),
                eq("encoding-name"));

        RecorderCameraRtsp camera =
                new RecorderCameraRtsp(this.mockGst, this.mockPipeline, RTSP_URL);
        camera.registerListener(this.capListener, this.padListener, this.errListener);

        // Test: x-rtp, video, unsupported encode
        willReturn("video").given(this.mockGst).getStructureString(eq(this.mockProp), eq("media"));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));

        // Test: x-rtp, audio, unsupported encode
        willReturn("audio").given(this.mockGst).getStructureString(eq(this.mockProp), eq("media"));
        Assertions.assertDoesNotThrow(
                () -> this.padAddListener.padAdded(this.mockRtsp, this.mockPad));
    }
}
