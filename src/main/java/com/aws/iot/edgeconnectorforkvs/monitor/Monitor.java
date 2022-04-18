/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.aws.iot.edgeconnectorforkvs.monitor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.aws.iot.edgeconnectorforkvs.monitor.callback.CheckCallback;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Video Recorder Builder class.
 */
@Slf4j
public final class Monitor {
    @Builder
    static class CheckItem {
        private String subject;
        private long timeStamp;
        private CheckCallback checkCallback;
        private Object userData;
    }

    static class CheckItemComparator implements Comparator<CheckItem>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(CheckItem c1, CheckItem c2) {
            long diff = c1.timeStamp - c2.timeStamp;

            if (diff < 0) {
                return -1;
            } else if (diff == 0) {
                return 0;
            } else {
                return 1;
            }
        }
    }

    private static Monitor instance = new Monitor();
    private HashMap<String, CheckItem> checkItems;
    private TreeSet<CheckItem> taskQueue;
    private Thread loop;
    private AtomicBoolean stopFlag;
    private AtomicLong currTimeStamp;
    private Lock loopHibernateLck;
    private Condition loopHibernateCond;
    private AtomicBoolean hibernateFlag;

    /**
     * Get the global unique instance of the monitor.
     *
     * @return monitor instance
     */
    @SuppressFBWarnings(value = "MS_EXPOSE_REP")
    public static Monitor getMonitor() {
        return instance;
    }

    /**
     * Controller uses the start method to start the monitor loop on demand.
     */
    public void start() {
        if (this.stopFlag.compareAndSet(true, false)) {
            this.loop = new Thread(() -> {
                try {
                    this.run();
                } catch (InterruptedException e) {
                    log.info("Monitor is interrupted by someone: {}", e.getMessage());
                }
            });
            this.loop.start();
        }
    }

    /**
     * Controller uses the stop method to stop the monitor when controller doesn't need it.
     *
     * @throws InterruptedException
     */
    public void stop() throws InterruptedException {
        if (this.stopFlag.compareAndSet(false, true)) {
            this.loopHibernateLck.lock();
            try {
                this.hibernateFlag.set(false);
                this.loopHibernateCond.signal();
            } finally {
                this.loopHibernateLck.unlock();
            }
            this.loop.join();
            this.loop = null;
        }
    }

    /**
     * Controller uses cleanTasks method to clear all tasks in the queue.
     */
    public synchronized void cleanTasks() {
        this.checkItems.clear();
        this.taskQueue.clear();
    }

    /**
     * Modules add tasks to check their status.
     *
     * @param subject unique task name
     * @param callback modules define their callback for the checking task
     * @param expireMillisec timeout in milliseconds
     * @param userData user data
     * @return true if tasks are added
     */
    public synchronized boolean add(String subject, CheckCallback callback, long expireMillisec,
            Object userData) {
        boolean ret = false;

        if (!checkItems.containsKey(subject)) {
            CheckItem item = CheckItem.builder().subject(subject).checkCallback(callback)
                    .timeStamp(Instant.now().toEpochMilli() + expireMillisec).userData(userData)
                    .build();

            log.info("Monitor has new task: {}.", subject);
            this.taskQueue.add(item);
            this.checkItems.put(subject, item);

            if (this.taskQueue.first().subject.equals(subject)) {
                log.info("Monitor sends signal to start checking.");
                this.loopHibernateLck.lock();
                try {
                    this.hibernateFlag.set(false);
                    this.loopHibernateCond.signal();
                } finally {
                    this.loopHibernateLck.unlock();
                }
            }
            ret = true;
        } else {
            log.warn("Monitor fails to add duplicated task: {}.", subject);
        }

        return ret;
    }

    /**
     * Modules removes tasks from the task queue.
     *
     * @param subject unique task name
     * @return true if tasks are removed from the queue
     */
    public synchronized boolean remove(String subject) {
        boolean ret = false;

        if (checkItems.containsKey(subject)) {
            log.info("Monitor removes task: {}.", subject);
            this.taskQueue.remove(checkItems.get(subject));
            this.checkItems.remove(subject);
            ret = true;
        } else {
            log.warn("Monitor fails to remove task: {}.", subject);
        }

        return ret;
    }

    private Monitor() {
        this.checkItems = new HashMap<>();
        this.taskQueue = new TreeSet<CheckItem>(new CheckItemComparator());
        this.stopFlag = new AtomicBoolean(true);
        this.currTimeStamp = new AtomicLong(0);
        this.loopHibernateLck = new ReentrantLock();
        this.loopHibernateCond = this.loopHibernateLck.newCondition();
        this.hibernateFlag = new AtomicBoolean(false);
    }

    private void run() throws InterruptedException {
        while (!this.stopFlag.get()) {
            this.updateTimeStamp();
            this.runTasks();
            this.waitTimeOut(this.getNextTimeOut());
        }
    }

    private void updateTimeStamp() {
        this.currTimeStamp.set(Instant.now().toEpochMilli());
    }

    private void runTasks() {
        while (true) {
            CheckItem task = this.getFirstRunnableTask();

            if (task == null) {
                break;
            } else {
                log.info("Monitor runs task: {}.", task.subject);
                task.checkCallback.check(this, task.subject, task.userData);
            }
        }
    }

    /**
     * Calculate next timeout in milliseconds.
     *
     * @return Next timeout in milliseconds. 0 if the timer expires immediatly. Greater than 0 if
     *         the timer will expire after the value in milliseconds. -1 if there is no
     *         task and the timer never expires.
     */
    private synchronized long getNextTimeOut() {
        long diff = 0;

        if (!this.taskQueue.isEmpty()) {
            CheckItem task = this.taskQueue.first();

            this.updateTimeStamp();
            diff = Math.max(0, task.timeStamp - this.currTimeStamp.get());
            if (diff > 0) {
                // Ask the monitor to hibernate if the waiting time is greater than 0.
                this.hibernateFlag.set(true);
            }
        } else {
            // Ask the monitor to hibernate if there is no task in the queue.
            diff = -1;
            this.hibernateFlag.set(true);
        }

        return diff;
    }

    private void waitTimeOut(long expireMillisec) throws InterruptedException {
        if (expireMillisec == -1 || expireMillisec > 0) {
            this.loopHibernateLck.lock();
            try {
                while (this.hibernateFlag.get()) {
                    if (expireMillisec == -1) {
                        log.info("Monitor is hibernating.");
                        this.loopHibernateCond.await();
                        log.info("Monitor is awake.");
                    } else {
                        log.debug("Monitor waits for next timeout: {}.", expireMillisec);
                        if (!this.loopHibernateCond.await(expireMillisec, TimeUnit.MILLISECONDS)) {
                            this.hibernateFlag.set(false);
                        }
                    }
                }
            } finally {
                this.loopHibernateLck.unlock();
            }
        }
    }

    private synchronized CheckItem getFirstRunnableTask() {
        CheckItem task = null;

        if (!this.taskQueue.isEmpty()) {
            CheckItem candidateTask = this.taskQueue.first();

            if (this.currTimeStamp.get() >= candidateTask.timeStamp) {
                String subject = candidateTask.subject;

                log.info("Monitor pops up task: {}.", subject);
                this.taskQueue.remove(checkItems.get(subject));
                this.checkItems.remove(subject);
                task = candidateTask;
            }
        }

        return task;
    }
}
