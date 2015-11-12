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

package com.vmware.photon.controller.common.dcp.validation;

import com.vmware.dcp.common.ServiceDocument;

import static com.google.common.base.Preconditions.checkState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * This class implements a validator that checks if the {@link Range}
 * annotation on a state object is honored.
 */
public enum RangeValidator {
  INSTANCE;

  public static void validate(ServiceDocument state) {
    try {
      Field[] declaredFields = state.getClass().getDeclaredFields();
      for (Field field : declaredFields) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
          if (annotation.annotationType() == Range.class) {
            long min = ((Range) annotation).min();
            long max = ((Range) annotation).max();
            Number value = (Number) field.get(state);
            checkState(value == null || (value.longValue() >= min && value.longValue() <= max),
                String.format("%s is not within range %d, %d", field.getName(), min, max));
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
