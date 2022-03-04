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

package com.aws.iot.edgeconnectorforkvs.scheduler;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class JobSchedulerTest implements SchedulerCallback {
    private JobScheduler jobScheduler;
    private static final String STREAM_NAME = "test-video-stream";

    @Mock
    private SchedulerFactory schedulerFactory;
    @Mock
    private Scheduler scheduler;

    private static long END_TIME_TWO_MINS = 2;
    private static long END_TIME_TEN_MINS = 10;
    private static long END_TIME_THIRTY_MINS = 30;
    private static long END_TIME_TWO_HOURS = 120;
    private static String EVERY_ELEVEN_MINS_CRON_EXPR = "*/11 * * * ?";

    @BeforeEach
    public void setup() throws SchedulerException {
        jobScheduler = new JobScheduler(schedulerFactory, this);
    }

    @Test
    public void constructor_validInput_initsScheduler() throws SchedulerException {
        jobScheduler = new JobScheduler(this);
    }

    @Test
    public void start_validInput_startsScheduler() throws SchedulerException {
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        jobScheduler.start();
        verify(scheduler).start();
    }

    @Test
    public void stop_validInput_stopsScheduler() throws SchedulerException {
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        jobScheduler.stopAllCameras();
        verify(scheduler).shutdown(true);
    }

    @Test
    public void stop_singleCamera_stopsScheduler() throws SchedulerException {
        // mock
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = EdgeConnectorForKVSConfiguration.builder()
            .kinesisVideoStreamName(STREAM_NAME)
            .build();

        JobDetail recorderJobDetail = newJob(StartJob.class)
            .withIdentity(STREAM_NAME, Constants.JobType.LOCAL_VIDEO_CAPTURE.name() + "_START")
            .build();
        JobDetail uploaderJobDetail = newJob(StartJob.class)
            .withIdentity(STREAM_NAME, Constants.JobType.LIVE_VIDEO_STREAMING.name() + "_START")
            .build();

        JobKey recorderJobKey = recorderJobDetail.getKey();
        JobKey uploaderJobKey = uploaderJobDetail.getKey();

        // when
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        when(scheduler.checkExists(recorderJobKey)).thenReturn(true);
        when(scheduler.checkExists(uploaderJobKey)).thenReturn(true);
        when(scheduler.deleteJob(recorderJobKey)).thenReturn(true);
        when(scheduler.deleteJob(uploaderJobKey)).thenReturn(true);

        // then
        jobScheduler.stop(edgeConnectorForKVSConfiguration);

        // verify
        verify(scheduler, times(1)).checkExists(recorderJobKey);
        verify(scheduler, times(1)).checkExists(uploaderJobKey);
        verify(scheduler, times(1)).deleteJob(recorderJobKey);
        verify(scheduler, times(1)).deleteJob(uploaderJobKey);
    }

    @Test
    public void start_invalidInput_throwsException() throws SchedulerException {
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        doThrow(SchedulerException.class).when(scheduler).start();
        assertThrows(EdgeConnectorForKVSException.class, () -> jobScheduler.start());
    }

    @Test
    public void stop_invalidInput_throwsException() throws SchedulerException {
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        doThrow(SchedulerException.class).when(scheduler).shutdown(true);
        assertThrows(EdgeConnectorForKVSException.class, () -> jobScheduler.stopAllCameras());
    }

    @Test
    public void scheduleVideoUpload_everyDayRecurringCronInput_executesWorkers() throws SchedulerException {
        // mock
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        when(scheduler.scheduleJob(any(), any())).thenReturn(new Date());

        // Start Upload Job at current time +5 seconds
        Instant startTime = Instant.now().plusSeconds(5);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " * * ?";

        jobScheduler.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING, STREAM_NAME, startCronExpression, END_TIME_THIRTY_MINS);

        // verify
        ArgumentCaptor<JobDetail> jobDetailArgumentCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerArgumentCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobDetailArgumentCaptor.capture(), triggerArgumentCaptor.capture());

        assertEquals(StartJob.class, jobDetailArgumentCaptor.getValue().getJobClass());
    }

    @Test
    public void scheduleVideoUpload_everyYearRecurringCronInput_executesWorkers() throws SchedulerException {
        // mock
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        when(scheduler.scheduleJob(any(), any())).thenReturn(new Date());

        // Start Upload Job at current time +5 seconds
        Instant startTime = Instant.now().plusSeconds(5);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " " +
            localStartTime.getDayOfMonth() + " " + localStartTime.getMonthValue() + " ? *";

        jobScheduler.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING, STREAM_NAME,
            startCronExpression, END_TIME_THIRTY_MINS);

        // verify
        ArgumentCaptor<JobDetail> jobDetailArgumentCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerArgumentCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobDetailArgumentCaptor.capture(), triggerArgumentCaptor.capture());

        assertEquals(StartJob.class, jobDetailArgumentCaptor.getValue().getJobClass());
    }

    @Test
    public void scheduleVideoUpload_everyElevenMinsRecurringCronInput_executesWorkers() throws SchedulerException {
        // mock
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        when(scheduler.scheduleJob(any(), any())).thenReturn(new Date());

        String startCronExpression = EVERY_ELEVEN_MINS_CRON_EXPR;

        jobScheduler.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING, STREAM_NAME,
            startCronExpression, END_TIME_TEN_MINS);

        // verify
        ArgumentCaptor<JobDetail> jobDetailArgumentCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerArgumentCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobDetailArgumentCaptor.capture(), triggerArgumentCaptor.capture());

        assertEquals(StartJob.class, jobDetailArgumentCaptor.getValue().getJobClass());
    }

    @Test
    public void scheduleVideoUpload_oneTimeCronInput_executesWorkers() throws SchedulerException {
        // mock
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        when(scheduler.scheduleJob(any(), any())).thenReturn(new Date());

        // Start Upload Job at current time +60 seconds
        Instant startTime = Instant.now().plusSeconds(60);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " " +
            localStartTime.getDayOfMonth() + " " + localStartTime.getMonthValue() + " ? " +
            localStartTime.getYear();

        jobScheduler.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING, STREAM_NAME, startCronExpression,
            END_TIME_THIRTY_MINS);

        // verify
        ArgumentCaptor<JobDetail> jobDetailArgumentCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerArgumentCaptor = ArgumentCaptor.forClass(Trigger.class);

        verify(scheduler).scheduleJob(jobDetailArgumentCaptor.capture(), triggerArgumentCaptor.capture());

        assertEquals(StartJob.class, jobDetailArgumentCaptor.getValue().getJobClass());
    }

    @Test
    public void scheduleVideoUpload_overlappingCronInput_throwsException() throws SchedulerException {
        // Start job every hour at 00 minute
        String startCronExpression = "0 * * * ? *";

        assertThrows(EdgeConnectorForKVSException.class, () ->
            jobScheduler.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING, STREAM_NAME, startCronExpression,
                END_TIME_TWO_HOURS));
    }

    @Test
    public void scheduleVideoUpload_invalidStartCronExpression_throwsException() throws SchedulerException {
        // Start Upload Job at current time +5 seconds
        Instant startTime = Instant.now().plusSeconds(5);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " * * *";

        assertThrows(EdgeConnectorForKVSException.class, () ->
            jobScheduler.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING, STREAM_NAME, startCronExpression,
                END_TIME_THIRTY_MINS));
    }

    @Test
    public void scheduleVideoCapture_everyDayRecurringCronInput_executesWorkers() throws SchedulerException {
        // mock
        when(schedulerFactory.getScheduler()).thenReturn(scheduler);
        when(scheduler.scheduleJob(any(), any())).thenReturn(new Date());

        // Start Upload Job every day at current time +5 seconds
        Instant startTime = Instant.now().plusSeconds(5);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " * * ?";

        jobScheduler.scheduleJob(Constants.JobType.LOCAL_VIDEO_CAPTURE, STREAM_NAME, startCronExpression,
            END_TIME_THIRTY_MINS);

        // verify
        ArgumentCaptor<JobDetail> jobDetailArgumentCaptor = ArgumentCaptor.forClass(JobDetail.class);
        ArgumentCaptor<Trigger> triggerArgumentCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(jobDetailArgumentCaptor.capture(), triggerArgumentCaptor.capture());

        assertEquals(StartJob.class, jobDetailArgumentCaptor.getValue().getJobClass());
    }

    @Test
    public void scheduleVideoCapture_overlappingCronInput_throwsException() throws SchedulerException {
        // Start job every hour at 00 minute
        String startCronExpression = "0 * * * ? *";

        assertThrows(EdgeConnectorForKVSException.class, () ->
            jobScheduler.scheduleJob(Constants.JobType.LOCAL_VIDEO_CAPTURE, STREAM_NAME, startCronExpression,
                END_TIME_TWO_HOURS));
    }

    @Test
    public void scheduleVideoCapture_invalidStartCronExpression_throwsException() throws SchedulerException {
        // Start Capture Job at current time +60 seconds
        Instant startTime = Instant.now().plusSeconds(60);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " * * *";

        assertThrows(EdgeConnectorForKVSException.class, () ->
            jobScheduler.scheduleJob(Constants.JobType.LOCAL_VIDEO_CAPTURE, STREAM_NAME, startCronExpression,
                END_TIME_THIRTY_MINS));
    }

    @Test
    public void scheduleVideoCapture_timeInPast_throwsException() throws SchedulerException {
        // Start Upload Job at current time -120 seconds
        Instant startTime = Instant.now().minusSeconds(120);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " " +
            localStartTime.getDayOfMonth() + " " + localStartTime.getMonthValue() + " ? " +
            localStartTime.getYear();

        assertThrows(EdgeConnectorForKVSException.class, () ->
            jobScheduler.scheduleJob(Constants.JobType.LOCAL_VIDEO_CAPTURE, STREAM_NAME, startCronExpression,
                END_TIME_THIRTY_MINS));
    }

    @Test
    @Disabled
    public void test_realObjects_happyPath() throws InterruptedException {
        SchedulerFactory sf = new StdSchedulerFactory();
        JobScheduler js = new JobScheduler(sf, this);
        // Start Upload Job at current time +60 seconds
        Instant startTime = Instant.now().plusSeconds(60);
        LocalDateTime localStartTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String startCronExpression = localStartTime.getMinute() + " " + localStartTime.getHour() + " " +
            localStartTime.getDayOfMonth() + " " + localStartTime.getMonthValue() + " ? " +
            localStartTime.getYear();

        js.scheduleJob(Constants.JobType.LIVE_VIDEO_STREAMING, STREAM_NAME, startCronExpression,
            END_TIME_TWO_MINS);

        js.start();
        Thread.sleep(60000 * 3);

    }

    @Override
    public void schedulerStartTaskCallback(Constants.@NonNull JobType jobType, @NonNull String streamName) {

    }

    @Override
    public void schedulerStopTaskCallback(Constants.@NonNull JobType jobType, @NonNull String streamName) {

    }
}
