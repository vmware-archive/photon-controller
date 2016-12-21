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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * This class initializes fields with the {@link DefaultInteger} annotation.
 */
public enum DefaultLongInitializer {
  INSTANCE;

  public static void initialize(ServiceDocument state) {
    try {
      Field[] declaredFields = state.getClass().getDeclaredFields();
      for (Field field : declaredFields) {
        if (field.get(state) == null) {
          Annotation[] declaredAnnotations = field.getDeclaredAnnotations();
          for (Annotation annotation : declaredAnnotations) {
            if (annotation.annotationType() == DefaultLong.class) {
              field.set(state, new Long(((DefaultLong) annotation).value()));
            }
          }
        }
      }
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
