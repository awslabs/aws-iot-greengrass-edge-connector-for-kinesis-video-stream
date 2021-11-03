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

import com.aws.iot.edgeconnectorforkvs.util.VideoRecordVisitor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.File;
import java.util.Date;

/**
 * This class adds information to its base type File.
 */
public class VideoFile extends File {

    private final Date videoDate;

    @Getter
    @Setter
    private boolean isParsed = false;

    @Getter
    @Setter
    private boolean isUploaded = false;

    /**
     * Constructor of VideoFile.
     *
     * @param pathname Path of the file
     */
    public VideoFile(String pathname) {
        super(pathname);

        videoDate = VideoRecordVisitor.getDateFromFilename(this.getName());
    }

    /**
     * Constructor of VideoFile.
     *
     * @param file File of this video file
     */
    public VideoFile(File file) {
        this((file == null) ? "" : file.getAbsolutePath());
    }

    /**
     * Return the date of this video file.
     *
     * @return The date of this video file
     */
    public Date getVideoDate() {
        return new Date(videoDate.getTime());
    }

    /**
     * Return if 2 video files are equal.
     *
     * @param obj The other video file
     * @return true if they are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!obj.getClass().equals(this.getClass())) {
            return false;
        }
        VideoFile other = (VideoFile) obj;
        return new EqualsBuilder()
                .append(videoDate, other.getVideoDate())
                .append(isParsed, other.isParsed())
                .append(isUploaded, other.isUploaded())
                .isEquals();
    }

    /**
     * Return hash code of this video file.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(videoDate)
                .append(isParsed)
                .append(isUploaded)
                .toHashCode();
    }
}
