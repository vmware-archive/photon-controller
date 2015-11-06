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
 * String constants of routes used by tenant related resource classes.
 */
public class TenantResourceRoutes {

  public static final String API = "/tenants";

  public static final String TENANT_PATH = "/tenants/{id}";

  public static final String TENANT_PROJECTS_PATH = "/tenants/{id}/projects";

  public static final String TENANT_RESOURCE_TICKETS_PATH = "/tenants/{id}/resource-tickets";

  public static final String TENANT_TASKS_PATH = "/tenants/{id}/tasks";

  public static final String TENANT_SET_SECURITY_GROUPS_PATH = "/tenants/{id}/set_security_groups";
}
