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

package com.vmware.photon.controller.api.model.constraints;

import com.google.inject.Inject;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates if the given string is null or a valid IP address or a domain.
 */
public class NullableDomainOrIPValidator implements ConstraintValidator<NullableDomainOrIP, String> {

  private final DomainOrIPValidator validator = new DomainOrIPValidator();

  @Inject
  public NullableDomainOrIPValidator() {
  }

  @Override
  public void initialize(NullableDomainOrIP constraintAnnotation) {
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    return validator.isValid(value, context);
  }
}
