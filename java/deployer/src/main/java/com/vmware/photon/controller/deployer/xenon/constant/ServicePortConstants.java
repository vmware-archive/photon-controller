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

package com.vmware.photon.controller.deployer.xenon.constant;

/**
 * Defines ports for various services.
 */
public class ServicePortConstants {

  public static final int DEPLOYER_PORT = 18000;

  public static final int HOUSEKEEPER_PORT = 16000;

  public static final int LOADBALANCER_API_HTTP_PORT = 28080;

  public static final int LOADBALANCER_API_HTTPS_PORT = 443;

  public static final int LOADBALANCER_MGMT_UI_HTTP_PORT = 80;

  public static final int LOADBALANCER_MGMT_UI_HTTPS_PORT = 4343;

  public static final int MANAGEMENT_API_PORT = 9000;

  public static final int ROOT_SCHEDULER_PORT = 13010;

  public static final int ZOOKEEPER_PORT = 2181;

  public static final int CLOUD_STORE_PORT = 19000;

  public static final int LIGHTWAVE_PORT = 443;

  public static final int MANAGEMENT_UI_HTTP_PORT = 20000;

  public static final int MANAGEMENT_UI_HTTPS_PORT = 20001;

  public static final int ESXI_PORT = 443;

  public static final int DHCP_AGENT_PORT = 17000;
}
