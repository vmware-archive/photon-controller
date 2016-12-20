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
import org.apache.commons.validator.routines.InetAddressValidator;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates if the given string is a valid CIDR.
 */
public class CidrValidator implements ConstraintValidator<Cidr, String> {

  public static final int CIDR_BIT_MASK_MAX_VALUE = 31;

  @Inject
  public CidrValidator() {
  }

  @Override
  public void initialize(Cidr constraintAnnotation) {
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return false;
    }

    int bitMask = 0;
    boolean validIpAddress = false;
    String[] cidr = value.split("/");

    if (cidr.length == 2 && cidr[1].matches("\\d+")) {
      validIpAddress = InetAddressValidator.getInstance().isValid(cidr[0]);
      bitMask = Integer.parseInt(cidr[1]);
    }

    if (!validIpAddress || !(bitMask > 0 && bitMask <= CIDR_BIT_MASK_MAX_VALUE)) {
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate(String.format("%s is invalid CIDR", value))
          .addConstraintViolation();
      return false;
    }

    return true;
  }
}
