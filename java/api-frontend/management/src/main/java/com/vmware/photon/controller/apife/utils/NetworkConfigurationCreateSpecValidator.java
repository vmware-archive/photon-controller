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

import com.vmware.photon.controller.api.NetworkConfigurationCreateSpec;
import com.vmware.photon.controller.api.constraints.VirtualNetworkDisabled;
import com.vmware.photon.controller.api.constraints.VirtualNetworkEnabled;
import com.vmware.photon.controller.apife.exceptions.external.InvalidNetworkConfigException;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validate NetworkConfigurationCreateSpec.
 */
public class NetworkConfigurationCreateSpecValidator {
  public static void validate(NetworkConfigurationCreateSpec spec) throws InvalidNetworkConfigException {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    if (spec.getVirtualNetworkEnabled()) {
      Set<ConstraintViolation<NetworkConfigurationCreateSpec>> violations =
          validator.validate(spec, VirtualNetworkEnabled.class);
      if (!violations.isEmpty()) {
        throw new InvalidNetworkConfigException(getErrorMessage(violations));
      }
    } else {
      Set<ConstraintViolation<NetworkConfigurationCreateSpec>> violations =
          validator.validate(spec, VirtualNetworkDisabled.class);
      if (!violations.isEmpty()) {
        throw new InvalidNetworkConfigException(getErrorMessage(violations));
      }
    }
  }

  private static String getErrorMessage(Set<ConstraintViolation<NetworkConfigurationCreateSpec>> violations) {
    List<String> errors = new ArrayList<>();
    for (ConstraintViolation<NetworkConfigurationCreateSpec> violation : violations) {
      String error = String.format("%s %s (was %s)", violation.getPropertyPath(), violation.getMessage(),
          violation.getInvalidValue());
      errors.add(error);
    }
    return errors.toString();
  }
}
