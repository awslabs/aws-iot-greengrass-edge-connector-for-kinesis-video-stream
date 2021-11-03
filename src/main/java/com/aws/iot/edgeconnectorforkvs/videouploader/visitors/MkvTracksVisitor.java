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

package com.aws.iot.edgeconnectorforkvs.videouploader.visitors;

import com.amazonaws.kinesisvideo.parser.ebml.EBMLTypeInfo;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.MkvDataElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.mkv.MkvEndMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvStartMasterElement;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvDataRawElement;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvParentRawElement;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvRawElement;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.MkvTracksException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A MKV visitor to gather tracks metadata in the MKV.
 */
@Slf4j
public class MkvTracksVisitor extends MkvElementVisitor {

    /**
     * Visitor's parsing state.
     */
    public enum State {
        /** In this state, we do nothing. */
        NEW,

        /** In this state, we buffering and processing MKV elements. */
        BUFFERING_TRACKS,

        /** This is an invalid state. */
        INVALID,
    }

    @Getter
    private State state = State.NEW;

    private MkvParentRawElement previousTracks = null;

    private MkvParentRawElement currentTracks = null;

    private MkvParentRawElement currentParentElement = null;

    private static final byte[] ID_TRACK_UID = new byte[]{(byte) 0x73, (byte) 0xC5};

    @Getter
    private boolean isTracksEquivalent = true;

    @Override
    public void visit(MkvStartMasterElement startMasterElement) {
        final EBMLTypeInfo ebmlTypeInfo = startMasterElement.getElementMetaData().getTypeInfo();
        switch (state) {
            case NEW:
                if (MkvTypeInfos.TRACKS.equals(ebmlTypeInfo)) {
                    log.debug("state: NEW -> BUFFERING_TRACKS");
                    state = State.BUFFERING_TRACKS;

                    currentTracks = new MkvParentRawElement(startMasterElement.getIdAndSizeRawBytes());
                    currentParentElement = currentTracks;
                }
                break;
            case BUFFERING_TRACKS:
                MkvParentRawElement parent = new MkvParentRawElement(startMasterElement.getIdAndSizeRawBytes());
                currentParentElement.addRawElement(parent);
                currentParentElement = parent;
                break;
            default:
                throw new MkvTracksException("Unknown state " + state);
        }
    }

    @Override
    public void visit(MkvEndMasterElement endMasterElement) {
        final EBMLTypeInfo ebmlTypeInfo = endMasterElement.getElementMetaData().getTypeInfo();
        switch (state) {
            case NEW:
                break;
            case BUFFERING_TRACKS:
                if (MkvTypeInfos.TRACKS.equals(ebmlTypeInfo)) {
                    if (previousTracks == null) {
                        previousTracks = currentTracks;
                        currentTracks = null;
                    } else {
                        isTracksEquivalent = checkIfTracksEquivalent(previousTracks, currentTracks);
                    }

                    log.debug("state: BUFFERING_TRACKS -> NEW");
                    state = State.NEW;
                } else {
                    currentParentElement = currentParentElement.getParent();
                }
                break;
            default:
                throw new MkvTracksException("Unknown state " + state);
        }
    }

    @Override
    public void visit(MkvDataElement dataElement) {
        final EBMLTypeInfo ebmlTypeInfo = dataElement.getElementMetaData().getTypeInfo();
        if (MkvTypeInfos.CRC_32.equals(ebmlTypeInfo) || MkvTypeInfos.VOID.equals(ebmlTypeInfo)) {
            return;
        }
        switch (state) {
            case NEW:
                break;
            case BUFFERING_TRACKS:
                MkvDataRawElement dataRawElement = new MkvDataRawElement(dataElement.getIdAndSizeRawBytes(),
                        dataElement.getDataBuffer());
                currentParentElement.addRawElement(dataRawElement);
                break;
            default:
                throw new MkvTracksException("Unknown state " + state);
        }
    }

    /**
     * Test if 2 tracks are equivalent. We compare fields related media settings, so we ignore track UID because it's
     * not related.
     *
     * @param previousTracks The first tracks to be compared
     * @param currentTracks  The second tracks to be compared
     * @return True if they are equivalent, false otherwise.
     */
    private boolean checkIfTracksEquivalent(MkvParentRawElement previousTracks, MkvParentRawElement currentTracks) {
        boolean isEquivalent = true;
        final ArrayList<MkvRawElement> previousElements = previousTracks.getFlattenedElements();
        final ArrayList<MkvRawElement> currentElements = currentTracks.getFlattenedElements();

        if (previousElements.size() != currentElements.size()) {
            isEquivalent = false;
        } else {
            for (int i = 0; i < previousElements.size(); i++) {
                final MkvRawElement previousElement = previousElements.get(i);
                final MkvRawElement currentElement = currentElements.get(i);

                if (previousElement.getClass() != currentElement.getClass()) {
                    isEquivalent = false;
                    break;
                }

                if (Arrays.equals(previousElement.getIdCopy(), ID_TRACK_UID)
                        && Arrays.equals(currentElement.getIdCopy(), ID_TRACK_UID)) {
                    log.debug("ignore track uid");
                } else {
                    if (!previousElement.equals(currentElement)) {
                        isEquivalent = false;
                        break;
                    }
                }
            }
        }
        return isEquivalent;
    }

    /**
     * Convert tracks to MKV raw data format.
     *
     * @return MKV raw data of tracks
     * @throws IOException if no available tracks
     */
    public byte[] toMkvRawData() throws IOException {
        if (isTracksAvailable()) {
            return previousTracks.toMkvRawData();
        } else {
            throw new IOException("No available tracks");
        }
    }

    public boolean isTracksAvailable() {
        return previousTracks != null;
    }
}
