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

import com.vmware.photon.controller.api.Datastore;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.apife.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.apife.exceptions.external.DatastoreNotFoundException;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Tests {@link DatastoreDcpBackend}.
 */
public class DatastoreDcpBackendTest {

  private DatastoreService.State createDatastore(
      ApiFeXenonRestClient dcpClient, String type, Set<String> tags) {
    DatastoreService.State datastore = new DatastoreService.State();
    datastore.id = UUID.randomUUID().toString();
    datastore.name = datastore.id;
    datastore.type = type;
    datastore.tags = tags;
    datastore.documentSelfLink = "/" + datastore.id;

    com.vmware.xenon.common.Operation result = dcpClient.post(DatastoreServiceFactory.SELF_LINK, datastore);
    return result.getBody(DatastoreService.State.class);
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests {@link DatastoreBackend#toApiRepresentation(String)}.
   */
  public class ToApiRepresentationTest {

    private ApiFeXenonRestClient dcpClient;

    private DatastoreBackend datastoreBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(
          null,
          DatastoreServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new DatastoreServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));

      dcpClient = new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(1));

      datastoreBackend = new DatastoreDcpBackend(dcpClient);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test
    public void testSuccess() throws Throwable {
      DatastoreService.State createdDatastore = createDatastore(dcpClient, "EXT3", null);
      String datastoreId = ServiceUtils.getIDFromDocumentSelfLink(createdDatastore.documentSelfLink);

      Datastore datastore = datastoreBackend.toApiRepresentation(datastoreId);
      assertThat(datastore.getType(), is("EXT3"));
      assertThat(datastore.getTags(), nullValue());
    }

    @Test(expectedExceptions = DatastoreNotFoundException.class,
        expectedExceptionsMessageRegExp = "^Datastore #id1 not found$")
    public void testGetNonExistingDatastore() throws DatastoreNotFoundException {
      datastoreBackend.toApiRepresentation("id1");
    }
  }

  /**
   * Tests {@link DatastoreDcpBackend#filter(Optional, Optional)}.
   */
  public class FilterTest {

    private ApiFeXenonRestClient dcpClient;

    private DatastoreBackend datastoreBackend;

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create(
          null,
          DatastoreServiceFactory.SELF_LINK,
          10, 10);

      host.startServiceSynchronously(new DatastoreServiceFactory(), null);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));

      dcpClient = new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(1));

      datastoreBackend = new DatastoreDcpBackend(dcpClient);

      // Create port groups for filtering
      createDatastore(dcpClient, "EXT3", null);
      createDatastore(dcpClient, "SHARED_VMFS", null);
      Set<String> tags = new HashSet<>();
      tags.add("tag1");
      createDatastore(dcpClient, "EXT3", tags);
      tags = new HashSet<>();
      tags.add("tag2");
      createDatastore(dcpClient, "SHARED_VMFS", tags);
      tags = new HashSet<>();
      tags.add("tag1");
      tags.add("tag2");
      createDatastore(dcpClient, "EXT3", tags);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      dcpClient.stop();
    }

    @Test(dataProvider = "filterParams")
    public void testSuccess(
        Optional<String> tag,
        int expectedSize) throws Throwable {
      ResourceList<Datastore> datastores = datastoreBackend.filter(tag, Optional.absent());
      assertThat(datastores.getItems().size(), is(expectedSize));
    }

    @Test(dataProvider = "filterParams")
    public void testFilterWithPagination(
            Optional<String> tag,
            int expectedSize) throws Throwable {
      ResourceList<Datastore> datastores = datastoreBackend.filter(tag, Optional.<Integer>absent());
      assertThat(datastores.getItems().size(), is(expectedSize));

      final int pageSize = 1;
      Set<Datastore> datastoreSet = new HashSet<>();
      datastores = datastoreBackend.filter(tag, Optional.of(pageSize));
      datastoreSet.addAll(datastores.getItems());

      while (datastores.getNextPageLink() != null) {
        datastores = datastoreBackend.getDatastoresPage(datastores.getNextPageLink());
        datastoreSet.addAll(datastores.getItems());
      }

      assertThat(datastoreSet.size(), is(expectedSize));
    }

    @DataProvider(name = "filterParams")
    public Object[][] getFilterParams() {
      return new Object[][]{
          {Optional.absent(), 5},
          {Optional.of("tag1"), 2},
          {Optional.of("tag2"), 2},
      };
    }
  }
}
