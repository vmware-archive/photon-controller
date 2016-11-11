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

package com.vmware.photon.controller.dhcpagent.dhcpdrivers;

/**
 * Defines DHCP driver constants.
 */
public class Constants {

  /**
   * Dnsmasq host directory path.
   */
  public static final String DNSMASQ_HOST_DIR_PATH = "/etc/dhcp-hosts";

  /**
   * Dnsmasq option directory path.
   */
  public static final String DNSMASQ_OPTION_DIR_PATH = "/etc/dhcp-options";

  /**
   * The file path for dnsmasq configuration file.
   */
  public static final String DNSMASQ_CONF_PATH = "/etc/dnsmasq.conf";

  /**
   * The file path for dnsmasq lease file.
   */
  public static final String DNSMASQ_LEASE_PATH = "/var/lib/misc/dnsmasq.leases";

  /**
   * The 5 second timeout to wait for a process to finish running a command.
   */
  public static final long TIMEOUT = 5;
}
