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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.Date;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_CALLBACK_INSTANCE;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_DURATION_IN_MINS_KEY;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_STREAM_NAME_KEY;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.JOB_TYPE_KEY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class JobImplementationTest {
    private StartJob startJob;
    private StopJob stopJob;

    @Mock
    private JobExecutionContext jobExecutionContext;
    @Mock
    private JobDetail jobDetail;
    @Mock
    private Scheduler scheduler;
    @Mock
    private SchedulerCallback callbackClass;

    private JobDataMap jobDataMap;
    private static final String JOB_NAME = "test-job";
    private static final String JOB_GROUP = "test-group";
    private static final String JOB_STREAM_NAME = "test-video-stream";
    private static final long JOB_DURATION_IN_MINS = 10;

    @BeforeEach
    public void setup() {
        startJob = new StartJob();
        stopJob = new StopJob();
        jobDataMap = new JobDataMap();
        jobDataMap.put(JOB_TYPE_KEY, Constants.JobType.LOCAL_VIDEO_CAPTURE);
        jobDataMap.put(JOB_DURATION_IN_MINS_KEY, JOB_DURATION_IN_MINS);
        jobDataMap.put(JOB_STREAM_NAME_KEY, JOB_STREAM_NAME);
        jobDataMap.put(JOB_CALLBACK_INSTANCE, callbackClass);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);
    }

    @Test
    public void startJob_execute_getsKey() throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        when(jobDetail.getKey()).thenReturn(jobKey);
        when(scheduler.scheduleJob(any(), any())).thenReturn(new Date());
        when(jobExecutionContext.getScheduler()).thenReturn(scheduler);

        startJob.execute(jobExecutionContext);
        verify(jobDetail).getKey();
    }

    @Test
    public void startJob_execute_throwsException() throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        when(jobDetail.getKey()).thenReturn(jobKey);
        doThrow(SchedulerException.class).when(scheduler).scheduleJob(any(), any());
        when(jobExecutionContext.getScheduler()).thenReturn(scheduler);

        assertThrows(JobExecutionException.class, () -> startJob.execute(jobExecutionContext));
    }

    @Test
    public void stopJob_execute_getsKey() throws JobExecutionException {
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        when(jobDetail.getKey()).thenReturn(jobKey);

        stopJob.execute(jobExecutionContext);
        verify(jobDetail).getKey();
    }

    @Test
    public void stopJob_execute_throwException() throws JobExecutionException {
        // mock
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        jobDataMap.put(JOB_CALLBACK_INSTANCE, callbackClass);

        // when
        when(jobDetail.getKey()).thenReturn(jobKey);
        doThrow(RuntimeException.class).when(callbackClass).schedulerStopTaskCallback(any(), any());

        // verify
        assertThrows(JobExecutionException.class, () -> stopJob.execute(jobExecutionContext));
    }
}
