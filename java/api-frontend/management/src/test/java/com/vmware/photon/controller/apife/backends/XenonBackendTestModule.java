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

import com.vmware.photon.controller.apibackend.helpers.TestHost;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.backends.clients.PhotonControllerXenonRestClient;
import com.vmware.photon.controller.cloudstore.xenon.CloudStoreServiceGroup;
import com.vmware.photon.controller.common.tests.nsx.NsxClientMock;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.nsxclient.NsxClient;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;

/**
 * The test module for Backends tests.
 */
public class XenonBackendTestModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(FlavorBackend.class).to(FlavorXenonBackend.class);
    bind(ImageBackend.class).to(ImageXenonBackend.class);
    bind(NetworkBackend.class).to(NetworkXenonBackend.class);
    bind(DatastoreBackend.class).to(DatastoreXenonBackend.class);
    bind(EntityLockBackend.class).to(EntityLockXenonBackend.class);
    bind(TaskBackend.class).to(TaskXenonBackend.class);
    bind(StepBackend.class).to(TaskXenonBackend.class); // Step backend was merged into Task backend
    bind(ProjectBackend.class).to(ProjectXenonBackend.class);
    bind(TenantBackend.class).to(TenantXenonBackend.class);
    bind(ResourceTicketBackend.class).to(ResourceTicketXenonBackend.class);
    bind(DiskBackend.class).to(DiskXenonBackend.class);
    bind(AttachedDiskBackend.class).to(AttachedDiskXenonBackend.class);
    bind(VmBackend.class).to(VmXenonBackend.class);
    bind(TombstoneBackend.class).to(TombstoneXenonBackend.class);
    bind(HostBackend.class).to(HostXenonBackend.class);
    bind(DeploymentBackend.class).to(DeploymentXenonBackend.class);
    bind(AvailabilityZoneBackend.class).to(AvailabilityZoneXenonBackend.class);

    customConfigure();
  }

  protected void customConfigure() {
    bindConstant().annotatedWith(Names.named("useVirtualNetwork")).to(false);
  }

  @Provides
  @Singleton
  BasicServiceHost getBasicServiceHost() throws Throwable {
    NsxClient nsxClientMock = new NsxClientMock.Builder()
        .listLogicalRouterPorts(true)
        .deleteLogicalRouterPort(true)
        .deleteLogicalPort(true)
        .deleteLogicalRouter(true)
        .deleteLogicalSwitch(true)
        .checkLogicalRouterPortExistence(true)
        .checkLogicalSwitchPortExistence(true)
        .checkLogicalRouterExistence(true)
        .checkLogicalSwitchExistence(true)
        .build();
    NsxClientFactory nsxClientFactory = mock(NsxClientFactory.class);
    doReturn(nsxClientMock).when(nsxClientFactory).create(any(String.class), any(String.class), any(String.class));

    BasicServiceHost host = new TestHost.Builder()
        .cloudStoreHelper(new CloudStoreHelper())
        .nsxClientFactory(nsxClientFactory)
        .build();
    ServiceHostUtils.startServices(host, CloudStoreServiceGroup.FACTORY_SERVICES);
    ServiceHostUtils.startFactoryServices(host, CloudStoreServiceGroup.FACTORY_SERVICES_MAP);

    return host;
  }

  @Provides
  @Singleton
  ApiFeXenonRestClient getApiFeXenonRestClient(BasicServiceHost host) {
    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    //since all our cloud store calls are synchronous we should only need one thread to handle them
    //however, if the test runner executes multiple tests in parallel using the same
    //instance of this module then the host and the rest client singleton instance will
    //be shared. To address this scenario I am setting the thread pool to be 4 assuming
    //that there are 4 cores present in the machine executing the tests.
    return new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(128), Executors.newScheduledThreadPool(1)
        , host);
  }

  @Provides
  @Singleton
  PhotonControllerXenonRestClient getPhotonControllerXenonRestClient(BasicServiceHost host) throws URISyntaxException {
    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
    return new PhotonControllerXenonRestClient(serverSet,
        Executors.newFixedThreadPool(128),
        Executors.newScheduledThreadPool(1), host);
  }
}
