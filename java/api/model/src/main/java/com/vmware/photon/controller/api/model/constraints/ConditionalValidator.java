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

package com.vmware.photon.controller.api.model.constraints;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates class based on the value of the boolean condition variable.
 */
public class ConditionalValidator {
  public static  <P, E extends Throwable> void validate(
      P payload, boolean condition, Class trutyGroup, Class falsyGroup, Class<E> eClass) throws E {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Set<ConstraintViolation<P>> violations;
    if (condition) {
      violations = validator.validate(payload, trutyGroup);
    } else {
      violations = validator.validate(payload, falsyGroup);
    }

    if (!violations.isEmpty()) {
      throw buildException(eClass, getErrorMessage(violations));
    }
  }

  private static <P> String getErrorMessage(Set<ConstraintViolation<P>> violations) {
    List<String> errors = new ArrayList<>();
    for (ConstraintViolation<P> violation : violations) {
      String error = String.format("%s %s (was %s)", violation.getPropertyPath(), violation.getMessage(),
          violation.getInvalidValue());
      errors.add(error);
    }
    return errors.toString();
  }

  private static <E extends Throwable> E buildException(Class<E> eClass, String message) {
    try {
      return eClass.getDeclaredConstructor(String.class).newInstance(message);
    } catch (NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException e) {
      return null;
    }
  }
}
