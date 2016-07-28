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

  public static final String APIFE_SERVICE_NAME = "api";

  public static final String CLOUDSTORE_SERVICE_NAME = "cloudstore";

  public static final String DEPLOYER_SERVICE_NAME = "deployer";

  public static final String HOUSEKEEPER_SERVICE_NAME = "housekeeper";

  public static final String SCHEDULER_SERVICE_NAME = "root-scheduler";

  public static final int LOADBALANCER_API_HTTP_PORT = 28080;

  public static final int LOADBALANCER_API_HTTPS_PORT = 443;

  public static final int LOADBALANCER_MGMT_UI_HTTP_PORT = 80;

  public static final int LOADBALANCER_MGMT_UI_HTTPS_PORT = 4343;

  public static final int MANAGEMENT_API_PORT = 9000;

  public static final int ZOOKEEPER_PORT = 2181;

  public static final int PHOTON_CONTROLLER_PORT = 19000;

  public static final int LIGHTWAVE_PORT = 443;

  public static final int MANAGEMENT_UI_HTTP_PORT = 20000;

  public static final int MANAGEMENT_UI_HTTPS_PORT = 20001;

  public static final int ESXI_PORT = 443;

  public static final int DHCP_AGENT_PORT = 17000;

  public static final int DEFAULT_SCHEDULED_THREAD_POOL_SIZE = 10;

  private Constants() {
  }
}
