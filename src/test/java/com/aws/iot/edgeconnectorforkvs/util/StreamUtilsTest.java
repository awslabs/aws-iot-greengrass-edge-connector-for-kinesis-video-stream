package com.aws.iot.edgeconnectorforkvs.util;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class StreamUtilsTest {
    @Test
    public void testFlushInputStream() throws IOException {
        //when
        String initialString = "text";
        InputStream targetStream = IOUtils.toInputStream(initialString);

        Thread thread = new Thread(() -> {
            try (final BufferedReader reader
                         = new BufferedReader(new InputStreamReader(targetStream, StandardCharsets.US_ASCII))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    //Read and consume from the InputStream
                }
            } catch (final IOException ex) {
                ex.printStackTrace();
            }

        });
        //then and verify
        thread.start();
        StreamUtils.flushInputStream(targetStream);
    }
}
