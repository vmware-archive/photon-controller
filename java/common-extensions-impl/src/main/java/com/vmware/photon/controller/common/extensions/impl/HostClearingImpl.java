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

package com.vmware.photon.controller.common.extensions.impl;

import com.vmware.photon.controller.common.extensions.api.HostClearing;
import com.vmware.photon.controller.common.extensions.api.HostClearingException;

/**
 * This interface manages image on datastore.
 */
public class HostClearingImpl implements HostClearing {

  /**
   * Initiate Vm Migration.
   *
   * @param hostId     the id of the vm being migrated
   * @return operationId
   * @throws com.vmware.photon.controller.common.extensions.api.HostClearingException
   */
  public String execute(String hostId) throws HostClearingException {
    return "NoOpId";
  }

  /**
   * Check migration result.
   *
   * @param operationId
   * @return
   * @throws com.vmware.photon.controller.common.extensions.api.HostClearingException
   */
  public boolean checkResult(String operationId) throws HostClearingException {
    return true;
  }

}
