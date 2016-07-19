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

import com.vmware.photon.controller.api.model.PortGroup;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.UsageTag;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.PortGroupNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.PortGroupService;
import com.vmware.photon.controller.cloudstore.xenon.entity.PortGroupServiceFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceUtils;

import com.google.common.base.Optional;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Tests {@link PortGroupXenonBackend}.
 */
public class PortGroupXenonBackendTest {

  private PortGroupService.State createPortGroup(
      ApiFeXenonRestClient xenonClient, String name, List<UsageTag> usageTags) {
    PortGroupService.State portGroup = new PortGroupService.State();
    portGroup.name = name;
    portGroup.usageTags = usageTags;

    com.vmware.xenon.common.Operation result = xenonClient.post(PortGroupServiceFactory.SELF_LINK, portGroup);
    return result.getBody(PortGroupService.State.class);
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests {@link PortGroupBackend#toApiRepresentation(String)}.
   */
  public class ToApiRepresentationTest {

    private ApiFeXenonRestClient xenonClient;

    private PortGroupBackend portGroupBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(
          null,
          PortGroupServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new PortGroupServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonClient =
          new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1));

      portGroupBackend = new PortGroupXenonBackend(xenonClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      xenonClient.stop();
    }

    @Test
    public void testSuccess() throws Throwable {
      PortGroupService.State createdPortGroup = createPortGroup(xenonClient, "P1", null);
      String portGroupId = ServiceUtils.getIDFromDocumentSelfLink(createdPortGroup.documentSelfLink);

      PortGroup portGroup = portGroupBackend.toApiRepresentation(portGroupId);
      assertThat(portGroup.getName(), is("P1"));
      assertThat(portGroup.getUsageTags(), nullValue());
    }

    @Test(expectedExceptions = PortGroupNotFoundException.class,
        expectedExceptionsMessageRegExp = "^Port Group #id1 not found$")
    public void testGetNonExistingPortGroup() throws PortGroupNotFoundException {
      portGroupBackend.toApiRepresentation("id1");
    }
  }

  /**
   * Tests {@link PortGroupXenonBackend#filter(com.google.common.base.Optional, com.google.common.base.Optional)}.
   */
  public class FilterTest {

    private ApiFeXenonRestClient xenonClient;

    private PortGroupBackend portGroupBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(
          null,
          PortGroupServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new PortGroupServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonClient =
          new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1));

      portGroupBackend = new PortGroupXenonBackend(xenonClient);

      // Create port groups for filtering
      createPortGroup(xenonClient, "P1", null);
      createPortGroup(xenonClient, "P2", null);
      List<UsageTag> usageTags = new ArrayList<>();
      usageTags.add(UsageTag.CLOUD);
      createPortGroup(xenonClient, "P1", usageTags);
      usageTags = new ArrayList<>();
      usageTags.add(UsageTag.MGMT);
      createPortGroup(xenonClient, "P2", usageTags);
      usageTags = new ArrayList<>();
      usageTags.add(UsageTag.MGMT);
      usageTags.add(UsageTag.CLOUD);
      createPortGroup(xenonClient, "P1", usageTags);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      xenonClient.stop();
    }

    @Test(dataProvider = "filterParams")
    public void testSuccess(
        Optional<String> name,
        Optional<UsageTag> usageTag,
        Optional<Integer> pageSize,
        int expectedSize) {
      ResourceList<PortGroup> portGroups = portGroupBackend.filter(name, usageTag, pageSize);
      assertThat(portGroups.getItems().size(), is(expectedSize));
    }

    @DataProvider(name = "filterParams")
    public Object[][] getFilterParams() {
      return new Object[][]{
          {Optional.absent(), Optional.absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 5},
          {Optional.of("P"), Optional.absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 0},
          {Optional.of("P1"), Optional.absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 3},
          {Optional.of("P2"), Optional.absent(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 2},
          {Optional.absent(), Optional.of(UsageTag.CLOUD), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 2},
          {Optional.absent(), Optional.of(UsageTag.MGMT), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 2},
          {Optional.of("P1"), Optional.of(UsageTag.MGMT), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 1},
          {Optional.of("P2"), Optional.of(UsageTag.MGMT), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 1},
          {Optional.of("P1"), Optional.of(UsageTag.CLOUD), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 2},
          {Optional.of("P2"), Optional.of(UsageTag.CLOUD), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), 0},
          {Optional.absent(), Optional.absent(), Optional.absent(), 5},
          {Optional.absent(), Optional.absent(), Optional.of(1), 1},
          {Optional.of("P1"), Optional.absent(), Optional.of(3), 3},
          {Optional.of("P1"), Optional.absent(), Optional.of(1), 1},
      };
    }
  }
}
