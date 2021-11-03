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

import java.util.EnumMap;
import com.aws.iot.edgeconnectorforkvs.videorecorder.model.ContainerType;

/**
 * Muxer configurations.
 */
public final class ConfigMuxer {
    /**
     * Container type to MUXER element.
     */
    public static final EnumMap<ContainerType, MuxerProperty> CONTAINER_INFO;
    static {
        CONTAINER_INFO = new EnumMap<>(ContainerType.class);

        // MATROSKA
        CONTAINER_INFO.put(ContainerType.MATROSKA, new MuxerProperty("matroskamux", "mkv"));
        CONTAINER_INFO.get(ContainerType.MATROSKA).getGeneralProp().put("offset-to-zero", true);
        CONTAINER_INFO.get(ContainerType.MATROSKA).getAppPathProp().put("streamable", true);

        // MP4
        CONTAINER_INFO.put(ContainerType.MP4, new MuxerProperty("mp4mux", "mp4"));
    }

    private ConfigMuxer() {}
}
