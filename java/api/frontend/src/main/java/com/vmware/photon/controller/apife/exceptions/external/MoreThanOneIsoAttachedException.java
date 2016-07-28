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

import org.apache.commons.lang3.StringUtils;

/**
 * Exception thrown during detach_iso operation when there is more
 * than one ISO attached to the VM.
 */
public class MoreThanOneIsoAttachedException extends ExternalException {

  private String id;

  public MoreThanOneIsoAttachedException() {
    super(ErrorCode.MORE_THAN_ONE_ISO_ATTACHED);
  }

  public MoreThanOneIsoAttachedException(String id) {
    this();

    this.id = id;
    addData("id", id);
  }

  @Override
  public String getMessage() {
    if (!StringUtils.isBlank(id)) {
      return String.format("More than one ISO is attached to vm %s", id);
    }
    return "More than one ISO is attached to vm";
  }
}
