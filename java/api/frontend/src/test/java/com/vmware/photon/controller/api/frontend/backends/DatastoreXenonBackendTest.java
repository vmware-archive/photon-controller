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

package com.vmware.photon.controller.api.frontend.backends;

import com.vmware.photon.controller.api.frontend.backends.clients.ApiFeXenonRestClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.DatastoreNotFoundException;
import com.vmware.photon.controller.api.model.Datastore;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DatastoreServiceFactory;
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
 * Tests {@link DatastoreXenonBackend}.
 */
public class DatastoreXenonBackendTest {

  private ApiFeXenonRestClient xenonClient;

  private DatastoreBackend datastoreBackend;

  private BasicServiceHost host;

  private DatastoreService.State createDatastore(
      ApiFeXenonRestClient xenonClient, String type, Set<String> tags) {
    DatastoreService.State datastore = new DatastoreService.State();
    datastore.id = UUID.randomUUID().toString();
    datastore.name = datastore.id;
    datastore.type = type;
    datastore.tags = tags;
    datastore.documentSelfLink = "/" + datastore.id;

    com.vmware.xenon.common.Operation result = xenonClient.post(DatastoreServiceFactory.SELF_LINK, datastore);
    return result.getBody(DatastoreService.State.class);
  }

  private void setupCommon() throws Throwable {
    host = BasicServiceHost.create(
        null,
        DatastoreServiceFactory.SELF_LINK,
        10, 10);

    host.startServiceSynchronously(new DatastoreServiceFactory(), null);

    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));

    xenonClient =
        new ApiFeXenonRestClient(serverSet, Executors.newFixedThreadPool(1), Executors.newScheduledThreadPool(1));

    datastoreBackend = new DatastoreXenonBackend(xenonClient);
  }

  public void tearDownCommon() throws Throwable {
    if (host != null) {
      BasicServiceHost.destroy(host);
    }

    if (xenonClient != null) {
      xenonClient.stop();
    }
  }

  @Test
  private void dummy() {
  }

  /**
   * Tests {@link DatastoreBackend#toApiRepresentation(String)}.
   */
  public class ToApiRepresentationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setupCommon();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      tearDownCommon();
    }

    @Test
    public void testSuccess() throws Throwable {
      DatastoreService.State createdDatastore = createDatastore(xenonClient, "EXT3", null);
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
   * Tests for querying datastore.
   * {@link DatastoreXenonBackend#filter(Optional, Optional)}.
   * {@link DatastoreXenonBackend#getNumberDatastores()}.
   */
  public class QueryTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setupCommon();

      // Create datastores for filtering
      createDatastore(xenonClient, "EXT3", null);
      createDatastore(xenonClient, "SHARED_VMFS", null);
      Set<String> tags = new HashSet<>();
      tags.add("tag1");
      createDatastore(xenonClient, "EXT3", tags);
      tags = new HashSet<>();
      tags.add("tag2");
      createDatastore(xenonClient, "SHARED_VMFS", tags);
      tags = new HashSet<>();
      tags.add("tag1");
      tags.add("tag2");
      createDatastore(xenonClient, "EXT3", tags);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      tearDownCommon();
    }

    @Test
    public void queryNumberHosts() throws Throwable {
      int num = datastoreBackend.getNumberDatastores();
      assertThat(num, is(5));
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
