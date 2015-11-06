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

import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.cloudstore.dcp.CloudStoreDcpHost;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.thrift.StaticServerSet;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * The test module for Backends tests.
 */
public class DcpBackendTestModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(FlavorBackend.class).to(FlavorDcpBackend.class);
    bind(ImageBackend.class).to(ImageDcpBackend.class);
    bind(NetworkBackend.class).to(NetworkDcpBackend.class);
    bind(DatastoreBackend.class).to(DatastoreDcpBackend.class);
    bind(PortGroupBackend.class).to(PortGroupDcpBackend.class);
    bind(EntityLockBackend.class).to(EntityLockDcpBackend.class);
    bind(TaskBackend.class).to(TaskDcpBackend.class);
    bind(StepBackend.class).to(TaskDcpBackend.class); // Step backend was merged into Task backend
    bind(ProjectBackend.class).to(ProjectDcpBackend.class);
    bind(TenantBackend.class).to(TenantDcpBackend.class);
    bind(ResourceTicketBackend.class).to(ResourceTicketDcpBackend.class);
    bind(DiskBackend.class).to(DiskDcpBackend.class);
    bind(AttachedDiskBackend.class).to(AttachedDiskDcpBackend.class);
    bind(VmBackend.class).to(VmDcpBackend.class);
    bind(TombstoneBackend.class).to(TombstoneDcpBackend.class);
    bind(HostBackend.class).to(HostDcpBackend.class);
    bind(DeploymentBackend.class).to(DeploymentDcpBackend.class);
  }

  @Provides
  @Singleton
  BasicServiceHost getBasicServiceHost() throws Throwable {
    BasicServiceHost host = BasicServiceHost.create();
    host.startFactoryServicesSynchronously(CloudStoreDcpHost.FACTORY_SERVICES);
    return host;
  }

  @Provides
  @Singleton
  ApiFeDcpRestClient getApiFeDcpRestClient(BasicServiceHost host) {
    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    //since all our cloud store calls are synchronous we should only need one thread to handle them
    //however, if the test runner executes multiple tests in parallel using the same
    //instance of this module then the host and the rest client singleton instance will
    //be shared. To address this scenario I am setting the thread pool to be 4 assuming
    //that there are 4 cores present in the machine executing the tests.
    return new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(128));
  }
}
