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

package com.vmware.photon.controller.api.frontend.resources.routes;

/**
 * String constants of routes used by Deployment related resource classes.
 */
public class DeploymentResourceRoutes {

  public static final String API = "/deployments";

  public static final String DEPLOYMENT_PATH = "/deployments/{id}";

  public static final String PERFORM_DEPLOYMENT_ACTION = "/deploy";

  public static final String DEPLOYMENT_DESTROY_ACTION = "/destroy";

  public static final String PAUSE_SYSTEM_ACTION = "/pause_system";

  public static final String PAUSE_BACKGROUND_TASKS_ACTION = "/pause_background_tasks";

  public static final String RESUME_SYSTEM_ACTION = "/resume_system";

  public static final String INITIALIZE_MIGRATION_ACTION = "/initialize_migration";

  public static final String FINALIZE_MIGRATION_ACTION = "/finalize_migration";

  public static final String ENABLE_CLUSTER_TYPE_ACTION = "/enable_cluster_type";

  public static final String DISABLE_CLUSTER_TYPE_ACTION = "/disable_cluster_type";

  public static final String DEPLOYMENT_VMS_PATH = "/deployments/{id}/vms";

  public static final String DEPLOYMENT_HOSTS_PATH = "/deployments/{id}/hosts";

  public static final String DEPLOYMENT_ADMIN_GROUPS_PATH = "/deployments/{id}/set_security_groups";

  public static final String SET_IMAGE_DATASTORES_ACTION = "/set_image_datastores";

  public static final String ENABLE_DHCP_ACTION = "/enable_dhcp";

  public static final String DEPLOYMENT_SIZE_PATH = "/size";
}
