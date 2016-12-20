/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.api.model;

/**
 * An enum that defines what a reserved IP is being used for.
 *
 * When a virtual network is being created, 3 IP addresses are reserved.
 * At this point, only one is being used for GATEWAY. The other two
 * are simply reserved for future uses.
 */
public enum ReservedIpType {
  GATEWAY, UNUSED1, UNUSED2
}
