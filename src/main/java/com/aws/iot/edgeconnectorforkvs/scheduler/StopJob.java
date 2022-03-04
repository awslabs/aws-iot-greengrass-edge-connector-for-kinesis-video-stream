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
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;

import java.util.Date;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_CALLBACK_INSTANCE;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_STREAM_NAME_KEY;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_TYPE_KEY;

/**
 * Class that implements the Quartz Job interface and
 * provides a way to stop video uploading/capturing.
 */
@Slf4j
public class StopJob implements Job {
    /**
     * Quartz requires a public empty constructor so that the
     * scheduler can instantiate the class whenever it needs.
     */
    public StopJob() {
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
        SchedulerCallback schedulerCallback = (SchedulerCallback) jobDataMap.get(JOB_CALLBACK_INSTANCE);
        log.info("StopJob: " + jobKey + " executing at " + new Date());
        log.trace("JobType: " + jobType.name());
        log.trace("StreamName: " + streamName);
        try {
            schedulerCallback.schedulerStopTaskCallback(jobType, streamName);
        } catch (Exception ex) {
            final String errorMessage = String.format("Exception on StopJob Execution. " +
                "Stream Name: %s, %s", streamName, ex.getMessage());
            log.error(errorMessage);
            // After this exception is caught, it will start camera level restart
            throw new JobExecutionException(errorMessage, ex);
        }
    }
}
