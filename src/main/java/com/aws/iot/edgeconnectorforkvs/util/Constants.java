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

package com.aws.iot.edgeconnectorforkvs.util;

public final class Constants {


    private Constants() {
    };

    public static final long STREAM_MANAGER_MAX_STREAM_SIZE = 268435456L; // 256MB
    public static final long STREAM_MANAGER_STREAM_SEGMENT_SIZE = 16777216L;  // 16MB
    public static final int MILLI_SECOND_TO_SECOND = 1000;
    public static final int RECORDER_RESTART_TIME_GAP_MILLI_SECONDS = 60 * 1000; // 1 minutes
    public static final int UPLOADER_WAIT_FOR_ACKS_DELAY_MILLI_SECONDS = 3 * 1000; // 3 seconds
    public static final int DEFAULT_CREATED_KVS_DATA_RETENTION_TIME_IN_HOURS = 7 * 24; // 7 dates
    // Temporarily set this to 1 until the issue with overwriting values on same timestamp is figured out
    public static final long STREAM_MANAGER_SITEWISE_BATCH_SIZE = 1L;
    public static final long SITEWISE_TIMESTAMP_SWITCH_TIME_IN_MILLISECONDS = 6 * 60 * 1000; // 6 hours
    public static final long INIT_LOCK_TIMEOUT_IN_SECONDS = 10L; // 10 seconds

    public static final String JOB_DURATION_IN_MINS_KEY = "JobDurationInMins";
    public static final String JOB_TYPE_KEY = "JobType";
    public static final String JOB_STREAM_NAME_KEY = "StreamName";
    public static final String JOB_CALLBACK_INSTANCE = "CallbackInstance";
    public static final int SCHEDULER_SECONDS_BOUNDARY = 0;
    public static final String JOB_ID_STOP_SUFFIX = "_STOP";
    public static final int EXTRA_THREADS_PER_POOL = 2;
    public static final int RECORDING_JOB_WAIT_TIME_IN_SECS = 5;
    public static final int WAIT_TIME_BEFORE_POLLING_IN_MILLISECS = 30000;
    public static final int WAIT_TIME_BEFORE_RESTART_IN_MILLISECS = 30000;
    public enum JobType { LOCAL_VIDEO_CAPTURE, LIVE_VIDEO_STREAMING };

    /********************************* For DiskManager ********************************/
    // Time delay for first invoke file clean up thread in minute
    public static final int FILE_CLEANER_SERVICE_FIRST_EXECUTE_TIME_DELAY_IN_MINUTES = 1;
    // File clean up thread running frequency in minute
    public static final int FILE_CLEANER_SERVICE_EXECUTE_FREQUENCY_IN_MINUTES = 1;
    // The maximum expected time gap for video files when doing recording
    public static final int MAX_EXPECTED_VIDEO_FILE_TIME_GAP_IN_MINUTES = 2;
    // The maximum time gap for overriding SiteWise property's timezone, use 6 days
    public static final int MAX_TIME_GAP_FOR_BACKFILL_SITEWISE_TIMESTAMP_IN_MINUTES = 6 * 24 * 60;
    public static final String WORK_DIR_ROOT_PATH = "./";
    public static final String PATH_DELIMITER = "/";
    public static final String VIDEO_FILENAME_PREFIX = "video_";
    public static final String VIDEO_FILENAME_POSTFIX = ".mkv";
    public static final String VIDEO_FILENAME_UPLOADED_POSTFIX = "_uploaded.mkv";

    /********************************* For SiteWise module ********************************/
    public static final String HUB_DEVICE_SITE_WISE_ASSET_MODEL_PREFIX = "EdgeConnectorForKVSHubModel";
    public static final String SITE_WISE_VIDEO_UPLOAD_REQUEST_MEASUREMENT_NAME = "VideoUploadRequest";
    public static final String SITE_WISE_VIDEO_UPLOADED_TIME_RANGE_MEASUREMENT_NAME = "VideoUploadedTimeRange";
    public static final String SITE_WISE_VIDEO_RECORDED_TIME_RANGE_MEASUREMENT_NAME = "VideoRecordedTimeRange";
    public static final String SITE_WISE_CACHED_VIDEO_AGE_OUT_ON_EDGE_MEASUREMENT_NAME = "CachedVideoAgeOutOnEdge";
    public static final String SECRETS_MANAGER_SECRET_KEY = "RTSPStreamURL";
    public static final String MQTT_LIVE_VIDEO_UPLOAD_REQUEST_KEY = "live";
    public static final String LIVE_STREAMING_STOP_TIMER_PREFIX = "StopLiveTimer";
    public static final long LIVE_STREAMING_STOP_TIMER_DELAY_IN_SECONDS = 5 * 60;
    public static final String START_TIME_EXPR_ALWAYS = "* * * * *";
    public static final String START_TIME_EXPR_NEVER = "-";

    /********************************* For environment variable ********************************/
    public static final String ENV_VAR_REGION = "AWS_REGION";
    public static final String ENV_VAR_IOT_THING_NAME = "AWS_IOT_THING_NAME";

    public static final int ARG_INDEX_SITE_WISE_ASSET_ID_FOR_HUB = 0;

    // To be used for debugging
    public static String getCallingFunctionName(int lvl) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[lvl + 1];
        String methodName = e.getMethodName();
        return methodName;
    }
}
