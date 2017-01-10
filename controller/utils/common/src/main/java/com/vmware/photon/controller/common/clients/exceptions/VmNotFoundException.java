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

package com.vmware.photon.controller.common.clients.exceptions;

/**
 * VM Not Found Exception.
 * <p/>
 * Thrown when the referenced VM was not found on the host.
 */
public class VmNotFoundException extends RpcException {
  public VmNotFoundException(String message) {
    super(message);
  }

  public VmNotFoundException(Throwable cause) {
    super(cause);
  }
}
