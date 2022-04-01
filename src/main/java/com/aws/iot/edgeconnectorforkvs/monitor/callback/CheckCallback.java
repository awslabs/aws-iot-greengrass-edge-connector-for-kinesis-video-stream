package com.aws.iot.edgeconnectorforkvs.monitor.callback;

import com.aws.iot.edgeconnectorforkvs.monitor.Monitor;

/**
 * Monitor check item.
 */
public interface CheckCallback {
    /**
     * Check callback.
     *
     * @param monitor monitor instance
     * @param subject item subject
     * @param userData user data
     */
    void check(Monitor monitor, String subject, Object userData);
}
