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

import com.aws.iot.edgeconnectorforkvs.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_CALLBACK_INSTANCE;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_DURATION_IN_MINS_KEY;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_STREAM_NAME_KEY;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_TYPE_KEY;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * Class that implements the Quartz Job interface and
 * provides a way to start video uploading/capturing.
 */
@Slf4j
public class StartJob implements Job {
    /**
     * Quartz requires a public empty constructor so that the
     * scheduler can instantiate the class whenever it needs.
     */
    public StartJob() {
    }

    /**
     * Schedules a Job to stop video upload/capture after defined jobDurationInMins
     *
     * @param scheduler         - Instance of quartz scheduler
     * @param jobType           - Type of Job - Upload/Capture
     * @param streamName        - Name of the Video Stream/Camera Stream
     * @param jobDurationInMins - Number of minutes from now when the stop job should be triggered
     * @throws SchedulerException - quartz scheduler exception
     */
    private void scheduleStopJob(Scheduler scheduler, Constants.JobType jobType, String streamName,
                                 long jobDurationInMins, SchedulerCallback schedulerCallback)
        throws SchedulerException {
        long jobDurationInSecs = Duration.ofMinutes(jobDurationInMins).getSeconds();
        Instant stopTimeInstant = Instant.now().plusSeconds(jobDurationInSecs);
        Date stopTime = Date.from(stopTimeInstant);
        JobDetail jobDetail = newJob(StopJob.class)
            .withIdentity(streamName, jobType.name() + Constants.JOB_ID_STOP_SUFFIX)
            .build();
        Trigger stopTrigger = newTrigger()
            .withIdentity(streamName + "-" + jobDetail.getKey(), jobType.name())
            .startAt(stopTime)
            .build();
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        jobDataMap.put(JOB_STREAM_NAME_KEY, streamName);
        jobDataMap.put(JOB_TYPE_KEY, jobType);
        jobDataMap.put(JOB_DURATION_IN_MINS_KEY, jobDurationInMins);
        jobDataMap.put(JOB_CALLBACK_INSTANCE, schedulerCallback);
        Date ft = scheduler.scheduleJob(jobDetail, stopTrigger);
        log.info(jobDetail.getKey() + " has been scheduled to run at: " + ft);
    }

    /**
     * <p>
     * Called by the <code>{@link org.quartz.Scheduler}</code> when a
     * <code>{@link org.quartz.Trigger}</code> fires that is associated with
     * the <code>Job</code>.
     * </p>
     *
     * @throws JobExecutionException if there is an exception while executing the job.
     */
    public void execute(JobExecutionContext context)
        throws JobExecutionException {
        JobKey jobKey = context.getJobDetail().getKey();
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        String streamName = (String) jobDataMap.get(JOB_STREAM_NAME_KEY);
        Constants.JobType jobType = (Constants.JobType) jobDataMap.get(JOB_TYPE_KEY);
        long jobDurationInMins = (long) jobDataMap.get(JOB_DURATION_IN_MINS_KEY);
        SchedulerCallback schedulerCallback = (SchedulerCallback) jobDataMap.get(JOB_CALLBACK_INSTANCE);
        log.info("StartJob: " + jobKey + " executing at " + new Date());
        log.trace("JobType: " + jobType.name());
        log.trace("Job Duration In Mins: " + jobDurationInMins);
        try {
            scheduleStopJob(context.getScheduler(), jobType, streamName, jobDurationInMins, schedulerCallback);
        } catch (Exception ex) {
            final String errorMessage = String.format("Could not schedule stop job for Job Type: %s, " +
                    "Stream Name: %s, Job Duration: %d mins. %s", jobType.name(), streamName,
                jobDurationInMins, ex.getMessage());
            log.error(errorMessage);
            // After this exception is caught, it will start camera level restart
            throw new JobExecutionException(errorMessage, ex);
        }
        // Call the Controller callback function
        schedulerCallback.schedulerStartTaskCallback(jobType, streamName);
    }
}
