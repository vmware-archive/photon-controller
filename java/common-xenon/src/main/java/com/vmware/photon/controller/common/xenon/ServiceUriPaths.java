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

package com.vmware.photon.controller.common.xenon;

/**
 * ESX cloud service paths.
 */
public class ServiceUriPaths extends com.vmware.xenon.services.common.ServiceUriPaths {
  public static final String SERVICES_ROOT = "/photon";

  public static final String STATUS_SERVICE = SERVICES_ROOT + "/status";

  public static final String LOGGER_CONTROL_SERVICE = SERVICES_ROOT + "/logger-control";

  public static final String CLOUDSTORE_ROOT = SERVICES_ROOT + "/cloudstore";

  public static final String CLOUDSTORE_GROOMERS_ROOT = CLOUDSTORE_ROOT + "/groomers";

  public static final String HOUSEKEEPER_ROOT = SERVICES_ROOT + "/housekeeper";

  public static final String CLUSTERMANAGER_ROOT = SERVICES_ROOT + "/clustermanager";

  public static final String APIBACKEND_ROOT = SERVICES_ROOT + "/apibackend";

  public static final String SCHEDULER_ROOT = SERVICES_ROOT + "/scheduler";

  public static final String DHCPAGENT_ROOT = SERVICES_ROOT + "/dhcpagent";

  public static final String CLUSTER_RESIZE_TASK_SERVICE =
      CLUSTERMANAGER_ROOT + "/cluster-resize-tasks";

  public static final String CLUSTER_DELETE_TASK_SERVICE =
      CLUSTERMANAGER_ROOT + "/cluster-delete-tasks";

  public static final String KUBERNETES_CLUSTER_CREATE_TASK_SERVICE =
      CLUSTERMANAGER_ROOT + "/kubernetes-cluster-create-tasks";

  public static final String MESOS_CLUSTER_CREATE_TASK_SERVICE =
      CLUSTERMANAGER_ROOT + "/mesos-cluster-create-tasks";

  public static final String SWARM_CLUSTER_CREATE_TASK_SERVICE =
      CLUSTERMANAGER_ROOT + "/swarm-cluster-create-tasks";

  public static final String DEFAULT_CLOUD_STORE_NODE_SELECTOR =
      com.vmware.xenon.services.common.ServiceUriPaths.DEFAULT_3X_NODE_SELECTOR;

  public static final String NODE_SELECTOR_FOR_SYMMETRIC_REPLICATION =
      com.vmware.xenon.services.common.ServiceUriPaths.DEFAULT_NODE_SELECTOR;

  public static final String DEPLOYER_ROOT = SERVICES_ROOT + "/deployer";
  public static final String UPGRADE_ROOT = SERVICES_ROOT + "/upgrade";
}
