package com.aws.iot.edgeconnectorforkvs.util;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.SITEWISE_TIMESTAMP_SWITCH_TIME_IN_MILLISECONDS;

import lombok.NonNull;

import java.util.Date;

public final class TimeUtils {

    private TimeUtils() {
    }

    /***
     * Check whether the given newTimeStamp have the time gap with currentDate longer than
     * SITEWISE_TIMESTAMP_SWITCH_TIME_IN_MILLISECONDS
     * @param newTimeStamp timestamp in long
     * @param currentDate Date
     * @return true|false
     */
    public static boolean isNeedToChangeUpdateTimeStamp(long newTimeStamp, @NonNull Date currentDate) {
        return newTimeStamp - currentDate.getTime() > SITEWISE_TIMESTAMP_SWITCH_TIME_IN_MILLISECONDS;
    }
}
