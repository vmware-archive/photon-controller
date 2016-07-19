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

package com.vmware.photon.controller.api.model.helpers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.groups.Default;

import java.util.List;
import java.util.Set;
import static java.lang.String.format;

/**
 * This class implements a thin wrapper around the Hibernate validator.
 * <p>
 * This is mostly a re-implementation of Dropwizard's Validator class, which was deprecated sometime between 0.6.2 and
 * 0.8.2.
 */
public class Validator {
  private final ValidatorFactory factory;

  public Validator() {
    this(Validation.buildDefaultValidatorFactory());
  }

  public Validator(ValidatorFactory factory) {
    this.factory = factory;
  }

  /**
   * Validates the given object, and returns a list of error messages, if any. If the returned
   * list is empty, the object is valid.
   *
   * @param o   a potentially-valid object
   * @param <T> the type of object to validate
   * @return a list of error messages, if any, regarding {@code o}'s validity
   */
  public <T> ImmutableList<String> validate(T o) {
    return validate(o, Default.class);
  }

  /**
   * Validates the given object, and returns a list of error messages, if any. If the returned
   * list is empty, the object is valid.
   *
   * @param o      a potentially-valid object
   * @param groups group or list of groups targeted for validation (default to {@link javax.validation.groups.Default})
   * @param <T>    the type of object to validate
   * @return a list of error messages, if any, regarding {@code o}'s validity
   */
  public <T> ImmutableList<String> validate(T o, Class<?>... groups) {
    final Set<String> errors = Sets.newHashSet();

    if (o == null) {
      errors.add("request entity required");
    } else {
      final Set<ConstraintViolation<T>> violations = factory.getValidator().validate(o, groups);
      for (ConstraintViolation<T> v : violations) {
        errors.add(format("%s %s (was %s)",
            v.getPropertyPath(),
            v.getMessage(),
            v.getInvalidValue()));
      }
    }

    List<String> l = Ordering.natural().sortedCopy(errors);
    return ImmutableList.copyOf(l);
  }
}
