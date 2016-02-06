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

package com.vmware.photon.controller.cloudstore.dcp.helpers;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.CloudStoreConfig;
import com.vmware.photon.controller.cloudstore.CloudStoreConfigTest;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.config.BadConfigException;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.dcp.DcpHostInfoProvider;
import com.vmware.photon.controller.common.dcp.MultiHostEnvironment;
import com.vmware.photon.controller.common.thrift.ThriftModule;
import com.vmware.photon.controller.common.thrift.ThriftServiceModule;
import com.vmware.photon.controller.common.zookeeper.ZookeeperModule;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.xenon.common.ServiceHost;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * This class implements helper routines for tests.
 */
public class TestHelper {

  public static Injector createInjector(String configFileResourcePath)
      throws BadConfigException {
    CloudStoreConfig config = ConfigBuilder.build(CloudStoreConfig.class,
        CloudStoreConfigTest.class.getResource(configFileResourcePath).getPath());
    return Guice.createInjector(
        new TestCloudStoreModule(config),
        new ThriftModule(),
        new ThriftServiceModule<>(
            new TypeLiteral<Host.AsyncClient>() {
            }
        ),
        new ZookeeperModule(config.getZookeeper()));
  }

  public static HostService.State getHostServiceStartState(Set<String> usageTags) {
    HostService.State startState = new HostService.State();
    startState.state = HostState.CREATING;
    startState.hostAddress = "hostAddress";
    startState.userName = "userName";
    startState.password = "password";
    startState.availabilityZoneId = "availabilityZone";
    startState.esxVersion = "6.0";
    startState.usageTags = new HashSet<>(usageTags);
    startState.reportedImageDatastores = new HashSet<>(Arrays.asList("datastore1"));

    if (usageTags.contains(UsageTag.MGMT.name())) {
      startState.metadata = new HashMap<>();
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_DATASTORE, "datastore1");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_DNS_SERVER, "8.8.8.8");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_GATEWAY, "8.8.8.143");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_IP, "8.8.8.27");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_NETWORK_NETMASK, "255.255.255.0");
      startState.metadata.put(HostService.State.METADATA_KEY_NAME_MANAGEMENT_PORTGROUP, "VM Network");
    }

    return startState;
  }

  public static <H extends ServiceHost & DcpHostInfoProvider> HostService.State createHostService(
      MultiHostEnvironment<H> testEnvironment,
      Set<String> usageTags) throws Throwable {
    return createHostService(testEnvironment, getHostServiceStartState(usageTags));
  }

  public static <H extends ServiceHost & DcpHostInfoProvider> HostService.State createHostService(
      MultiHostEnvironment<H> testEnvironment,
      HostService.State startState)
      throws Throwable {
    return testEnvironment.callServiceSynchronously(
        HostServiceFactory.SELF_LINK,
        startState,
        HostService.State.class);
  }

  public static HostService.State getHostServiceStartState() {
    return getHostServiceStartState(ImmutableSet.of(UsageTag.MGMT.name(), UsageTag.CLOUD.name()));
  }

  /**
   * Class for constructing config injection.
   */
  public static class TestInjectedConfig {
    private String bind;
    private String registrationAddress;
    private int port;
    private String path;

    @Inject
    public TestInjectedConfig(
        @CloudStoreConfig.Bind String bind,
        @CloudStoreConfig.RegistrationAddress String registrationAddress,
        @CloudStoreConfig.Port int port,
        @CloudStoreConfig.StoragePath String path) {
      this.bind = bind;
      this.registrationAddress = registrationAddress;
      this.port = port;
      this.path = path;
    }

    public String getBind() {
      return bind;
    }

    public String getRegistrationAddress() {
      return registrationAddress;
    }

    public int getPort() {
      return port;
    }

    public String getPath() {
      return path;
    }
  }

}
