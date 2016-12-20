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

package com.vmware.photon.controller.api.model;

/**
 * Usage tag for this entity.
 * <p/>
 * <p/>
 * <code>MGMT</code> - Reserved for installing management components
 * <br/>
 * <code>CLOUD</code> - Resource reserved for cloud vms
 * <br/>
 * <code>IMAGE</code> - Resource reserved for images, Datastore only.
 */
public enum UsageTag {
  /**
   * NOTE: Please remember to ALWAYS update the static strings when updating the enum list!
   */
  MGMT,
  CLOUD,
  IMAGE;
  /**
   * These strings are for swagger documentation.
   * Currently swagger's ApiModelProperty.allowableValues property do not allow non-constant values
   * so please maintain this string when making changes to this enum.
   * Only add the new values to appropriate strings, or create a new one when a new combination appears.
   */
  public static final String PORTGROUP_USAGES = "MGMT, CLOUD";
  public static final String HOST_USAGES = "MGMT, CLOUD";
  public static final String DATASTORE_USAGES = "MGMT, CLOUD, IMAGE";


}
