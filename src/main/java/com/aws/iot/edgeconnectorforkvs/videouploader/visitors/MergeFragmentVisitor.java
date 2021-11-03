/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.aws.iot.edgeconnectorforkvs.videouploader.visitors;

import com.amazonaws.kinesisvideo.parser.ebml.EBMLTypeInfo;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.mkv.MkvDataElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.mkv.MkvEndMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvStartMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.MkvValue;
import com.amazonaws.kinesisvideo.parser.mkv.visitors.CompositeMkvElementVisitor;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvCluster;
import com.aws.iot.edgeconnectorforkvs.videouploader.mkv.MkvSimpleBlock;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.MergeFragmentException;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.MkvTracksException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A MKV element visitor that support to merge 2 different MKV files.
 */
@Slf4j
public final class MergeFragmentVisitor extends CompositeMkvElementVisitor implements Flushable {

    // Output channel for merge results
    private final WritableByteChannel outputChannel;

    private final MergeVisitor mergeVisitor;

    private final boolean isPausable;

    /**
     * Visitor's parsing state.
     */
    public enum MergeState {
        /** This state expects a new incoming MKV data. */
        NEW,

        /** This state buffering elements of segment except cluster and tag. */
        BUFFERING_SEGMENT,

        /** This state buffering elements of cluster. */
        BUFFERING_CLUSTER,

        /** This state buffering elements of tag. */
        BUFFERING_TAG,

        /** This state can be changed from BUFFERING_CLUSTER, and go to BUFFERING_SEGMENT after isDone() is called. */
        PAUSE,

        /** This is invalid state. */
        INVALID,
    }

    // Put state machine in NEW state
    private MergeState state = MergeState.NEW;

    // A output stream for buffering segment elements
    private final ByteArrayOutputStream bufferingSegmentStream = new ByteArrayOutputStream();

    // Channel for holding the buffering output stream of segment
    private WritableByteChannel bufferingSegmentChannel;

    // For MKV tracks elements and its children elements, the are handled by MkvTracksVisitor
    private boolean isIgnoreTracksElements = false;

    // True if a segment element (without cluster and tag) has been emitted
    private boolean isSegmentEmitted = false;

    // Recording track numbers, then we can check frames to avoid KVS FRAMES_MISSING_FOR_TRACK error
    private final Set<Long> trackNumbers = new HashSet<>();

    // It's for recording and comparing MKV tracks.
    private final MkvTracksVisitor tracksVisitor = new MkvTracksVisitor();

    // True if current segment element has be verified and it's the same with previous segment
    private boolean isSegmentVerified = false;

    // Offset for adding to next cluster (a.k.a. fragment) timecode
    @Setter
    private long nextFragmentTimecodeOffsetMs = 0;

    // Timescale for whole MKV file
    private long timescaleMs = DEFAULT_TIMESCALE_MS;

    // Previous cluster
    private MkvCluster previousCluster = null;

    // Current cluster
    private MkvCluster currentCluster = null;

    private static final ByteBuffer SEGMENT_ELEMENT_WITH_UNKNOWN_LENGTH =
            ByteBuffer.wrap(new byte[]{(byte) 0x18, (byte) 0x53, (byte) 0x80, (byte) 0x67, (byte) 0xFF});

    private static final ByteBuffer CLUSTER_ELEMENT_WITH_UNKNOWN_LENGTH =
            ByteBuffer.wrap(new byte[]{(byte) 0x1F, (byte) 0x43, (byte) 0xB6, (byte) 0x75, (byte) 0xFF});

    private static final long DEFAULT_TIMESCALE_MS = 1;

    private MergeFragmentVisitor(List<MkvElementVisitor> childVisitors,
                                 OutputStream outputStream,
                                 boolean isPausable) {
        super(childVisitors);
        this.outputChannel = Channels.newChannel(outputStream);
        this.isPausable = isPausable;
        mergeVisitor = new MergeVisitor();
        this.childVisitors.add(mergeVisitor);
        this.childVisitors.add(tracksVisitor);

        this.bufferingSegmentChannel = Channels.newChannel(bufferingSegmentStream);
    }

    /**
     * Create a default {@link MergeFragmentVisitor}.
     *
     * @param outputStream Output stream for merged MKV data
     * @return {@link MergeFragmentVisitor}
     */
    public static MergeFragmentVisitor create(OutputStream outputStream) {
        final List<MkvElementVisitor> childVisitors = new ArrayList<>();

        return new MergeFragmentVisitor(childVisitors, outputStream, false);
    }

    /**
     * Create a default {@link MergeFragmentVisitor}.
     *
     * @param outputStream Output stream for merged MKV data
     * @return {@link MergeFragmentVisitor}
     */
    public static MergeFragmentVisitor createPausable(OutputStream outputStream) {
        final List<MkvElementVisitor> childVisitors = new ArrayList<>();

        return new MergeFragmentVisitor(childVisitors, outputStream, true);
    }

    private class MergeVisitor extends MkvElementVisitor {
        @Override
        public void visit(MkvStartMasterElement startMasterElement)
                throws MkvElementVisitException, MergeFragmentException {
            final EBMLTypeInfo ebmlTypeInfo = startMasterElement.getElementMetaData().getTypeInfo();
            log.debug("+name=" + ebmlTypeInfo.getName() + ", level=" + ebmlTypeInfo.getLevel());
            switch (state) {
                case NEW:
                    if (!MkvTypeInfos.EBML.equals(ebmlTypeInfo)) {
                        throw new IllegalArgumentException("Un-expected non-EBML element in new MKV stream");
                    }
                    log.trace("state: NEW -> BUFFERING_SEGMENT");
                    state = MergeState.BUFFERING_SEGMENT;
                    bufferAndCollectSegment(startMasterElement);
                    break;
                case BUFFERING_SEGMENT:
                    if (MkvTypeInfos.CLUSTER.equals(ebmlTypeInfo)) {
                        if (!isSegmentEmitted) {
                            if (tracksVisitor.isTracksAvailable()) {
                                emitBufferedSegmentData();
                            } else {
                                throw new MergeFragmentException("No track info available");
                            }
                            isSegmentVerified = true;
                        } else if (!isSegmentVerified) {
                            if (tracksVisitor.isTracksEquivalent()) {
                                log.info("Tracks of new incoming MKV data are the same");
                            } else {
                                log.info("Tracks of new incoming MKV data are different");
                                throw new MergeFragmentException("Tracks of new incoming MKV data are different");
                            }
                        }
                        isSegmentVerified = true;

                        log.trace("state: BUFFERING_SEGMENT -> BUFFERING_CLUSTER");
                        state = MergeState.BUFFERING_CLUSTER;
                        bufferAndCollectCluster(startMasterElement);
                    } else if (MkvTypeInfos.TAGS.equals(ebmlTypeInfo)) {
                        log.trace("state: BUFFERING_SEGMENT -> BUFFERING_TAG");
                        state = MergeState.BUFFERING_TAG;
                        log.debug("Ignore Tag header: " + ebmlTypeInfo.getName());
                    } else {
                        if (MkvTypeInfos.TRACKS.equals(ebmlTypeInfo)) {
                            isIgnoreTracksElements = true;
                        }
                        bufferAndCollectSegment(startMasterElement);
                    }
                    break;
                case BUFFERING_CLUSTER:
                    bufferAndCollectCluster(startMasterElement);
                    break;
                case BUFFERING_TAG:
                    log.debug("Ignore Tag header: " + ebmlTypeInfo.getName());
                    break;
                default:
                    throw new MkvTracksException("Unknown state " + state);
            }
        }

        @Override
        public void visit(MkvEndMasterElement endMasterElement) throws MkvElementVisitException {
            final EBMLTypeInfo ebmlTypeInfo = endMasterElement.getElementMetaData().getTypeInfo();
            log.debug("-name=" + ebmlTypeInfo.getName() + ", level=" + ebmlTypeInfo.getLevel());
            switch (state) {
                case NEW:
                    throw new IllegalArgumentException("NEW state should not start with MkvEndMasterElement "
                            + endMasterElement);
                case BUFFERING_SEGMENT:
                    if (MkvTypeInfos.SEGMENT.equals(ebmlTypeInfo)) {
                        log.trace("state: BUFFERING_SEGMENT -> NEW");
                        state = MergeState.NEW;
                        nextFragmentTimecodeOffsetMs = -1;
                        bufferingSegmentStream.reset();
                        bufferingSegmentChannel = Channels.newChannel(bufferingSegmentStream);
                        isSegmentVerified = false;
                    } else if (MkvTypeInfos.TRACKS.equals(ebmlTypeInfo)) {
                        isIgnoreTracksElements = false;
                    }
                    break;
                case BUFFERING_CLUSTER:
                    if (MkvTypeInfos.CLUSTER.equals(ebmlTypeInfo)) {
                        sortClusters();
                        emitCluster(previousCluster);
                        previousCluster = currentCluster;
                        currentCluster = null;
                        if (isPausable) {
                            log.trace("state: BUFFERING_CLUSTER -> PAUSE");
                            state = MergeState.PAUSE;
                        } else {
                            log.trace("state: BUFFERING_CLUSTER -> BUFFERING_SEGMENT");
                            state = MergeState.BUFFERING_SEGMENT;
                        }
                    }
                    break;
                case BUFFERING_TAG:
                    if (MkvTypeInfos.TAGS.equals(ebmlTypeInfo)) {
                        log.trace("state: BUFFERING_TAG -> BUFFERING_SEGMENT");
                        state = MergeState.BUFFERING_SEGMENT;
                    }
                    break;
                default:
                    throw new MkvTracksException("Unknown state " + state);
            }
        }

        @Override
        public void visit(MkvDataElement dataElement) throws MkvElementVisitException {
            final EBMLTypeInfo ebmlTypeInfo = dataElement.getElementMetaData().getTypeInfo();
            log.debug("name=" + ebmlTypeInfo.getName() + ", level=" + ebmlTypeInfo.getLevel());
            switch (state) {
                case NEW:
                    throw new IllegalArgumentException("NEW state should not start with MkvDataElement " + dataElement);
                case BUFFERING_SEGMENT:
                    bufferAndCollectSegment(dataElement);
                    break;
                case BUFFERING_CLUSTER:
                    bufferAndCollectCluster(dataElement);
                    break;
                case BUFFERING_TAG:
                    log.debug("Ignore Tag data: " + ebmlTypeInfo.getName());
                    break;
                default:
                    throw new MkvTracksException("Unknown state " + state);
            }
        }
    }

    /**
     * Return if this composite visitor is done for visiting.
     *
     * @return True if this visitor is done for visiting
     */
    @Override
    public boolean isDone() {
        if (state == MergeState.PAUSE) {
            log.trace("state: PAUSE -> BUFFERING_SEGMENT");
            state = MergeState.BUFFERING_SEGMENT;
            return true;
        } else {
            return false;
        }
    }

    /**
     * It re-throws exception for {@link MkvElementVisitException}
     *
     * @param exception The Exception to re-throw
     * @throws MkvElementVisitException The re-throw exception
     */
    private void wrapIOException(final IOException exception) throws MkvElementVisitException {
        String exceptionMessage = "IOException in merge visitor";
        throw new MkvElementVisitException(exceptionMessage, exception);
    }

    /**
     * Flush cluster that hasn't been written.
     *
     * @throws IOException Exception is thrown when it failed to write to output channel.
     */
    @Override
    public void flush() throws IOException {
        try {
            emitCluster(previousCluster);
            previousCluster = null;
            emitCluster(currentCluster);
            currentCluster = null;
        } catch (MkvElementVisitException exception) {
            throw new IOException(exception.getMessage());
        }
    }

    /**
     * Buffer element of segment (without cluster and tag)
     *
     * @param startMasterElement The beginning of this element. It include EBML ID and Length fields.
     * @throws MkvElementVisitException It's thrown when it failed to write element to buffering channel
     */
    private void bufferAndCollectSegment(final MkvStartMasterElement startMasterElement)
            throws MkvElementVisitException {
        final EBMLTypeInfo ebmlTypeInfo = startMasterElement.getElementMetaData().getTypeInfo();
        if (!isSegmentVerified) {
            if (MkvTypeInfos.SEGMENT.equals(ebmlTypeInfo)) {
                SEGMENT_ELEMENT_WITH_UNKNOWN_LENGTH.rewind();
                try {
                    bufferingSegmentChannel.write(SEGMENT_ELEMENT_WITH_UNKNOWN_LENGTH);
                } catch (IOException exception) {
                    wrapIOException(exception);
                }
            } else {
                if (!isIgnoreTracksElements) {
                    startMasterElement.writeToChannel(bufferingSegmentChannel);
                }
            }
        } else {
            /* After segment is verified, all other level 1 elements should be ignored because we can't put them in
            the middle of cluster stream. */
            log.debug("Ignore " + ebmlTypeInfo.getName());
        }
    }

    /**
     * Buffer element of segment (without cluster and tag)
     *
     * @param dataElement The whole element includes ID, Length, and Data.
     * @throws MkvElementVisitException It's thrown when it failed to write element to buffering channel
     */
    private void bufferAndCollectSegment(final MkvDataElement dataElement)
            throws MkvElementVisitException {
        final EBMLTypeInfo ebmlTypeInfo = dataElement.getElementMetaData().getTypeInfo();
        if (!isSegmentVerified) {
            if (MkvTypeInfos.TIMECODESCALE.equals(ebmlTypeInfo)) {
                timescaleMs = getTimescaleMs(dataElement);
            } else if (!isSegmentEmitted && MkvTypeInfos.TRACKNUMBER.equals(ebmlTypeInfo)) {
                trackNumbers.add(getTrackNumber(dataElement));
            }
            if (!isIgnoreTracksElements) {
                dataElement.writeToChannel(bufferingSegmentChannel);
            }
        } else {
            /* After segment is verified, all other level 1 elements should be ignored because we can't put them in
            the middle of cluster stream. */
            log.debug("Ignore " + ebmlTypeInfo.getName());
        }
    }

    /**
     * Buffer element of cluster
     *
     * @param startMasterElement The beginning of this element. It include EBML ID and Length fields.
     */
    private void bufferAndCollectCluster(final MkvStartMasterElement startMasterElement)
            throws MkvElementVisitException {
        final EBMLTypeInfo ebmlTypeInfo = startMasterElement.getElementMetaData().getTypeInfo();
        if (MkvTypeInfos.CLUSTER.equals(ebmlTypeInfo)) {
            if (currentCluster != null) {
                throw new MkvElementVisitException("There are un-expectedly nested cluster elements",
                        new RuntimeException());
            }
            currentCluster = new MkvCluster(CLUSTER_ELEMENT_WITH_UNKNOWN_LENGTH);
        }
    }

    /**
     * Buffer element of cluster
     *
     * @param dataElement The whole element includes ID, Length, and Data.
     * @throws MkvElementVisitException It's thrown when it failed to write element to buffering channel
     */
    private void bufferAndCollectCluster(final MkvDataElement dataElement)
            throws MkvElementVisitException {
        if (currentCluster == null) {
            throw new MkvElementVisitException("Unable to buffer data element to empty clusters",
                    new RuntimeException());
        }

        final EBMLTypeInfo ebmlTypeInfo = dataElement.getElementMetaData().getTypeInfo();
        if (MkvTypeInfos.TIMECODE.equals(ebmlTypeInfo)) {
            Optional<BigInteger> clusterTimecode = Optional.of((BigInteger) dataElement.getValueCopy().getVal());
            final long clusterTimecodeMs = clusterTimecode.get().longValue();

            if (nextFragmentTimecodeOffsetMs == -1) {
                if (previousCluster != null) {
                    // TODO: we can do better guess if we refer to more than one previous cluster
                    nextFragmentTimecodeOffsetMs = previousCluster.getExpectedNextTimeCode();
                } else {
                    // There is no previous cluster to help us guess
                    nextFragmentTimecodeOffsetMs = 0;
                }
            }

            final long clusterTimecodeUpdatedMs = clusterTimecodeMs + nextFragmentTimecodeOffsetMs / timescaleMs;
            log.trace("Update cluster timecode from " + clusterTimecodeMs + " to " + clusterTimecodeUpdatedMs);
            currentCluster.setAbsoluteTimecode(clusterTimecodeUpdatedMs);
        } else if (MkvTypeInfos.SIMPLEBLOCK.equals(ebmlTypeInfo)) {
            final MkvValue<Frame> frame = dataElement.getValueCopy();

            currentCluster.addSimpleBlock(new MkvSimpleBlock(
                    frame.getVal().getTimeCode(),
                    frame.getVal().getTrackNumber(),
                    dataElement.getIdAndSizeRawBytes(),
                    dataElement.getDataBuffer()));
        } else {
            log.debug("Ignore ebml element: " + ebmlTypeInfo.getName());
        }
    }

    /**
     * Write segment elements to output channel
     */
    private void emitBufferedSegmentData() throws MkvElementVisitException {
        try {
            bufferingSegmentChannel.close();

            outputChannel.write(ByteBuffer.wrap(bufferingSegmentStream.toByteArray()));
            outputChannel.write(ByteBuffer.wrap(tracksVisitor.toMkvRawData()));
            log.info("Wrote segment to output stream");
            isSegmentEmitted = true;
        } catch (IOException exception) {
            wrapIOException(exception);
        }
    }

    private void emitCluster(MkvCluster cluster) throws MkvElementVisitException {
        if (cluster != null) {
            boolean isMissingFrameForTrack = false;
            for (long trackNumber : trackNumbers) {
                if (cluster.getSimpleBlockCountInTrack(trackNumber) == 0) {
                    isMissingFrameForTrack = true;
                }
            }
            if (isMissingFrameForTrack) {
                log.trace("Skip cluster for missing frame for track: " + cluster);
            } else {
                log.trace("Wrote cluster to channel: " + cluster);
                cluster.sort();
                try {
                    cluster.writeToChannel(outputChannel);
                } catch (IOException exception) {
                    wrapIOException(exception);
                }
            }
        }
    }

    /**
     * Get timescale in milliseconds precision from a timescale element
     *
     * @param timescaleElement EBML timescale element
     * @return Timescale in milliseconds precision.
     */
    private long getTimescaleMs(final MkvDataElement timescaleElement) {
        Optional<BigInteger> timescale = Optional.of((BigInteger) timescaleElement.getValueCopy().getVal());
        return timescale.get().longValue() / 1_000_000L;
    }

    /**
     * Get track number from track number element
     *
     * @param trackNumberElement EBML track number
     * @return track number
     */
    private long getTrackNumber(final MkvDataElement trackNumberElement) {
        Optional<BigInteger> trackNumber = Optional.of((BigInteger) trackNumberElement.getValueCopy().getVal());
        return trackNumber.get().longValue();
    }

    /**
     * Fix out-of-order situations.  There might be 2 kinds of out-of-order.
     * (1) Simple blocks out of order within one cluster. This can be fixed by sort simple blocks within that cluster.
     * (2) The absolute timecode of the latest simple block in previous cluster is larger than the earliest simple block
     * in current cluster.  This can be fixed by moving simple block from previous cluster to current cluster.
     */
    private void sortClusters() {
        if (previousCluster != null) {
            previousCluster.sort();
        }
        if (currentCluster != null) {
            currentCluster.sort();
        }
        if (previousCluster != null && currentCluster != null) {
            final short timecodeDiff = (short) (previousCluster.getAbsoluteTimecode()
                    - currentCluster.getAbsoluteTimecode());
            while (!previousCluster.isEmpty() && previousCluster.getLatestSimpleBlockTimecode()
                    >= currentCluster.getAbsoluteTimecode()) {
                MkvSimpleBlock simpleBlock = previousCluster.removeLatestSimpleBlock();

                simpleBlock.updateTimecode(timecodeDiff);
                currentCluster.addSimpleBlock(simpleBlock);

                previousCluster.sort();
                currentCluster.sort();
            }
        }
    }
}
