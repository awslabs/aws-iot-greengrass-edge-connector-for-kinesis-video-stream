package com.aws.iot.edgeconnectorforkvs.monitor;

import java.util.concurrent.TimeUnit;
import com.aws.iot.edgeconnectorforkvs.monitor.callback.CheckCallback;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MonitorTest {
    @BeforeEach
    void setupTest() {}

    @Test
    void compareTime_invokeComparator_noAssertions() {
        Monitor.CheckItemComparator comp = new Monitor.CheckItemComparator();
        CheckCallback cb = (monitor, subject, userData) -> {
        };
        Monitor.CheckItem c1 = Monitor.CheckItem.builder().subject("c1").checkCallback(cb)
                .timeStamp(-1).userData(null).build();
        Monitor.CheckItem c2 = Monitor.CheckItem.builder().subject("c2").checkCallback(cb)
                .timeStamp(0).userData(null).build();
        Monitor.CheckItem c3 = Monitor.CheckItem.builder().subject("c3").checkCallback(cb)
                .timeStamp(1).userData(null).build();

        Assertions.assertEquals(-1, comp.compare(c1, c2));
        Assertions.assertEquals(0, comp.compare(c2, c2));
        Assertions.assertEquals(1, comp.compare(c3, c2));
        Assertions.assertNotNull(Monitor.CheckItem.builder().toString());
    }

    @Test
    void runTask_startMonitorThreaed_exceptionNotThrow() {
        CheckCallback task1 = (monitor, subject, userData) -> {
        };
        CheckCallback task2 = (monitor, subject, userData) -> {
            Monitor.getMonitor().add("testMonitorTask1", task1, 10, null);
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            } catch (InterruptedException e) {
                Assertions.fail();
            }
        };
        CheckCallback task3 = (monitor, subject, userData) -> {
        };

        // Test for add/remove tasks
        Monitor.getMonitor().cleanTasks();
        Monitor.getMonitor().add("testMonitorTask1", task1, 100, null);
        Monitor.getMonitor().add("testMonitorTask1", task1, 100, null);
        Monitor.getMonitor().remove("testMonitorTask1");
        Monitor.getMonitor().remove("testMonitorTask1");

        Monitor.getMonitor().start();
        Monitor.getMonitor().start();
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e1) {
            Assertions.fail();
        }

        // Test for first task and insert to queue in order
        Monitor.getMonitor().add("testMonitorTask2", task2, 100, null);
        Monitor.getMonitor().add("testMonitorTask1", task1, 10, null);
        Monitor.getMonitor().add("testMonitorTask3", task3, 500, null);

        // Test for add a task and wake up thread for executing the new one
        try {
            TimeUnit.MILLISECONDS.sleep(300);
        } catch (InterruptedException e1) {
            Assertions.fail();
        }
        Monitor.getMonitor().add("testMonitorTask4", task3, 1, null);

        try {
            TimeUnit.MILLISECONDS.sleep(500);
            Monitor.getMonitor().stop();
            Monitor.getMonitor().stop();
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }
}
