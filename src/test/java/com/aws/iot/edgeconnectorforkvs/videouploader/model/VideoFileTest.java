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

package com.aws.iot.edgeconnectorforkvs.videouploader.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VideoFileTest {

    @Test
    public void equals_differentObject_returnFalse() {
        VideoFile videoFile = new VideoFile("video_0.mkv");
        VideoFile otherVideoFile = new VideoFile("video_1.mkv");
        VideoFile sameVideoFile = new VideoFile("video_0.mkv");

        Assertions.assertNotEquals(videoFile, null);
        Assertions.assertNotEquals(videoFile, "other");
        Assertions.assertNotEquals(videoFile, otherVideoFile);
        Assertions.assertEquals(videoFile, sameVideoFile);

        otherVideoFile.setParsed(true);
        Assertions.assertNotEquals(videoFile, otherVideoFile);

        otherVideoFile.setUploaded(true);
        Assertions.assertNotEquals(videoFile, otherVideoFile);

        Assertions.assertNotEquals(videoFile.hashCode(), otherVideoFile.hashCode());
    }
}
