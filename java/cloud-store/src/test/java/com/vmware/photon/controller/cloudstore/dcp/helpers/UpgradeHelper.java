/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.cloudstore.dcp.helpers;

import com.vmware.photon.controller.cloudstore.dcp.CloudStoreXenonHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class implements helper routines for field changes from previous version.
 */
public class UpgradeHelper {
  private static final String FILE_NAME = "fieldsbenchmark.yml";

  public static void generateBenchmarkFile() throws Throwable {
    Map<String, HashMap<String, String>> currentServices = populateCurrentState();

    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new FileOutputStream(FILE_NAME), "utf-8"))) {

      for (Map.Entry<String, HashMap<String, String>> service : currentServices.entrySet()) {
        writer.write(service.getKey() + ":");
        writer.newLine();

        HashMap<String, String> fieldMap = service.getValue();
        for (Map.Entry<String, String> field : fieldMap.entrySet()) {
          writer.write("  ");
          writer.write(field.getKey() + ": ");
          writer.write(field.getValue());
          writer.newLine();
        }
      }
      writer.flush();
      writer.close();
    }
  }

  public static Map<String, HashMap<String, String>> populateCurrentState() throws Throwable {
    Map<String, HashMap<String, String>> currentServices = new HashMap<>();

    List<String> factoriesToUpgrade = MigrationUtils
        .findAllUpgradeServices().stream()
        .map(entry -> entry.destinationFactoryServicePath)
        .collect(Collectors.toList());

    for (Class<?> service : CloudStoreXenonHost.FACTORY_SERVICES) {
      if (!factoriesToUpgrade.contains(ServiceHostUtils.getServiceSelfLink("SELF_LINK", service))) {
        // We are not interested in this entity
        continue;
      }

      FactoryService factoryInstance = (FactoryService) service.newInstance();

      Service instance = factoryInstance.createServiceInstance();
      Class<?>[] innerClasses = instance.getClass().getClasses();
      Class<?> serviceClass = null;
      for (Class<?> c : innerClasses) {
        if (ServiceDocument.class.isAssignableFrom(c)) {
          serviceClass = c;
          break;
        }
      }

      HashMap<String, String> fieldNames = new HashMap<>();
      for (Field field : serviceClass.getDeclaredFields()) {
        // Get only public non static fields
        if (!Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
          fieldNames.put(field.getName(), field.getType().getCanonicalName());
        }
      }
      currentServices.put(instance.getClass().getSimpleName(), fieldNames);
    }

    return currentServices;
  }

  public static Map<String, HashMap<String, String>> parseBenchmarkState() throws Throwable {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    String json = UpgradeHelper.class.getResource("/" + FILE_NAME).getPath();
    JsonNode rootNode = mapper.readValue(new File(json), JsonNode.class);

    Map<String, HashMap<String, String>> previousServices = new HashMap<>();

    Iterator<Map.Entry<String, JsonNode>> elements = rootNode.fields();
    while (elements.hasNext()) {
      Map.Entry<String, JsonNode> entry = elements.next();
      String nodeName = entry.getKey();

      HashMap<String, String> fieldNames = new HashMap<>();
      JsonNode it = entry.getValue();
      Iterator<String> fields = it.fieldNames();

      if (fields != null) {
        while (fields.hasNext()) {
          String fieldName = fields.next();
          JsonNode fieldValue = it.get(fieldName);
          fieldNames.put(fieldName, fieldValue.asText());
        }
      }

      previousServices.put(nodeName, fieldNames);
    }

    return previousServices;
  }

}
