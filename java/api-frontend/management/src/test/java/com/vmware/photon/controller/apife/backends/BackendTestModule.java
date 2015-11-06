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

package com.vmware.photon.controller.apife.backends;

import com.google.inject.AbstractModule;

/**
 * The test module for Backends tests.
 */
public class BackendTestModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(FlavorBackend.class).to(FlavorSqlBackend.class);
    bind(ImageBackend.class).to(ImageSqlBackend.class);
    bind(TaskBackend.class).to(TaskSqlBackend.class);
    bind(NetworkBackend.class).to(NetworkSqlBackend.class);
    bind(PortGroupBackend.class).to(PortGroupSqlBackend.class);
    bind(StepBackend.class).to(StepSqlBackend.class);
    bind(EntityLockBackend.class).to(StepLockSqlBackend.class);
    bind(ProjectBackend.class).to(ProjectSqlBackend.class);
    bind(ResourceTicketBackend.class).to(ResourceTicketSqlBackend.class);
    bind(TenantBackend.class).to(TenantSqlBackend.class);
    bind(DiskBackend.class).to(DiskSqlBackend.class);
    bind(AttachedDiskBackend.class).to(AttachedDiskSqlBackend.class);
    bind(VmBackend.class).to(VmSqlBackend.class);
    bind(HostBackend.class).to(HostSqlBackend.class);
    bind(DeploymentBackend.class).to(DeploymentSqlBackend.class);
    bind(TombstoneBackend.class).to(TombstoneSqlBackend.class);
  }
}
