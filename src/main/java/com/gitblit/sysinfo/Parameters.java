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

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This code was extracted from JavaMelody and refactored.
 *
 * @author Emeric Vernat
 * @author James Moger
 */
class Parameters {
    static final File TEMPORARY_DIRECTORY = new File(System.getProperty("java.io.tmpdir"));

    static String getHostName() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isEmpty()) {
                return host;
            }
        } catch (UnknownHostException e) {
        }

        host = System.getenv("COMPUTERNAME");
        if (host != null) {
            return host;
        } else {
            host = System.getenv("HOSTNAME");
            return host != null ? host : "localhost";
        }
    }

    static String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            return "unknown";
        }
    }
}
