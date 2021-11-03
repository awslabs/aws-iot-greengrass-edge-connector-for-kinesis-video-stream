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

package com.aws.iot.edgeconnectorforkvs.util;

import com.aws.iot.edgeconnectorforkvs.videouploader.model.VideoFile;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A video record selector that helps to filter out videos within specific period.
 */
@Slf4j
public class VideoRecordVisitor extends SimpleFileVisitor<Path> {
    /* A pre-defined filename that specifies data in the precision of milliseconds. */
    private static final String VIDEO_FILENAME_FORMAT = Constants.VIDEO_FILENAME_PREFIX + "\\d+\\"
            + Constants.VIDEO_FILENAME_POSTFIX;

    /* A pattern for paring date in the filename */
    private static final String VIDEO_FILENAME_DATE_PATTERN = "(\\d+)";

    private String recordFilePath;

    private Date videoUploadingStartTime;

    private Date videoUploadingEndTime;

    private final List<VideoFile> videoFiles = new ArrayList<>();

    /**
     * The factory create of VideoRecordVisitor.
     *
     * @param recordFilePath The record path of videos.
     * @return The VideoRecordVisitor instance
     */
    @Builder
    public static VideoRecordVisitor create(@NonNull String recordFilePath) {
        VideoRecordVisitor visitor = new VideoRecordVisitor();
        visitor.recordFilePath = recordFilePath;
        return visitor;
    }

    @Override
    public FileVisitResult visitFile(Path file, @SuppressWarnings("unused") BasicFileAttributes attrs) {
        if (file != null) {
            final Path filename = file.getFileName();
            if (filename != null) {
                final Date videoTime = getDateFromFilename(filename.toString());
                if (videoUploadingStartTime.before(videoTime) && videoTime.before(videoUploadingEndTime)) {
                    videoFiles.add(new VideoFile(file.toFile()));
                }
            }
        }
        return FileVisitResult.CONTINUE;
    }

    /**
     * List all files that are in the record file path, match the pre-defined filename format, and its {@link Date} is
     * between videoUploadingStartTime and videoUploadingEndTime.
     *
     * @param videoUploadingStartTime Video upload start time
     * @param videoUploadingEndTime   Video upload end time
     * @return An list iterator
     */
    public List<VideoFile> listFilesToUpload(@NonNull Date videoUploadingStartTime,
                                             @NonNull Date videoUploadingEndTime) {
        this.videoFiles.clear();
        this.videoUploadingStartTime = new Date(videoUploadingStartTime.getTime());
        this.videoUploadingEndTime = new Date(videoUploadingEndTime.getTime());

        try {
            Files.walkFileTree(Paths.get(recordFilePath), this);
        } catch (IOException ex) {
            log.error("Failed to retrieve file list");
        }

        videoFiles.sort((file1, file2) -> {
            final Date date1 = getDateFromFilename(file1.getName());
            final Date date2 = getDateFromFilename(file2.getName());
            return date1.compareTo(date2);
        });

        return new ArrayList<VideoFile>(videoFiles);
    }

    /**
     * Return {@link Date} from pre-defined filename format.
     *
     * @param filename filename to parse.
     * @return {@link Date} parsed from filename, or zero otherwise.
     */
    public static Date getDateFromFilename(@NonNull String filename) {
        if (filename.matches(VIDEO_FILENAME_FORMAT)) {
            final Pattern pattern = Pattern.compile(VIDEO_FILENAME_DATE_PATTERN);
            final Matcher matcher = pattern.matcher(filename);
            try {
                matcher.find();
                long timestamp = Long.parseLong(matcher.group());
                return new Date(timestamp);
            } catch (NumberFormatException e) {
                return new Date(0L);
            }
        }
        return new Date(0L);
    }

    /**
     * Return {@link Date} from pre-defined filename format.
     *
     * @param filePath filename to parse.
     * @return {@link Date} parsed from filename, or zero otherwise.
     */
    public static Date getDateFromFilePath(@NonNull Path filePath) {
        Path fileNamePath = filePath.getFileName();
        if (fileNamePath != null && fileNamePath.toString() != null) {
            return getDateFromFilename(fileNamePath.toString());
        }
        return new Date(0L);
    }

    /**
     * It's a wrapper to ignore exception for opening a FileInputStream.
     *
     * @param videoFile filename to open
     * @return {@link InputStream} from {@link FileInputStream}
     */
    public InputStream getInputStreamFromFile(@NonNull File videoFile) {
        try {
            return new FileInputStream(videoFile);
        } catch (FileNotFoundException ex) {
            log.error("File " + videoFile.getName() + " is not found");
        }

        return null;
    }

    private static String generateUploadedFilename(Date date) {
        return Constants.VIDEO_FILENAME_PREFIX + date.getTime() + Constants.VIDEO_FILENAME_UPLOADED_POSTFIX;
    }

    /**
     * Mark a video as an uploaded one by renaming it.
     *
     * @param videoFile The video file to be marked.
     */
    public static void markVideoAsUploaded(VideoFile videoFile) {
        String uploadedFilename = generateUploadedFilename(videoFile.getVideoDate());

        try {
            Path basePath = Paths.get(videoFile.getAbsolutePath()).getParent();
            if (basePath != null) {
                Path uploadedPath = Paths.get(basePath.toString(), uploadedFilename);
                Files.move(videoFile.toPath(), uploadedPath);
            }
        } catch (IOException exception) {
            log.warn(exception.getMessage());
        }
    }
}
