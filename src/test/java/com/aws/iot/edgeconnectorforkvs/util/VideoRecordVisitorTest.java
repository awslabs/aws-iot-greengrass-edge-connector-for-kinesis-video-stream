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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VideoRecordVisitorTest {

    private static final Long TEST_TIME = 1600000000000L;

    private static Path tempDir;
    private static Path tempVideo1;
    private static Path tempVideo2;
    private static Path tempVideo3;
    private static Path tempVideo4;

    private VideoRecordVisitor videoRecordVisitor;

    @Mock
    private Path mockFile;

    @BeforeAll
    public static void setupForAll() {
        Instant instantNow = Instant.ofEpochMilli(TEST_TIME);
        try {
            tempDir = Files.createTempDirectory("temp");

            // create 4 temp video files with timestamp now-700s, -500s, -300s, -100s
            tempVideo1 = Files.createFile(Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(700)).getTime() + ".mkv"));
            tempVideo2 = Files.createFile(Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(500)).getTime() + ".mkv"));
            tempVideo3 = Files.createFile(Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(300)).getTime() + ".mkv"));
            tempVideo4 = Files.createFile(Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.minusSeconds(100)).getTime() + ".mkv"));

            // Clean up these temp files when exit.
            Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 @SuppressWarnings("unused") BasicFileAttributes attrs) {
                    file.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                                                         @SuppressWarnings("unused") BasicFileAttributes attrs) {
                    dir.toFile().deleteOnExit();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ex) {
            System.out.println("Unable to create temp directory or temp video files!");
        }
    }

    @BeforeEach
    public void setupForEach() {
        videoRecordVisitor = VideoRecordVisitor.builder()
                .recordFilePath(tempDir.toAbsolutePath().toString())
                .build();
    }

    public boolean tempVideoFilesPresent() {
        return tempDir != null && tempVideo1 != null && tempVideo2 != null && tempVideo3 != null && tempVideo4 != null;
    }

    @Test
    public void constructor_toString_notNull() {
        Assertions.assertNotNull(VideoRecordVisitor.builder().toString());
    }

    @Test
    public void constructor_nullRecordPathFromBuilder_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoRecordVisitor.builder()
                        .recordFilePath(null)
                        .build()
        );
    }

    @Test
    public void constructor_nullRecordPathFromFactory_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> VideoRecordVisitor.create(null)
        );
    }

    @Test
    public void listFilesToUpload_nullInputs_throwException() {
        Instant instantNow = Instant.ofEpochMilli(TEST_TIME);
        Date videoUploadingStartTime = Date.from(instantNow.minusSeconds(90));
        Date videoUploadingEndTime = Date.from(instantNow.minusSeconds(10));

        Assertions.assertThrows(NullPointerException.class,
                () -> videoRecordVisitor.listFilesToUpload(null, videoUploadingEndTime));
        Assertions.assertThrows(NullPointerException.class,
                () -> videoRecordVisitor.listFilesToUpload(videoUploadingStartTime, null));
    }

    @Test
    public void listFilesToUpload_invalidFolder_emptyList() {
        Instant instantNow = Instant.ofEpochMilli(TEST_TIME);
        Date videoUploadingStartTime = Date.from(instantNow.minusSeconds(90));
        Date videoUploadingEndTime = Date.from(instantNow.minusSeconds(10));

        videoRecordVisitor = VideoRecordVisitor.builder()
                .recordFilePath("/invalidFolder")
                .build();

        List<VideoFile> videoFiles = videoRecordVisitor.listFilesToUpload(videoUploadingStartTime,
                videoUploadingEndTime);
        Assertions.assertTrue(videoFiles.isEmpty());
    }

    @Test
    public void listFilesToUpload_validInputs_emptyList() {
        Assumptions.assumeTrue(tempVideoFilesPresent());

        Instant instantNow = Instant.ofEpochMilli(TEST_TIME);
        Date videoUploadingStartTime = Date.from(instantNow.minusSeconds(90));
        Date videoUploadingEndTime = Date.from(instantNow.minusSeconds(10));

        // There should be no files in this time range
        List<VideoFile> videoFiles = videoRecordVisitor.listFilesToUpload(videoUploadingStartTime,
                videoUploadingEndTime);

        Assertions.assertTrue(videoFiles.isEmpty());
    }

    @Test
    public void listFilesToUpload_validInputs_nonEmptyList() {
        Assumptions.assumeTrue(tempVideoFilesPresent());

        Instant instantNow = Instant.ofEpochMilli(TEST_TIME);
        Date videoUploadingStartTime = Date.from(instantNow.minusSeconds(600));
        Date videoUploadingEndTime = Date.from(instantNow.minusSeconds(200));

        // list video files between now-600s and now-100s, the result should be 2 video files with timestamp now-500s
        // and now-300s
        List<VideoFile> videoFiles = videoRecordVisitor.listFilesToUpload(videoUploadingStartTime,
                videoUploadingEndTime);

        // Validate the list should match video 2 and video 3
        Assertions.assertEquals(2, videoFiles.size());
        Assertions.assertEquals(tempVideo2.getFileName().toString(), videoFiles.get(0).getName());
        Assertions.assertEquals(tempVideo3.getFileName().toString(), videoFiles.get(1).getName());
    }

    @Test
    public void getDateFromFilename_nullInputs_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> videoRecordVisitor.getDateFromFilename(null));
    }

    @Test
    public void getDateFromFilename_invalidInputs_timestampZero() {
        String invalidVideoFilename = "invalidVideoFilename.mkv";
        Date videoDate = videoRecordVisitor.getDateFromFilename(invalidVideoFilename);
        Assertions.assertEquals(new Date(0L), videoDate);
    }

    @Test
    public void getDateFromFilename_NumberFormatException() {
        //Over length date format
        String invalidVideoFilename = "video_13131312313131313131313131313131313131313131312313213131311313.mkv";
        Date videoDate = videoRecordVisitor.getDateFromFilename(invalidVideoFilename);
        Assertions.assertEquals(new Date(0L), videoDate);
    }

    @Test
    public void getInputStreamFromFile_validInputs_NonNullInputStream() {
        Assumptions.assumeTrue(tempVideoFilesPresent());

        Assertions.assertNotNull(videoRecordVisitor.getInputStreamFromFile(tempVideo1.toFile()));
    }

    @Test
    public void getInputStreamFromFile_invalidInputs_returnNull() {
        InputStream inputStream = videoRecordVisitor.getInputStreamFromFile(new File("invalidFilename"));
        Assertions.assertNull(inputStream);
    }

    @Test
    public void getInputStreamFromFile_nullInputs_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> videoRecordVisitor.getInputStreamFromFile(null));
    }

    @Test
    public void visit_nullFile_noExceptionIsThrown() {
        Assumptions.assumeTrue(tempVideoFilesPresent());

        Assertions.assertDoesNotThrow(() ->
                videoRecordVisitor.visitFile(null, null));
    }

    @Test
    public void visit_nullFilename_noExceptionIsThrown() {
        Assumptions.assumeTrue(tempVideoFilesPresent());

        when(mockFile.getFileName()).thenReturn(null);
        Assertions.assertDoesNotThrow(() ->
                videoRecordVisitor.visitFile(mockFile, null));

    }
}
