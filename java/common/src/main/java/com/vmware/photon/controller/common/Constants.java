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

package com.vmware.photon.controller.common;

/**
 * Constants available to all EsxCloud projects.
 */
public class Constants {
  public static final Void VOID = null;

  public static final String PROJECT_NAME = "mgmt-project";

  public static final String RESOURCE_TICKET_NAME = "mgmt-res-ticket";

  public static final String TENANT_NAME = "mgmt-tenant";

  public static final String APIFE_SERVICE_NAME = "apife";

  public static final String CLOUDSTORE_SERVICE_NAME = "cloudstore";

  public static final String DEPLOYER_SERVICE_NAME = "deployer";

  public static final String HOUSEKEEPER_SERVICE_NAME = "housekeeper";

  public static final String SCHEDULER_SERVICE_NAME = "root-scheduler";

  private Constants() {
  }
}
