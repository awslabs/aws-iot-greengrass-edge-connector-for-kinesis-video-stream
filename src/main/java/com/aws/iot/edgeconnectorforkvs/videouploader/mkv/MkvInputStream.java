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
import com.aws.iot.edgeconnectorforkvs.videouploader.visitors.MergeFragmentVisitor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A MKV input stream that support time ordering elements.
 */
@Slf4j
public class MkvInputStream extends InputStream {

    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    private final MergeFragmentVisitor mergeFragmentVisitor =
            MergeFragmentVisitor.createPausable(byteArrayOutputStream);

    private ByteArrayInputStream byteArrayInputStream = null;

    private final StreamingMkvReader streamingMkvReader;

    private boolean isClosed = false;

    /**
     * Constructor of MKV input stream.
     *
     * @param inputStream The MKV input stream which may contains out of order elements.
     */
    public MkvInputStream(@NonNull InputStream inputStream) {
        streamingMkvReader = StreamingMkvReader.createDefault(new InputStreamParserByteSource(inputStream));
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

        try {
            streamingMkvReader.apply(mergeFragmentVisitor);

            if (byteArrayOutputStream.size() > 0) {
                byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            } else {
                log.debug("No data available from input stream");
            }
        } catch (MkvElementVisitException exception) {
            log.error("Unable to parse the input stream");
            closeMkvInputStream();
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
