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

import static com.google.common.base.Preconditions.checkState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Validates that the marked field is set once and only once in a patch state.
 */
public enum WriteOnceValidator {
  INSTANCE;

  public static void validate(ServiceDocument startState, ServiceDocument patchState) {
    try {
      Field[] declaredFields = startState.getClass().getDeclaredFields();
      for (Field field : declaredFields) {
        Annotation[] declaredAnnotations = field.getDeclaredAnnotations();
        for (Annotation annotation : declaredAnnotations) {
          if (annotation.annotationType() == WriteOnce.class) {
            Object startValue = field.get(startState);
            Object patchValue = field.get(patchState);
            if (null != patchValue) {
              checkState(null == startValue,
                  String.format("%s cannot be set or changed in a patch", field.getName()));
            }
          }
        }
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
