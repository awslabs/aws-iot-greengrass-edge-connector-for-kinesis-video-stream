package com.aws.iot.edgeconnectorforkvs.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Date;

public class TimeUtilsTest {

    @Test
    public void test_isNeedToChangeUpdateTimeStamp() {
        long newTimeStamp = 1636389218000L;
        Date currentDate = new Date(1636388857999L);
        assertTrue(TimeUtils.isNeedToChangeUpdateTimeStamp(newTimeStamp, currentDate));

        currentDate = new Date(1636388858001L);
        assertFalse(TimeUtils.isNeedToChangeUpdateTimeStamp(newTimeStamp, currentDate));
    }
}
