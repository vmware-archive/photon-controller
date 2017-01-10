/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.nsxclient.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Helper class to generate the string representation of an object.
 */
public class ToStringHelper {
  /**
   * This class uses the JsonProperty annotation to determine which fields
   * should be included in the converted string.
   *
   * @param obj
   * @return
   */
  public static String jsonObjectToString(Object obj) {
    try {
      Objects.ToStringHelper helper = Objects.toStringHelper(obj);
      Field[] declaredFields = obj.getClass().getDeclaredFields();
      for (Field field : declaredFields) {
        field.setAccessible(true);
        Annotation[] declaredAnnotations = field.getDeclaredAnnotations();
        for (Annotation annotation : declaredAnnotations) {
          if (annotation instanceof JsonProperty) {
            JsonProperty jsonProperty = (JsonProperty) annotation;
            if (jsonProperty.required()) {
              helper.add(field.getName(), field.get(obj));
            } else {
              if (field.get(obj) != null) {
                helper.add(field.getName(), field.get(obj));
              }
            }
          }
        }
      }

      return helper.toString();
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

  }
}
