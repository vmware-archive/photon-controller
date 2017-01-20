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

package com.vmware.photon.controller.api.frontend.resources.routes;

/**
 * String constants of routes used by services related resource classes.
 */
public class ServiceResourceRoutes {

  public static final String API = "/services";

  public static final String SERVICES_PATH = "/services/{id}";

  public static final String SERVICES_VMS_PATH = "/services/{id}/vms";

  public static final String PROJECTS_SERVICES_PATH = "/projects/{id}/services";

  public static final String SERVICES_RESIZE_PATH = "/services/{id}/resize";

  public static final String SERVICES_TRIGGER_MAINTENANCE_PATH = "/trigger_maintenance";
}
