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

package com.vmware.photon.controller.apife.utils;

import com.vmware.photon.controller.api.StatsInfo;
import com.vmware.photon.controller.api.constraints.StatsDisabled;
import com.vmware.photon.controller.api.constraints.StatsEnabled;
import com.vmware.photon.controller.apife.exceptions.external.InvalidStatsConfigException;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validate the statsInfo.
 */
public class StatsInfoValidator {
  /**
   * Validate if StatsInfo has the correct configuration information.
   */
  public static void validate(StatsInfo statsInfo) throws InvalidStatsConfigException {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    if (statsInfo.getEnabled()) {
      Set<ConstraintViolation<StatsInfo>> violations = validator.validate(statsInfo, StatsEnabled.class);
      if (!violations.isEmpty()) {
        throw new InvalidStatsConfigException(getErrorMessage(violations));
      }
    } else {
      Set<ConstraintViolation<StatsInfo>> violations = validator.validate(statsInfo, StatsDisabled.class);
      if (!violations.isEmpty()) {
        throw new InvalidStatsConfigException(getErrorMessage(violations));
      }
    }
  }

  private static String getErrorMessage(Set<ConstraintViolation<StatsInfo>> violations) {
    List<String> errors = new ArrayList<>();
    for (ConstraintViolation<StatsInfo> violation : violations) {
      String error = String.format("%s %s (was %s)", violation.getPropertyPath(), violation.getMessage(),
          violation.getInvalidValue());
      errors.add(error);
    }
    return errors.toString();
  }
}
