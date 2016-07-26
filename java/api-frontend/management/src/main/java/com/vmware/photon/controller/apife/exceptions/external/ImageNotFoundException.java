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

package com.vmware.photon.controller.apife.exceptions.external;

/**
 * Gets thrown when requested disk is not found.
 */
public class ImageNotFoundException extends ExternalException {
  private final Type type;
  private final String value;

  public ImageNotFoundException(Type type, String value) {
    super(ErrorCode.IMAGE_NOT_FOUND);
    this.type = type;
    this.value = value;

    addData(type.getType(), value);
  }

  @Override
  public String getMessage() {
    return String.format("Image %s '%s' not found", type.getType(), value);
  }

  /**
   * Type of ImageNotFoundException.
   */
  public static enum Type {
    ID("id"),
    NAME("name");

    private final String type;

    Type(String type) {
      this.type = type;
    }

    public String getType() {
      return this.type;
    }
  }
}
