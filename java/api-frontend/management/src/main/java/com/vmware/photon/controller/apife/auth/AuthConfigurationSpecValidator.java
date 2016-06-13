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

package com.vmware.photon.controller.apife.auth;

import com.vmware.photon.controller.api.AuthConfigurationSpec;
import com.vmware.photon.controller.api.constraints.AuthDisabled;
import com.vmware.photon.controller.api.constraints.AuthEnabled;
import com.vmware.photon.controller.apife.exceptions.external.InvalidAuthConfigException;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validate the authInfo.
 */
public class AuthConfigurationSpecValidator {
  /**
   * Validate if AuthInfo has the correct configuration information.
   */
  public static void validate(AuthConfigurationSpec authConfig) throws InvalidAuthConfigException {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    if (authConfig.getEnabled()) {
      Set<ConstraintViolation<AuthConfigurationSpec>> violations = validator.validate(authConfig, AuthEnabled.class);
      if (!violations.isEmpty()) {
        throw new InvalidAuthConfigException(getErrorMessage(violations));
      }
    } else {
      Set<ConstraintViolation<AuthConfigurationSpec>> violations = validator.validate(authConfig, AuthDisabled.class);
      if (!violations.isEmpty()) {
        throw new InvalidAuthConfigException(getErrorMessage(violations));
      }
    }
  }

  private static String getErrorMessage(Set<ConstraintViolation<AuthConfigurationSpec>> violations) {
    List<String> errors = new ArrayList<>();
    // NOTE: Does not print the invalid value as it could contain usernames or passwords.
    for (ConstraintViolation<AuthConfigurationSpec> violation : violations) {
      String error = String.format("%s %s", violation.getPropertyPath(), violation.getMessage());
      errors.add(error);
    }
    return errors.toString();
  }
}
