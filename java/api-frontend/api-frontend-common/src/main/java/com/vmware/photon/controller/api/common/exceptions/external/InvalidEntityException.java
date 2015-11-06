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

package com.vmware.photon.controller.api.common.exceptions.external;

import com.google.common.collect.ImmutableList;

/**
 * Gets thrown/created when the parameters passed to create an entity is invalid.
 */
public class InvalidEntityException extends ExternalException {
  private final String message;
  private final ImmutableList<String> errors;

  public InvalidEntityException(String message, Iterable<String> errors) {
    super(ErrorCode.INVALID_ENTITY);
    this.message = message;
    this.errors = ImmutableList.copyOf(errors);
  }

  @Override
  public String getMessage() {
    return String.format("The supplied entity is invalid: %s", message);
  }

  public ImmutableList<String> getErrors() {
    return errors;
  }
}
