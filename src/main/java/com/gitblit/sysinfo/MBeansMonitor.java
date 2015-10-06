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

import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This code was extracted from JavaMelody and refactored.
 *
 * @author Emeric Vernat
 * @author James Moger
 */

public final class MBeansMonitor {

    private static final String JAVA_LANG_MBEAN_DESCRIPTION = "Information on the management interface of the MBean";
    private static final Comparator<MBeanNode> NODE_COMPARATOR = (o1, o2) -> o1.getName() != null ? o1.getName().compareTo(o2.getName()) : 0;
    private static final Comparator<MBeanNode.MBeanAttribute> ATTRIBUTE_COMPARATOR = (o1, o2) -> o1.getName().compareTo(o2.getName());
    private final MBeanServer mbeanServer;

    public MBeansMonitor() {
        this(ManagementFactory.getPlatformMBeanServer());
    }

    private MBeansMonitor(MBeanServer mbeanServer) {
        super();
        this.mbeanServer = mbeanServer;
    }

    Object getAttribute(ObjectName name, String attribute) throws JMException {
        return mbeanServer.getAttribute(name, attribute);
    }

    public List<MBeanNode> getAllMBeanNodes() throws JMException {
        initJRockitMBeansIfNeeded();

        List<MBeanNode> result = new ArrayList<>();
        MBeanServer platformMBeanServer = mbeanServer;
        MBeanNode platformNode = new MBeanNode("");
        MBeansMonitor platformMBeansMonitor = new MBeansMonitor();
        platformNode.getChildren().addAll(platformMBeansMonitor.getMBeanNodes());
        result.add(platformNode);

        for (MBeanServer mbeanServer : getMBeanServers()) {
            if (!mbeanServer.equals(platformMBeanServer)) {
                MBeanNode node = new MBeanNode(mbeanServer.getDefaultDomain());
                MBeansMonitor mbeans = new MBeansMonitor(mbeanServer);
                node.getChildren().addAll(mbeans.getMBeanNodes());
                result.add(node);
            }
        }
        return result;
    }

    private void initJRockitMBeansIfNeeded() {
        if (System.getProperty("java.vendor").contains("BEA")) {
            try {
                // http://blogs.oracle.com/hirt/jrockit/
                try {
                    mbeanServer.getMBeanInfo(new ObjectName("bea.jrockit.management:type=JRockitConsole"));
                } catch (InstanceNotFoundException e1) {
                    mbeanServer.createMBean("bea.jrockit.management.JRockitConsole", null);
                }
            } catch (JMException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private List<MBeanNode> getMBeanNodes() throws JMException {
        List<MBeanNode> result = new ArrayList<>();
        Set<ObjectName> names = mbeanServer.queryNames(null, null);
        for (ObjectName name : names) {
            String domain = name.getDomain();
            if ("jboss.deployment".equals(domain)) {
                continue;
            }
            MBeanNode domainNode = getMBeanNodeFromList(result, domain);
            if (domainNode == null) {
                domainNode = new MBeanNode(domain);
                result.add(domainNode);
            }
            String keyPropertyListString = name.getKeyPropertyListString();
            String firstPropertyValue;
            int indexOf = keyPropertyListString.indexOf('=');
            if (indexOf == -1) {
                firstPropertyValue = null;
            } else {
                firstPropertyValue = name.getKeyProperty(keyPropertyListString
                        .substring(0, indexOf));
            }
            if ("Servlet".equals(firstPropertyValue) && "jonas".equals(domain)) {
                continue;
            }
            MBeanNode firstPropertyNode = getMBeanNodeFromList(domainNode.getChildren(),
                    firstPropertyValue);
            if (firstPropertyNode == null) {
                firstPropertyNode = new MBeanNode(firstPropertyValue);
                domainNode.getChildren().add(firstPropertyNode);
            }
            final MBeanNode mbean = getMBeanNode(name);
            firstPropertyNode.getChildren().add(mbean);
        }
        sortMBeanNodes(result);
        return result;
    }

    private void sortMBeanNodes(List<MBeanNode> nodes) {
        if (nodes.size() > 1) {
            Collections.sort(nodes, NODE_COMPARATOR);
        }

        for (MBeanNode node : nodes) {
            List<MBeanNode> children = node.getChildren();
            if (children != null) {
                sortMBeanNodes(children);
            }
            List<MBeanNode.MBeanAttribute> attributes = node.getAttributes();
            if (attributes != null && attributes.size() > 1) {
                Collections.sort(attributes, ATTRIBUTE_COMPARATOR);
            }
        }
    }

    private MBeanNode getMBeanNodeFromList(List<MBeanNode> list, String name) {
        for (MBeanNode node : list) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    private MBeanNode getMBeanNode(ObjectName name) throws JMException {
        String mbeanName = name.toString();
        MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(name);
        String description = formatDescription(mbeanInfo.getDescription());
        MBeanAttributeInfo[] attributeInfos = mbeanInfo.getAttributes();
        List<MBeanNode.MBeanAttribute> attributes = getAttributes(name, attributeInfos);
        return new MBeanNode(mbeanName, description, attributes);
    }

    private List<MBeanNode.MBeanAttribute> getAttributes(ObjectName name, MBeanAttributeInfo[] attributeInfos) {
        List<String> attributeNames = new ArrayList<>(attributeInfos.length);
        for (MBeanAttributeInfo attribute : attributeInfos) {
            if (attribute.isReadable() && !"password".equalsIgnoreCase(attribute.getName())) {
                attributeNames.add(attribute.getName());
            }
        }
        String[] attributeNamesArray = attributeNames.toArray(new String[attributeNames.size()]);
        List<MBeanNode.MBeanAttribute> result = new ArrayList<>();
        try {
            List<Object> attributes = mbeanServer.getAttributes(name, attributeNamesArray);
            for (Object object : attributes) {
                Attribute attribute = (Attribute) object;
                Object value = convertValueIfNeeded(attribute.getValue());
                String attributeDescription = getAttributeDescription(attribute.getName(), attributeInfos);
                String formattedAttributeValue = formatAttributeValue(value);
                MBeanNode.MBeanAttribute mbeanAttribute = new MBeanNode.MBeanAttribute(attribute.getName(),
                        attributeDescription, formattedAttributeValue);
                result.add(mbeanAttribute);
            }
        } catch (Exception e) {
            MBeanNode.MBeanAttribute mbeanAttribute = new MBeanNode.MBeanAttribute("exception", null, e.toString());
            result.add(mbeanAttribute);
        }
        return result;
    }

    private String formatAttributeValue(Object attributeValue) {
        try {
            if (attributeValue instanceof List) {
                StringBuilder sb = new StringBuilder();
                sb.append('[');
                boolean first = true;
                for (Object value : (List<?>) attributeValue) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(",\n");
                    }
                    sb.append(String.valueOf(value));
                }
                sb.append(']');
                return sb.toString();
            }
            return String.valueOf(attributeValue);
        } catch (Exception e) {
            return e.toString();
        }
    }

    private String formatDescription(String description) {
        if (description == null || JAVA_LANG_MBEAN_DESCRIPTION.equals(description)) {
            return null;
        }
        int indexOf = description.indexOf("  ");
        if (indexOf != -1) {
            StringBuilder sb = new StringBuilder(description);
            while (indexOf != -1) {
                sb.deleteCharAt(indexOf);
                indexOf = sb.indexOf("  ");
            }
            return sb.toString();
        }
        return description;
    }

    private Object convertValueIfNeeded(Object value) {
        if (value instanceof CompositeData) {
            CompositeData data = (CompositeData) value;
            Map<String, Object> values = new TreeMap<>();
            for (String key : data.getCompositeType().keySet()) {
                values.put(key, convertValueIfNeeded(data.get(key)));
            }
            return values;
        } else if (value instanceof CompositeData[]) {
            List<Object> list = new ArrayList<>();
            for (CompositeData data : (CompositeData[]) value) {
                list.add(convertValueIfNeeded(data));
            }
            return list;
        } else if (value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        } else if (value instanceof TabularData) {
            TabularData tabularData = (TabularData) value;
            return convertValueIfNeeded(tabularData.values());
        } else if (value instanceof Collection) {
            List<Object> list = new ArrayList<>();
            for (Object data : (Collection<?>) value) {
                list.add(convertValueIfNeeded(data));
            }
            return list;
        }
        return convertJRockitValueIfNeeded(value);
    }

    private Object convertJRockitValueIfNeeded(Object value) {
        if (value instanceof double[]) {
            List<Double> list = new ArrayList<>();
            for (double data : (double[]) value) {
                list.add(data);
            }
            return list;
        } else if (value instanceof int[]) {
            List<Integer> list = new ArrayList<>();
            for (int data : (int[]) value) {
                list.add(data);
            }
            return list;
        }
        return value;
    }

    private List<Object> getConvertedAttributes(List<String> mbeanAttributes) {
        initJRockitMBeansIfNeeded();

        List<Object> result = new ArrayList<>();
        List<MBeanServer> mBeanServers = getMBeanServers();
        for (String mbeansAttribute : mbeanAttributes) {
            int lastIndexOfPoint = mbeansAttribute.lastIndexOf('.');
            if (lastIndexOfPoint <= 0) {
                throw new IllegalArgumentException(mbeansAttribute);
            }
            String name = mbeansAttribute.substring(0, lastIndexOfPoint);
            String attribute = mbeansAttribute.substring(lastIndexOfPoint + 1);
            if ("password".equalsIgnoreCase(attribute)) {
                throw new IllegalArgumentException(name + '.' + attribute);
            }

            InstanceNotFoundException instanceNotFoundException = null;
            for (MBeanServer mbeanServer : mBeanServers) {
                try {
                    MBeansMonitor mbeans = new MBeansMonitor(mbeanServer);
                    Object jmxValue = mbeans.convertValueIfNeeded(mbeans.getAttribute(new ObjectName(name), attribute));
                    result.add(jmxValue);
                    instanceNotFoundException = null;
                    break;
                } catch (InstanceNotFoundException e) {
                    instanceNotFoundException = e;
                } catch (JMException e) {
                    throw new IllegalArgumentException(name + '.' + attribute, e);
                }
            }

            if (instanceNotFoundException != null) {
                throw new IllegalArgumentException(name + '.' + attribute, instanceNotFoundException);
            }
        }
        return result;
    }

    String getConvertedAttributes(String jmxValueParameter) {
        List<String> mbeanAttributes = Arrays.asList(jmxValueParameter.split("[|]"));
        List<Object> jmxValues = getConvertedAttributes(mbeanAttributes);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object jmxValue : jmxValues) {
            if (first) {
                first = false;
            } else {
                sb.append('|');
            }
            sb.append(jmxValue);
        }
        return sb.toString();
    }

    private String getAttributeDescription(String name, MBeanAttributeInfo[] attributeInfos) {
        for (MBeanAttributeInfo attributeInfo : attributeInfos) {
            if (name.equals(attributeInfo.getName())) {
                String attributeDescription = formatDescription(attributeInfo.getDescription());
                if (attributeDescription == null || attributeDescription.isEmpty() || name.equals(attributeDescription)) {
                    return null;
                }
                return attributeDescription;
            }
        }
        return null;
    }

    private List<MBeanServer> getMBeanServers() {
        return MBeanServerFactory.findMBeanServer(null);
    }
}
