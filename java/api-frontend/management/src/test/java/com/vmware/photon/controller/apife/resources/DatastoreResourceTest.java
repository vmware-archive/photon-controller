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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Datastore;
import com.vmware.photon.controller.apife.clients.DatastoreFeClient;
import com.vmware.photon.controller.apife.exceptions.external.DatastoreNotFoundException;
import com.vmware.photon.controller.apife.resources.datastore.DatastoreResource;
import com.vmware.photon.controller.apife.resources.routes.DatastoreResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.collect.ImmutableSet;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

/**
 * Tests {@link com.vmware.photon.controller.apife.resources.datastore.DatastoreResource}.
 */
public class DatastoreResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(DatastoreResourceTest.class);

  private String taskId = "task1";
  private String datastoreId = "datastore1";
  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();
  private String datastoreRoutePath =
      UriBuilder.fromPath(DatastoreResourceRoutes.DATASTORE_PATH).build(datastoreId).toString();

  @Mock
  private DatastoreFeClient datastoreFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new DatastoreResource(datastoreFeClient));
  }

  @Test
  public void testGetDatastore() throws ExternalException {
    Datastore datastore = new Datastore();

    datastore.setId(datastoreId);
    datastore.setTags(ImmutableSet.of("tag1"));
    datastore.setSelfLink("self link");

    doReturn(datastore).when(datastoreFeClient).getDatastore(datastoreId);

    Response clientResponse = client()
        .target(datastoreRoutePath)
        .request(MediaType.APPLICATION_JSON)
        .get();
    assertThat(clientResponse.getStatus(), is(200));

    Datastore gotDatastore = clientResponse.readEntity(Datastore.class);
    assertThat(gotDatastore, is(datastore));
  }

  @Test
  public void testGetNonExsitingDatastore() throws ExternalException {
    doThrow(new DatastoreNotFoundException(datastoreId)).when(datastoreFeClient).getDatastore(datastoreId);

    Response clientResponse = client()
        .target(datastoreRoutePath)
        .request(MediaType.APPLICATION_JSON)
        .get();

    assertThat(clientResponse.getStatus(), is(404));
  }

}
