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

package com.vmware.photon.controller.common.xenon.validation;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * This class initializes fields with the {@link RenamedField} annotation.
 */
public enum RenamedFieldHandler {
  INSTANCE;

  public static List<Field> initialize(Object sourceJsonObject, ServiceDocument destinationState) throws
      RuntimeException {
    List<Field> result = new ArrayList<Field>();
    try {
      Field[] declaredFields = destinationState.getClass().getDeclaredFields();
      for (Field field : declaredFields) {
        Annotation[] declaredAnnotations = field.getDeclaredAnnotations();
        if (declaredAnnotations != null) {
          for (Annotation annotation : declaredAnnotations) {
            if (annotation.annotationType() == RenamedField.class) {
              // Find the original field in source
              Object originalFieldValue = getNamedFieldValue(sourceJsonObject, ((RenamedField) annotation)
                      .originalName(),
                  field.getType());
              if (originalFieldValue != null) {
                field.set(destinationState, originalFieldValue);
                result.add(field);
              }
            }
          }
        }
      }
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
    return result;
  }

  private static Object getNamedFieldValue(Object jsonObject, String fieldName, Class<?> fieldType) {
    Object fieldValue = Utils.getJsonMapValue(jsonObject, fieldName, fieldType);
    return fieldValue;
  }

}
