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
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * This code was extracted from JavaMelody and refactored.
 *
 * @author Emeric Vernat
 * @author James Moger
 */
public class SysInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Date START_DATE = new Date();
    private final MemoryInfo memoryInfo;
    private final long processCpuTimeMillis;
    private final double systemLoadAverage;
    private final long unixOpenFileDescriptorCount;
    private final long unixMaxFileDescriptorCount;
    private final String host;
    private final String os;
    private final int availableProcessors;
    private final String javaVersion;
    private final String jvmVersion;
    private final String pid;
    private final Date startDate;
    private final String jvmArguments;
    private final long freeDiskSpaceInTemp;
    private final int threadCount;
    private final int peakThreadCount;
    private final long totalStartedThreadCount;
    private final List<ThreadInfo> threadInfoList;
    private final List<ProcessInfo> processInfoList;

    public SysInfo() {
        this(true, true);
    }

    public SysInfo(boolean collectThreadInfo, boolean collectProcessInfo) {
        startDate = START_DATE;
        host = Parameters.getHostName() + '@' + Parameters.getHostAddress();
        os = buildOS();
        availableProcessors = Runtime.getRuntime().availableProcessors();
        javaVersion = System.getProperty("java.runtime.name") + ", " + System.getProperty("java.runtime.version");
        jvmVersion = System.getProperty("java.vm.name") + ", " + System.getProperty("java.vm.version") + ", " + System.getProperty("java.vm.info");
        jvmArguments = buildJvmArguments();

        pid = PID.getPID();

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        threadCount = threadBean.getThreadCount();
        peakThreadCount = threadBean.getPeakThreadCount();
        totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

        threadInfoList = collectThreadInfo ? ThreadInfo.buildThreadInfoList() : Collections.emptyList();
        processInfoList = collectProcessInfo ? ProcessInfo.buildProcessInfoList() : Collections.emptyList();

        memoryInfo = new MemoryInfo();
        systemLoadAverage = buildSystemLoadAverage();
        processCpuTimeMillis = buildProcessCpuTimeMillis();

        unixOpenFileDescriptorCount = buildOpenFileDescriptorCount();
        unixMaxFileDescriptorCount = buildMaxFileDescriptorCount();
        freeDiskSpaceInTemp = Parameters.TEMPORARY_DIRECTORY.getFreeSpace();
    }

    private static String buildOS() {
        String name = System.getProperty("os.name");
        String version = System.getProperty("os.version");
        String patchLevel = System.getProperty("sun.os.patch.level");
        String arch = System.getProperty("os.arch");
        String bits = System.getProperty("sun.arch.data.model");

        StringBuilder sb = new StringBuilder();
        sb.append(name).append(", ");
        if (!name.toLowerCase(Locale.ENGLISH).contains("windows")) {
            // version is "6.1" and useless for os.name "Windows 7",
            // and can be "2.6.32-358.23.2.el6.x86_64" for os.name "Linux"
            sb.append(version).append(' ');
        }
        if (!"unknown".equals(patchLevel)) {
            // patchLevel is "unknown" and useless on Linux,
            // and can be "Service Pack 1" on Windows
            sb.append(patchLevel);
        }
        sb.append(", ").append(arch).append('/').append(bits);
        return sb.toString();
    }

    private long buildProcessCpuTimeMillis() {
        OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (isSunOsMBean(operatingSystem)) {
            // nanoseconds to milliseconds
            return MemoryInfo.getLongFromOperatingSystem(operatingSystem, "getProcessCpuTime") / 1000000;
        }
        return -1;
    }

    private long buildOpenFileDescriptorCount() {
        OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (isSunOsMBean(operatingSystem) && isSunUnixMBean(operatingSystem)) {
            try {
                return MemoryInfo.getLongFromOperatingSystem(operatingSystem, "getOpenFileDescriptorCount");
            } catch (Error e) {
                // pour issue 16 (using jsvc on ubuntu or debian)
                return -1;
            }
        }
        return -1;
    }

    private long buildMaxFileDescriptorCount() {
        OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (isSunOsMBean(operatingSystem) && isSunUnixMBean(operatingSystem)) {
            try {
                return MemoryInfo.getLongFromOperatingSystem(operatingSystem, "getMaxFileDescriptorCount");
            } catch (Error e) {
                // pour issue 16 (using jsvc on ubuntu or debian)
                return -1;
            }
        }
        return -1;
    }

    private double buildSystemLoadAverage() {
        // System load average for the last minute.
        // The system load average is the sum of
        // the number of runnable entities queued to the available processors
        // and the number of runnable entities running on the available processors
        // averaged over a period of time.
        OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (operatingSystem.getSystemLoadAverage() >= 0) {
            return operatingSystem.getSystemLoadAverage();
        }
        return -1;
    }

    private static String buildJvmArguments() {
        StringBuilder jvmArgs = new StringBuilder();
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            jvmArgs.append(jvmArg).append('\n');
        }
        if (jvmArgs.length() > 0) {
            jvmArgs.deleteCharAt(jvmArgs.length() - 1);
        }
        return jvmArgs.toString();
    }

    boolean isSunOsMBean(OperatingSystemMXBean operatingSystem) {
        // on ne teste pas operatingSystem instanceof com.sun.management.OperatingSystemMXBean
        // car le package com.sun n'existe à priori pas sur une jvm tierce
        String className = operatingSystem.getClass().getName();
        return "com.sun.management.OperatingSystem".equals(className)
                || "com.sun.management.UnixOperatingSystem".equals(className)
                // sun.management.OperatingSystemImpl pour java 8
                || "sun.management.OperatingSystemImpl".equals(className);
    }

    boolean isSunUnixMBean(OperatingSystemMXBean operatingSystem) {
        for (Class<?> inter : operatingSystem.getClass().getInterfaces()) {
            if ("com.sun.management.UnixOperatingSystemMXBean".equals(inter.getName())) {
                return true;
            }
        }
        return false;
    }

    public MemoryInfo getMemoryInfo() {
        return memoryInfo;
    }

    public long getProcessCpuTimeMillis() {
        return processCpuTimeMillis;
    }

    public double getSystemLoadAverage() {
        return systemLoadAverage;
    }

    public long getUnixOpenFileDescriptorCount() {
        return unixOpenFileDescriptorCount;
    }

    public long getUnixMaxFileDescriptorCount() {
        return unixMaxFileDescriptorCount;
    }

    public double getUnixOpenFileDescriptorPercentage() {
        if (unixOpenFileDescriptorCount >= 0) {
            return 100d * unixOpenFileDescriptorCount / unixMaxFileDescriptorCount;
        }
        return -1d;
    }

    public String getHost() {
        return host;
    }

    public String getOs() {
        return os;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getJvmVersion() {
        return jvmVersion;
    }

    public String getPid() {
        return pid;
    }

    public Date getStartDate() {
        return startDate;
    }

    public String getJvmArguments() {
        return jvmArguments;
    }

    public long getFreeDiskSpaceInTemp() {
        return freeDiskSpaceInTemp;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getPeakThreadCount() {
        return peakThreadCount;
    }

    public long getTotalStartedThreadCount() {
        return totalStartedThreadCount;
    }

    public List<ThreadInfo> getThreadInfoList() {
        // on trie sur demande (si affichage)
        List<ThreadInfo> result = new ArrayList<>(threadInfoList);
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public List<ProcessInfo> getProcessInfoList() {
        // on trie sur demande (si affichage)
        List<ProcessInfo> result = new ArrayList<>(processInfoList);
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public boolean isStackTraceEnabled() {
        for (ThreadInfo threadInformations : threadInfoList) {
            List<StackTraceElement> stackTrace = threadInformations.getStackTrace();
            if (stackTrace != null && !stackTrace.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[pid=" + getPid() + ", host=" + getHost()
                + ", javaVersion=" + getJavaVersion() + ']';
    }
}
