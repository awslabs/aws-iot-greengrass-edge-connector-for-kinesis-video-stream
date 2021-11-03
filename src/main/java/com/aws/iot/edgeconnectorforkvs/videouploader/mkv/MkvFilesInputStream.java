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

package com.aws.iot.edgeconnectorforkvs.videouploader.mkv;

import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.VideoFile;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions.MergeFragmentException;
import com.aws.iot.edgeconnectorforkvs.videouploader.visitors.MergeFragmentVisitor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.ListIterator;

/**
 * A MKV input stream that support time ordering elements and merging 2 different MKV files.
 */
@Slf4j
public class MkvFilesInputStream extends InputStream {

    private final ListIterator<VideoFile> mkvIterator;

    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    private final MergeFragmentVisitor mergeFragmentVisitor = MergeFragmentVisitor.create(byteArrayOutputStream);

    private ByteArrayInputStream byteArrayInputStream = null;

    private Date mkvStartTime = null;

    private boolean isClosed = false;

    /**
     * Constructor of MKV files input stream.
     *
     * @param mkvIterator A file iterator that contains all MKV files
     */
    public MkvFilesInputStream(@NonNull ListIterator<VideoFile> mkvIterator) {
        this.mkvIterator = mkvIterator;
    }

    @Override
    public int available() {
        if (isClosed) {
            return -1;
        } else {
            wantMoreData();
            if (byteArrayInputStream == null) {
                return -1;
            } else {
                return byteArrayInputStream.available();
            }
        }
    }

    @Override
    public void close() {
        closeMkvInputStream();
        try {
            // FIXME: The data flushed here are not able to read out.
            mergeFragmentVisitor.flush();
        } catch (IOException exception) {
            log.debug("Failed to do flush");
        }
        isClosed = true;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read() {
        if (available() >= 0) {
            return byteArrayInputStream.read();
        } else {
            return -1;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (available() >= 0) {
            return byteArrayInputStream.read(b, off, len);
        } else {
            return -1;
        }
    }

    private void wantMoreData() {
        if (byteArrayInputStream != null) {
            if (byteArrayInputStream.available() > 0) {
                return;
            } else {
                closeMkvInputStream();
            }
        }

        // We don't have data to read in current input stream.  Try to get data from next file.
        while (byteArrayInputStream == null) {
            if (!mkvIterator.hasNext()) {
                break;
            }

            final VideoFile mkvFile = mkvIterator.next();
            final Date mkvTimestamp = mkvFile.getVideoDate();
            if (mkvStartTime == null) {
                mkvStartTime = mkvTimestamp;
            }

            try (FileInputStream fileInputStream = new FileInputStream(mkvFile)) {
                final StreamingMkvReader streamingMkvReader =
                        StreamingMkvReader.createDefault(
                                new InputStreamParserByteSource(fileInputStream));

                mergeFragmentVisitor.setNextFragmentTimecodeOffsetMs(mkvTimestamp.getTime() - mkvStartTime.getTime());
                streamingMkvReader.apply(mergeFragmentVisitor);
                mkvFile.setParsed(true);

                if (byteArrayOutputStream.size() > 0) {
                    byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                } else {
                    log.info("File: {} doesn't contain any video data, skip merging.", mkvFile.getAbsolutePath());
                    closeMkvInputStream();
                }
            } catch (FileNotFoundException exception) {
                log.error("File not found " + mkvFile.getAbsolutePath());
            } catch (IOException exception) {
                log.error("Failed to close file: " + mkvFile.getAbsolutePath());
            } catch (MkvElementVisitException exception) {
                log.error("Unable to parse " + mkvFile.getAbsolutePath());
            } catch (MergeFragmentException exception) {
                log.error("Failed to merge file: " + mkvFile.getAbsolutePath());
                mkvIterator.previous();
                closeMkvInputStream();
                break;
            }
        }

        if (byteArrayInputStream == null) {
            // We don't have data after iterate all files that can be merged, try to flush and get some data
            try {
                mergeFragmentVisitor.flush();
                if (byteArrayOutputStream.size() > 0) {
                    byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                } else {
                    // No more available date, close it.
                    log.info("No more mkv data available to read");
                    close();
                }
            } catch (IOException exception) {
                log.debug("Failed to flush visitor");
            }
        }
    }

    private void closeMkvInputStream() {
        byteArrayOutputStream.reset();
        if (byteArrayInputStream != null) {
            try {
                byteArrayInputStream.close();
            } catch (IOException exception) {
                log.error("Unable to close mkvInputStream");
            } finally {
                byteArrayInputStream = null;
            }
        }
    }
}
