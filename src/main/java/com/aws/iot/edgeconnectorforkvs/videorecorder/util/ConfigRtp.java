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

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;

/**
 * RTP configurations.
 */
public final class ConfigRtp {
    /**
     * Encode to RTP depay element.
     */
    public static final Map<String, String> DEPAY_INFO;
    static {
        Hashtable<String, String> tmp = new Hashtable<>();

        // video
        tmp.put("H264", "rtph264depay");
        tmp.put("H265", "rtph265depay");
        tmp.put("VP8", "rtpvp8depay");
        // audio
        tmp.put("MPEG4-GENERIC", "rtpmp4gdepay");
        tmp.put("MP4A-LATM", "rtpmp4adepay");
        tmp.put("OPUS", "rtpopusdepay");
        tmp.put("PCMA", "rtppcmadepay");
        tmp.put("PCMU", "rtppcmudepay");

        DEPAY_INFO = Collections.unmodifiableMap(tmp);
    }

    private ConfigRtp() {}
}
