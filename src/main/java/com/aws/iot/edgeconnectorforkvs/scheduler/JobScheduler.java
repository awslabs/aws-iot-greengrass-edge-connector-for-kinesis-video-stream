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
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import javax.inject.Inject;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Class to manage scheduled video upload functionality.
 */
@Slf4j
public class JobScheduler {
    private final SchedulerFactory schedulerFactory;
    private SchedulerCallback schedulerCallback;

    /**
     * Constructor for JobScheduler.
     *
     * @param schedulerCallback - Class which implements the callbacks
     */
    public JobScheduler(@NonNull SchedulerCallback schedulerCallback) {
        this.schedulerFactory = new StdSchedulerFactory();
        this.schedulerCallback = schedulerCallback;
    }

    /**
     * Constructor for JobScheduler.
     *
     * @param schedulerFactory  - Instance of quartz scheduler factory
     * @param schedulerCallback - Class which implements the callbacks
     */
    @Inject
    public JobScheduler(SchedulerFactory schedulerFactory, SchedulerCallback schedulerCallback) {
        this.schedulerFactory = schedulerFactory;
        this.schedulerCallback = schedulerCallback;
    }

    /**
     * Schedules a Job to start video upload/capture. As soon as the start job is triggered,
     * a corresponding stop job is scheduled based on jobDurationInMins
     *
     * @param jobType           - Type of Job - Upload/Capture
     * @param streamName        - Name of the Video Stream/Camera Stream
     * @param startTimeExpr     - Cron Expression defined as minute, hour, day-of-month, month,
     *                          day-of-week, year(optional)
     * @param jobDurationInMins - Number of minutes the started job should run for
     * @throws EdgeConnectorForKVSException - generic exception
     */
    public void scheduleJob(Constants.JobType jobType, String streamName, String startTimeExpr,
                            long jobDurationInMins) {
        try {
            // hardcode seconds part to 0
            startTimeExpr = Integer.toString(Constants.SCHEDULER_SECONDS_BOUNDARY) + " " + startTimeExpr;
            boolean valid = verifyCron(startTimeExpr, jobDurationInMins);
            if (!valid) {
                final String errorMessage = String.format("Error: Invalid Start Time Expression and Job Duration." +
                        "Job Type: %s, Stream Name: %s, StartTime Expr: %s, Job Duration: %d mins." +
                        "Job not scheduled.", jobType.name(), streamName,
                    startTimeExpr, jobDurationInMins);
                log.error(errorMessage);
                throw new EdgeConnectorForKVSException(errorMessage);
            }
            Scheduler sched = null;
            sched = this.schedulerFactory.getScheduler();
            // seconds, minute, hour, day-of-month, month, day-of-week, year(optional)
            log.info("Start Job Cron Expression: " + startTimeExpr);
            JobDetail jobDetail = produceJobDetails(streamName, jobType.name());
            CronTrigger startTrigger = newTrigger()
                .withIdentity(streamName + "-" + jobDetail.getKey(), jobType.name())
                .withSchedule(cronSchedule(startTimeExpr))
                .build();
            JobDataMap jobDataMap = jobDetail.getJobDataMap();
            jobDataMap.put(Constants.JOB_TYPE_KEY, jobType);
            jobDataMap.put(Constants.JOB_DURATION_IN_MINS_KEY, jobDurationInMins);
            jobDataMap.put(Constants.JOB_STREAM_NAME_KEY, streamName);
            jobDataMap.put(Constants.JOB_CALLBACK_INSTANCE, schedulerCallback);
            Date ft = sched.scheduleJob(jobDetail, startTrigger);
            log.info(jobDetail.getKey() + " has been scheduled to run at: " + ft + " and repeat based on expression: "
                + startTrigger.getCronExpression());
        } catch (ParseException | SchedulerException ex) {
            final String errorMessage = String.format("Could not schedule job for Job Type: %s, Stream Name: %s, " +
                    "StartTime Expr: %s, Job Duration: %d mins. %s", jobType.name(), streamName,
                startTimeExpr, jobDurationInMins, ex.getMessage());
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        }
    }

    /**
     * Verifies if there is a overlap between start cron expression and the terminate duration in mins
     *
     * @param startCron      - Cron expression for starting a job
     * @param durationInMins - Duration in mins when the job should be terminated
     * @return - true if valid, false otherwise
     * @throws ParseException - thrown by cron expression parser
     */
    private boolean verifyCron(String startCron, long durationInMins) throws ParseException {
        CronExpression expression = new CronExpression(startCron);
        // We use epoch 0 (1/1/1970 00:00:00) because Quartz scheduler gives
        // any minute divisible by cron expression as the next valid time.
        // For example with the cron expression */11 * * * ? and current time 04:50PM
        // next valid time returned is 04:55PM and next to next valid time returned is
        // 05:00PM which causes validation issues
        Date baseDate = new Date(Instant.EPOCH.getEpochSecond());

        Date first = expression.getNextValidTimeAfter(baseDate);
        if (first == null) {
            // trying to schedule a job in the past?
            return false;
        }
        Date next = expression.getNextValidTimeAfter(first);
        if (next == null) {
            // this is a one-time fire and not a recurring job
            Date currentDate = new Date();
            if (currentDate.after(first)) {
                log.error("Error: Cron is set to start in the past");
                return false;
            }
            return true;
        }

        log.info("verifyCron :: First Time: " + first);
        log.info("verifyCron :: Next Time: " + next);
        long diffInMins = TimeUnit.MILLISECONDS.toMinutes(next.getTime() - first.getTime());
        log.info("Duration in minutes: " + durationInMins);
        log.info("Difference in minutes: " + diffInMins);
        // duration in mins should be less than time between first and next start
        return durationInMins <= diffInMins;
    }

    /**
     * Start the Quartz Scheduler.
     */
    public void start() {
        try {
            Scheduler sched = this.schedulerFactory.getScheduler();
            sched.start();
            log.info("Started Scheduler");
        } catch (SchedulerException ex) {
            final String errorMessage = String.format("Error starting JobScheduler: " + ex.getMessage());
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        }
    }

    /**
     * Stops the Quartz Scheduler for all cameras
     */
    public void stopAllCameras() {
        try {
            log.info("Stopping job scheduler!");
            Scheduler sched = this.schedulerFactory.getScheduler();
            log.info("Shutting Down Scheduler");
            sched.shutdown(true);
        } catch (SchedulerException ex) {
            final String errorMessage = String.format("Error stopping scheduler for all cameras: " + ex.getMessage());
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        }
    }

    /**
     * Stops the Quartz Scheduler for camera level
     */
    public void stop(EdgeConnectorForKVSConfiguration configuration) {
        try {
            Scheduler sched = this.schedulerFactory.getScheduler();
            // Remove jobs for failed camera
            String kvsStreamName = configuration.getKinesisVideoStreamName();
            JobDetail recorderJobDetail = produceJobDetails(
                kvsStreamName, Constants.JobType.LOCAL_VIDEO_CAPTURE.name());
            JobDetail uploaderJobDetail = produceJobDetails(
                kvsStreamName, Constants.JobType.LIVE_VIDEO_STREAMING.name());
            JobKey recorderJobKey = recorderJobDetail.getKey();
            JobKey uploaderJobKey = uploaderJobDetail.getKey();
            if (sched.checkExists(recorderJobKey)) {
                sched.deleteJob(recorderJobKey);
                log.info("Removing recorder job from scheduler for " + recorderJobKey);
            }
            if (sched.checkExists(uploaderJobKey)) {
                sched.deleteJob(uploaderJobKey);
                log.info("Removing uploader job from scheduler for " + uploaderJobKey);
            }
        } catch (SchedulerException ex) {
            final String errorMessage = String.format("Error stopping scheduler for single camera: " + ex.getMessage());
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        }
    }

    private JobDetail produceJobDetails(String kvsStreamName, String jobType) {
        return newJob(StartJob.class)
            .withIdentity(kvsStreamName, jobType + "_START")
            .build();
    }
}
