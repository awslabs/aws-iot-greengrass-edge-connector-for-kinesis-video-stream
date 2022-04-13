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

import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.MkvStartMasterElement;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.aws.iot.edgeconnectorforkvs.videouploader.TestUtil;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.MkvStatistics;
import com.aws.iot.edgeconnectorforkvs.videouploader.model.VideoFile;
import com.aws.iot.edgeconnectorforkvs.videouploader.visitors.MergeFragmentVisitor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class MkvFilesInputStreamTest {

    private static final Long TEST_TIME = 1600000000000L;

    private static boolean isVideoFilesAvailable = false;
    private static Path tempVideoPath1;
    private static Path tempVideoPath2;
    private static Path tempVideoAudioPath;
    private static Path tempVideoWithZeroSizePath;

    private MkvFilesInputStream mkvInputStream;

    @Mock
    private MergeFragmentVisitor mockMergeFragmentVisitor;

    @Mock
    private StreamingMkvReader mockStreamingMkvReader;

    @Mock
    private ByteArrayInputStream mockByteArrayInputStream;

    @BeforeAll
    public static void setupForAll() {
        Instant instantNow = Instant.ofEpochMilli(TEST_TIME);
        try {
            Path tempDir = Files.createTempDirectory("temp");
            tempVideoPath1 = Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow).getTime() + ".mkv");
            tempVideoPath2 = Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.plusMillis(10)).getTime() + ".mkv");
            tempVideoAudioPath = Paths.get(tempDir.toString(),
                    "video_" + Date.from(instantNow.plusMillis(20)).getTime() + ".mkv");

            byte[] sampleVideo = TestUtil.createSampleVideo(false);
            byte[] sampleVideoAudio = TestUtil.createSampleVideo(true);
            if (sampleVideo != null && sampleVideoAudio != null) {
                Files.copy(new ByteArrayInputStream(sampleVideo), tempVideoPath1);
                Files.copy(new ByteArrayInputStream(sampleVideo), tempVideoPath2);
                Files.copy(new ByteArrayInputStream(sampleVideoAudio), tempVideoAudioPath);
                tempVideoWithZeroSizePath = Files.createFile(Paths.get(tempDir.toString(),
                        "video_" + Date.from(instantNow.plusMillis(30)).getTime() + ".mkv"));

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

                isVideoFilesAvailable = true;
            }
        } catch (Exception ex) {
            log.error("Unable to create temp directory or temp video files! " + ex.getMessage());
        }
    }

    @Test
    public void available_unavailableFile_returnNegativeByteCount() {
        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile("unavailableFile"));

        // Do test
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());

        // Verify
        Assertions.assertEquals(-1, mkvInputStream.available());
    }

    @Test
    public void available_zeroSizedFile_returnNegativeByteCount() {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoWithZeroSizePath.toFile()));

        // Do test
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());

        // Verify
        Assertions.assertEquals(-1, mkvInputStream.available());
    }

    @Test
    public void available_closeStream_negativeByteCount() {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoPath2.toFile()));

        // Do test
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());
        mkvInputStream.close();

        Assertions.assertEquals(-1, mkvInputStream.available());
    }

    @Test
    public void wantMoreData_MkvElementVisitException_noExceptionIsThrown()
            throws NoSuchFieldException, IllegalAccessException, MkvElementVisitException {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        // Setup test
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoPath2.toFile()));
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());

        // Mock it
        Field field = MkvFilesInputStream.class.getDeclaredField("mergeFragmentVisitor");
        field.setAccessible(true);
        field.set(mkvInputStream, mockMergeFragmentVisitor);

        doThrow(MkvElementVisitException.class).when(mockMergeFragmentVisitor).visit(any(MkvStartMasterElement.class));

        // Do test
        Assertions.assertDoesNotThrow(()->mkvInputStream.available());
    }

    @Test
    public void wantMoreData_flushThrowsIOException_noExceptionIsThrown()
            throws NoSuchFieldException, IllegalAccessException, IOException {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        // Setup test
        final List<VideoFile> filesToMerge = new ArrayList<>();
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());

        // Mock it
        Field field = MkvFilesInputStream.class.getDeclaredField("mergeFragmentVisitor");
        field.setAccessible(true);
        field.set(mkvInputStream, mockMergeFragmentVisitor);

        doThrow(IOException.class).when(mockMergeFragmentVisitor).flush();

        // Do test
        Assertions.assertDoesNotThrow(()->mkvInputStream.available());
    }

    @Test
    public void constructor_nullInput_throwException() {
        Assertions.assertThrows(NullPointerException.class,
                () -> mkvInputStream = new MkvFilesInputStream(null));
    }

    @Test
    public void close_visitorThrowException_noExceptionIsThrown()
            throws Exception {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        // Setup test
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoPath2.toFile()));
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());

        // Mock it
        Field field = MkvFilesInputStream.class.getDeclaredField("mergeFragmentVisitor");
        field.setAccessible(true);
        field.set(mkvInputStream, mockMergeFragmentVisitor);

        doThrow(IOException.class).when(mockMergeFragmentVisitor).flush();

        // Do test
        Assertions.assertDoesNotThrow(()->mkvInputStream.close());
    }

    @Test
    public void closeMkvInputStream_throwException_noExceptionIsThrown()
            throws NoSuchFieldException, IllegalAccessException, IOException {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        // Setup test
        final List<VideoFile> filesToMerge = new ArrayList<>();
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());

        // Mock it
        Field field = MkvFilesInputStream.class.getDeclaredField("byteArrayInputStream");
        field.setAccessible(true);
        field.set(mkvInputStream, mockByteArrayInputStream);

        doThrow(IOException.class).when(mockByteArrayInputStream).close();

        // Do test
        Assertions.assertDoesNotThrow(()->mkvInputStream.close());
    }

    @Test
    public void readBuffer_twoVideoSamples_videoMerged() {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        byte[] buffer = new byte[1024];
        int readLen;

        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoPath2.toFile()));

        // Create answer
        byte[] answer = createMergedVideo();

        // Do test
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());
        while ((readLen = mkvInputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, readLen);
        }
        mkvInputStream.close();
        byte[] result = outputStream.toByteArray();

        // Verify answer
        Assertions.assertArrayEquals(answer, result);
    }

    @Test
    public void read_twoDifferentSamples_mergedFirstVideoOnly() {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        byte[] buffer = new byte[1024];
        int readLen;

        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoAudioPath.toFile()));

        // Create answer
        byte[] answer = TestUtil.createSampleVideo(false);

        // Do test
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());
        while ((readLen = mkvInputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, readLen);
        }
        mkvInputStream.close();
        byte[] result = outputStream.toByteArray();

        // Verify answer
        Assertions.assertArrayEquals(answer, result);
    }

    @Test
    public void readOneByte_twoVideoSamples_videoMerged() {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        int b;

        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoPath2.toFile()));

        // Create answer
        byte[] answer = createMergedVideo();

        // Do test
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());
        while ((b = mkvInputStream.read()) != -1) {
            outputStream.write(b);
        }
        mkvInputStream.close();

        // Verify answer
        Assertions.assertArrayEquals(answer, outputStream.toByteArray());
    }

    @Test
    public void markSupported_validSampleVideo_returnFalse() {
        Assumptions.assumeTrue(isVideoFilesAvailable);

        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoPath2.toFile()));

        // Do test
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());
        mkvInputStream.close();

        Assertions.assertFalse(mkvInputStream.markSupported());
    }

    private static byte[] createMergedVideo() {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(TestUtil.createTracksHeader(false));
            byteArrayOutputStream.write(TestUtil.createClusterHeader(0));
            byteArrayOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            byteArrayOutputStream.write(TestUtil.createClusterHeader(10));
            byteArrayOutputStream.write(TestUtil.createSimpleBlock((short) 0, 1));
            byteArrayOutputStream.close();
            return byteArrayOutputStream.toByteArray();
        } catch (Exception exception) {
            log.error("Unable to create sample video");
        }
        return null;
    }

    @Test
    public void getStats_validSampleVideo_correctSrcAndSinkCnt() {
        Assumptions.assumeTrue(isVideoFilesAvailable);
        MkvStatistics stats;

        // Create input
        final List<VideoFile> filesToMerge = new ArrayList<>();
        filesToMerge.add(new VideoFile(tempVideoPath1.toFile()));
        filesToMerge.add(new VideoFile(tempVideoPath2.toFile()));

        // Do test
        mkvInputStream = new MkvFilesInputStream(filesToMerge.listIterator());
        stats = mkvInputStream.getStats();
        Assertions.assertEquals(stats.getMkvSrcReadCnt(), 0);
        Assertions.assertEquals(stats.getMkvSinkReadCnt(), 0);

        mkvInputStream.available();
        stats = mkvInputStream.getStats();
        Assertions.assertEquals(stats.getMkvSrcReadCnt(), 126);
        Assertions.assertEquals(stats.getMkvSinkReadCnt(), 0);

        mkvInputStream.read();
        stats = mkvInputStream.getStats();
        Assertions.assertEquals(stats.getMkvSrcReadCnt(), 126);
        Assertions.assertEquals(stats.getMkvSinkReadCnt(), 1);

        byte[] tempBuf = new byte[10];
        mkvInputStream.read(tempBuf, 0, 10);
        stats = mkvInputStream.getStats();
        Assertions.assertEquals(stats.getMkvSrcReadCnt(), 126);
        Assertions.assertEquals(stats.getMkvSinkReadCnt(), 11);
    }
}
