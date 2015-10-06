/*
 * Copyright 2008-2014 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
package com.gitblit.sysinfo;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This code was extracted from JavaMelody and refactored.
 *
 * @author Emeric Vernat
 * @author James Moger
 */
public class ThreadInfo implements Serializable, Comparable<ThreadInfo> {
    private static final long serialVersionUID = 1L;
    private static final ThreadMXBean THREAD_BEAN = ManagementFactory.getThreadMXBean();
    private static final boolean CPU_TIME_ENABLED = THREAD_BEAN.isThreadCpuTimeSupported() && THREAD_BEAN.isThreadCpuTimeEnabled();
    private final String name;
    private final long id;
    private final int priority;
    private final boolean daemon;
    private final Thread.State state;
    private final long cpuTimeMillis;
    private final long userTimeMillis;
    private final boolean deadlocked;
    private final String globalThreadId;
    private final List<StackTraceElement> stackTrace;

    ThreadInfo(Thread thread, List<StackTraceElement> stackTrace, long cpuTimeMillis, long userTimeMillis,
               boolean deadlocked, String hostAddress) {
        super();
        assert thread != null;
        assert stackTrace == null || stackTrace instanceof Serializable;

        this.name = thread.getName();
        this.id = thread.getId();
        this.priority = thread.getPriority();
        this.daemon = thread.isDaemon();
        this.state = thread.getState();
        this.stackTrace = stackTrace;
        this.cpuTimeMillis = cpuTimeMillis;
        this.userTimeMillis = userTimeMillis;
        this.deadlocked = deadlocked;
        this.globalThreadId = buildGlobalThreadId(thread, hostAddress);
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public Thread.State getState() {
        return state;
    }

    public List<StackTraceElement> getStackTrace() {
        if (stackTrace != null) {
            return Collections.unmodifiableList(stackTrace);
        }
        return stackTrace;
    }

    public String getExecutedMethod() {
        List<StackTraceElement> trace = stackTrace;
        if (trace != null && !trace.isEmpty()) {
            return trace.get(0).toString();
        }
        return "";
    }

    public long getCpuTimeMillis() {
        return cpuTimeMillis;
    }

    public long getUserTimeMillis() {
        return userTimeMillis;
    }

    public boolean isDeadlocked() {
        return deadlocked;
    }

    public String getGlobalThreadId() {
        return globalThreadId;
    }

    private static String buildGlobalThreadId(Thread thread, String hostAddress) {
        return PID.getPID() + '_' + hostAddress + '_' + thread.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + getId() + ", name=" + getName() + ", daemon="
                + isDaemon() + ", priority=" + getPriority() + ", deadlocked=" + isDeadlocked()
                + ", state=" + getState() + ']';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(ThreadInfo thread2) {
        return getName().compareToIgnoreCase(thread2.getName());
    }


    public static long getCurrentThreadCpuTime() {
        return getThreadCpuTime(Thread.currentThread().getId());
    }

    public static long getThreadCpuTime(long threadId) {
        if (CPU_TIME_ENABLED) {
            return THREAD_BEAN.getThreadCpuTime(threadId);
        }
        return 0;
    }

    public static List<ThreadInfo> buildThreadInfoList() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        List<Thread> threads = new ArrayList<>(stackTraces.keySet());

        // si "1.6.0_01".compareTo(Parameters.JAVA_VERSION) > 0;
        // on récupèrait les threads sans stack trace en contournant bug 6434648 avant 1.6.0_01
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6434648
        // hormis pour le thread courant qui obtient sa stack trace différemment sans le bug
        //		threads = getThreadsFromThreadGroups();
        //		final Thread currentThread = Thread.currentThread();
        //		stackTraces = Collections.singletonMap(currentThread, currentThread.getStackTrace());

        boolean cpuTimeEnabled = threadBean.isThreadCpuTimeSupported() && threadBean.isThreadCpuTimeEnabled();
        long[] deadlockedThreads = getDeadlockedThreads(threadBean);
        List<ThreadInfo> threadInfosList = new ArrayList<>(threads.size());
        // hostAddress récupéré ici car il peut y avoir plus de 20000 threads
        String hostAddress = Parameters.getHostAddress();
        for (Thread thread : threads) {
            StackTraceElement[] stackTraceElements = stackTraces.get(thread);
            List<StackTraceElement> stackTraceElementList = stackTraceElements == null ? null : Arrays.asList(stackTraceElements);
            long cpuTimeMillis;
            long userTimeMillis;
            if (cpuTimeEnabled) {
                cpuTimeMillis = threadBean.getThreadCpuTime(thread.getId()) / 1000000;
                userTimeMillis = threadBean.getThreadUserTime(thread.getId()) / 1000000;
            } else {
                cpuTimeMillis = -1;
                userTimeMillis = -1;
            }
            boolean deadlocked = deadlockedThreads != null && Arrays.binarySearch(deadlockedThreads, thread.getId()) >= 0;
            // stackTraceElementList est une ArrayList et non unmodifiableList pour lisibilité xml
            threadInfosList.add(
                    new ThreadInfo(thread, stackTraceElementList, cpuTimeMillis, userTimeMillis, deadlocked, hostAddress));
        }
        // on retourne ArrayList et non unmodifiableList pour lisibilité du xml par xstream
        return threadInfosList;
    }

    static long[] getDeadlockedThreads(ThreadMXBean threadBean) {
        long[] deadlockedThreads;
        if (threadBean.isSynchronizerUsageSupported()) {
            deadlockedThreads = threadBean.findDeadlockedThreads();
        } else {
            deadlockedThreads = threadBean.findMonitorDeadlockedThreads();
        }
        if (deadlockedThreads != null) {
            Arrays.sort(deadlockedThreads);
        }
        return deadlockedThreads;
    }
}
