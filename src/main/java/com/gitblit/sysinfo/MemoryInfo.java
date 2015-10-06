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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * This code was extracted from JavaMelody and refactored.
 *
 * @author Emeric Vernat
 * @author James Moger
 */
public class MemoryInfo implements Serializable {
    private static final long serialVersionUID = 3281861236369720876L;
    private static final String NEXT = ",\n";
    private static final String MO = " Mo";
    private final long usedMemory;
    private final long maxMemory;
    private final long usedPermGen;
    private final long maxPermGen;
    private final long usedNonHeapMemory;
    private final int loadedClassesCount;
    private final long garbageCollectionTimeMillis;
    private final long usedPhysicalMemorySize;
    private final long usedSwapSpaceSize;
    private final String memoryDetails;

    public MemoryInfo() {
        super();
        usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        maxMemory = Runtime.getRuntime().maxMemory();
        final MemoryPoolMXBean permGenMemoryPool = getPermGenMemoryPool();
        if (permGenMemoryPool != null) {
            final MemoryUsage usage = permGenMemoryPool.getUsage();
            usedPermGen = usage.getUsed();
            maxPermGen = usage.getMax();
        } else {
            usedPermGen = -1;
            maxPermGen = -1;
        }
        usedNonHeapMemory = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
        loadedClassesCount = ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
        garbageCollectionTimeMillis = buildGarbageCollectionTimeMillis();

        final OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (isSunOsMBean(operatingSystem)) {
            usedPhysicalMemorySize = getLongFromOperatingSystem(operatingSystem, "getTotalPhysicalMemorySize")
                    - getLongFromOperatingSystem(operatingSystem, "getFreePhysicalMemorySize");
            usedSwapSpaceSize = getLongFromOperatingSystem(operatingSystem, "getTotalSwapSpaceSize")
                    - getLongFromOperatingSystem(operatingSystem, "getFreeSwapSpaceSize");
        } else {
            usedPhysicalMemorySize = -1;
            usedSwapSpaceSize = -1;
        }

        memoryDetails = buildMemoryDetails();
    }

    private static MemoryPoolMXBean getPermGenMemoryPool() {
        for (MemoryPoolMXBean memoryPool : ManagementFactory.getMemoryPoolMXBeans()) {
            // name is "Perm Gen" or "PS Perm Gen" (32 vs 64 bits ?)
            if (memoryPool.getName().endsWith("Perm Gen")) {
                return memoryPool;
            }
        }
        return null;
    }

    private static long buildGarbageCollectionTimeMillis() {
        long garbageCollectionTime = 0;
        for (GarbageCollectorMXBean garbageCollector : ManagementFactory.getGarbageCollectorMXBeans()) {
            garbageCollectionTime += garbageCollector.getCollectionTime();
        }
        return garbageCollectionTime;
    }

    private String buildMemoryDetails() {
        NumberFormat integerFormat = DecimalFormat.getIntegerInstance();
        String nonHeapMemory = "Non heap memory = " +
                integerFormat.format(usedNonHeapMemory / 1024 / 1024) + MO + " (Perm Gen, Code Cache)";
        String classLoading = "Loaded classes = " + integerFormat.format(loadedClassesCount);
        String gc = "Garbage collection time = " + integerFormat.format(garbageCollectionTimeMillis) + " ms";
        OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        String osInfo = "";
        if (isSunOsMBean(operatingSystem)) {
            osInfo = "Process cpu time = "
                    + integerFormat.format(getLongFromOperatingSystem(operatingSystem,
                    "getProcessCpuTime") / 1000000)
                    + " ms,\nCommitted virtual memory = "
                    + integerFormat.format(getLongFromOperatingSystem(operatingSystem,
                    "getCommittedVirtualMemorySize") / 1024 / 1024)
                    + MO
                    + ",\nFree physical memory = "
                    + integerFormat.format(getLongFromOperatingSystem(operatingSystem,
                    "getFreePhysicalMemorySize") / 1024 / 1024)
                    + MO
                    + ",\nTotal physical memory = "
                    + integerFormat.format(getLongFromOperatingSystem(operatingSystem,
                    "getTotalPhysicalMemorySize") / 1024 / 1024)
                    + MO
                    + ",\nFree swap space = "
                    + integerFormat.format(getLongFromOperatingSystem(operatingSystem,
                    "getFreeSwapSpaceSize") / 1024 / 1024)
                    + MO
                    + ",\nTotal swap space = "
                    + integerFormat.format(getLongFromOperatingSystem(operatingSystem,
                    "getTotalSwapSpaceSize") / 1024 / 1024) + MO;
        }

        return nonHeapMemory + NEXT + classLoading + NEXT + gc + NEXT + osInfo;
    }

    private static boolean isSunOsMBean(OperatingSystemMXBean operatingSystem) {
        String className = operatingSystem.getClass().getName();
        return "com.sun.management.OperatingSystem".equals(className)
                || "com.sun.management.UnixOperatingSystem".equals(className)
                // sun.management.OperatingSystemImpl for java 8
                || "sun.management.OperatingSystemImpl".equals(className);
    }

    static long getLongFromOperatingSystem(OperatingSystemMXBean operatingSystem, String methodName) {
        try {
            Method method = operatingSystem.getClass().getMethod(methodName, (Class<?>[]) null);
            method.setAccessible(true);
            return (Long) method.invoke(operatingSystem, (Object[]) null);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            } else if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new IllegalStateException(e.getCause());
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public long getUsedMemory() {
        return usedMemory;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public double getUsedMemoryPercentage() {
        return 100d * usedMemory / maxMemory;
    }

    public long getUsedPermGen() {
        return usedPermGen;
    }

    public long getMaxPermGen() {
        return maxPermGen;
    }

    public double getUsedPermGenPercentage() {
        if (usedPermGen > 0 && maxPermGen > 0) {
            return 100d * usedPermGen / maxPermGen;
        }
        return -1d;
    }

    public long getUsedNonHeapMemory() {
        return usedNonHeapMemory;
    }

    public int getLoadedClassesCount() {
        return loadedClassesCount;
    }

    public long getGarbageCollectionTimeMillis() {
        return garbageCollectionTimeMillis;
    }

    public long getUsedPhysicalMemorySize() {
        return usedPhysicalMemorySize;
    }

    public long getUsedSwapSpaceSize() {
        return usedSwapSpaceSize;
    }

    public String getMemoryDetails() {
        return memoryDetails;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[usedMemory=" + getUsedMemory() + ", maxMemory="
                + getMaxMemory() + ']';
    }
}
