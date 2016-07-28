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
 * String constants of routes used by host related resource classes.
 */
public class HostResourceRoutes {

  public static final String API = "/hosts";

  public static final String HOST_PATH = "/hosts/{id}";

  public static final String HOST_VMS_PATH = "/hosts/{id}/vms";

  public static final String HOST_TASKS_PATH = "/hosts/{id}/tasks";

  public static final String HOST_SUSPEND_ACTION = "/suspend";
  public static final String HOST_ENTER_MAINTENANCE_ACTION = "/enter_maintenance";
  public static final String HOST_EXIT_MAINTENANCE_ACTION = "/exit_maintenance";

  public static final String HOST_RESUME_ACTION = "/resume";

  public static final String HOST_SET_AVAILABILITY_ZONE_ACTION = "/set_availability_zone";
}
