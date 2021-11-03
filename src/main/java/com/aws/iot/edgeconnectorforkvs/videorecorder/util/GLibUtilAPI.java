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

import java.util.HashMap;
import org.freedesktop.gstreamer.lowlevel.GNative;
import org.freedesktop.gstreamer.lowlevel.GTypeMapper;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/**
 * GLib utility.
 */
@SuppressWarnings("MethodName")
interface GLibUtilAPI extends Library {
    GLibUtilAPI GLIB_API =
            GNative.loadLibrary("glib-2.0", GLibUtilAPI.class, new HashMap<String, Object>() {
                {
                    put(Library.OPTION_TYPE_MAPPER, new GTypeMapper());
                }
            });

    Pointer g_strdup(String str);
}
