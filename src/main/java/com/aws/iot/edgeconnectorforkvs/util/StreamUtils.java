package com.aws.iot.edgeconnectorforkvs.util;

import java.io.IOException;
import java.io.InputStream;

public class StreamUtils {
    public static void flushInputStream(InputStream inputStream) throws IOException {
        int bytesAvailable = inputStream.available();

        do {
            byte[] b = new byte[bytesAvailable];
            inputStream.read(b);
            bytesAvailable = inputStream.available();
        } while (bytesAvailable > 0);
    }
}
