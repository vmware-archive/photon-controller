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

package com.vmware.photon.controller.apife.resources.routes;

/**
 * String constants of routes used by network related resource classes.
 */
public class NetworkResourceRoutes {

  public static final String API = "/networks";

  public static final String NETWORK_PATH = "/networks/{id}";

  public static final String NETWORK_SET_PORTGROUPS_PATH = "/networks/{id}/set_portgroups";

  public static final String NETWORK_SET_DEFAULT_ACTION = "/set_default";
}
