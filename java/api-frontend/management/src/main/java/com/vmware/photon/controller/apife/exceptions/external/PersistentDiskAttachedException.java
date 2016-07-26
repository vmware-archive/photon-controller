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

import com.vmware.photon.controller.apife.entities.BaseDiskEntity;
import com.vmware.photon.controller.apife.entities.VmEntity;

/**
 * Gets thrown when persistent disk is attached to the VM being deleted.
 */
public class PersistentDiskAttachedException extends ExternalException {
  private final String vmId;
  private final String diskId;
  private final String diskKind;

  public PersistentDiskAttachedException(BaseDiskEntity disk, VmEntity vm) {
    super(ErrorCode.PERSISTENT_DISK_ATTACHED);
    this.vmId = vm.getId();
    this.diskId = disk.getId();
    this.diskKind = disk.getKind();

    addData("vmId", vmId);
    addData("diskId", diskId);
    addData("diskKind", diskKind);
  }

  @Override
  public String getMessage() {
    return "Disk " + diskKind + "#" + diskId + " is attached to VM#" + vmId;
  }
}
