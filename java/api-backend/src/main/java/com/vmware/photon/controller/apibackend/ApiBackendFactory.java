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

package com.vmware.photon.controller.apibackend;

import com.vmware.photon.controller.apibackend.tasks.network.virtual.ConnectVmToSwitchTaskService;
import com.vmware.photon.controller.apibackend.tasks.network.virtual.CreateLogicalPortsTaskService;
import com.vmware.photon.controller.apibackend.tasks.network.virtual.CreateLogicalRouterTaskService;
import com.vmware.photon.controller.apibackend.tasks.network.virtual.CreateLogicalSwitchTaskService;
import com.vmware.photon.controller.apibackend.tasks.network.virtual.DeleteLogicalPortsTaskService;
import com.vmware.photon.controller.apibackend.tasks.network.virtual.DeleteLogicalRouterTaskService;
import com.vmware.photon.controller.apibackend.tasks.network.virtual.DeleteLogicalSwitchTaskService;
import com.vmware.photon.controller.apibackend.tasks.network.virtual.DisconnectVmFromSwitchTaskService;
import com.vmware.photon.controller.apibackend.workflows.network.virtual.AssignFloatingIpToVmWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.network.virtual.ConfigureDhcpWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.network.virtual.CreateVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.network.virtual.DeleteVirtualNetworkWorkflowService;
import com.vmware.photon.controller.apibackend.workflows.network.virtual.ReleaseFloatingIpFromVmWorkflowService;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Common factory used for ApiBackend.
 */
public class ApiBackendFactory {

  /**
   * All Xenon Factory Services in api-backend.
   */
  public static final Map<Class<? extends Service>, Supplier<FactoryService>> FACTORY_SERVICES_MAP =
      ImmutableMap.<Class<? extends Service>, Supplier<FactoryService>>builder()
      // tasks
      .put(CreateLogicalRouterTaskService.class, CreateLogicalRouterTaskService::createFactory)
      .put(CreateLogicalSwitchTaskService.class, CreateLogicalSwitchTaskService::createFactory)
      .put(CreateLogicalPortsTaskService.class, CreateLogicalPortsTaskService::createFactory)
      .put(DeleteLogicalSwitchTaskService.class, DeleteLogicalSwitchTaskService::createFactory)
      .put(DeleteLogicalRouterTaskService.class, DeleteLogicalRouterTaskService::createFactory)
      .put(DeleteLogicalPortsTaskService.class, DeleteLogicalPortsTaskService::createFactory)
      .put(ConnectVmToSwitchTaskService.class, ConnectVmToSwitchTaskService::createFactory)
      .put(DisconnectVmFromSwitchTaskService.class, DisconnectVmFromSwitchTaskService::createFactory)

      // workflows
      .put(CreateVirtualNetworkWorkflowService.class, CreateVirtualNetworkWorkflowService::createFactory)
      .put(DeleteVirtualNetworkWorkflowService.class, DeleteVirtualNetworkWorkflowService::createFactory)
      .put(ConfigureDhcpWorkflowService.class, ConfigureDhcpWorkflowService::createFactory)
      .put(AssignFloatingIpToVmWorkflowService.class, AssignFloatingIpToVmWorkflowService::createFactory)
      .put(ReleaseFloatingIpFromVmWorkflowService.class, ReleaseFloatingIpFromVmWorkflowService::createFactory)
      .build();
}
