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

package com.vmware.photon.controller.api.frontend.resources;

import com.vmware.photon.controller.api.frontend.clients.AvailabilityZoneFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.AvailabilityZoneNotFoundException;
import com.vmware.photon.controller.api.frontend.resources.availabilityzone.AvailabilityZoneResource;
import com.vmware.photon.controller.api.frontend.resources.routes.AvailabilityZonesResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.AvailabilityZone;
import com.vmware.photon.controller.api.model.Task;

import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.availabilityzone.AvailabilityZoneResource}.
 */
public class AvailabilityZoneResourceTest extends ResourceTest {

  private String availabilityZoneId = "availabilityZone1";

  private String availabilityZoneRoutePath =
      UriBuilder.fromPath(AvailabilityZonesResourceRoutes.AVAILABILITYZONE_PATH).build(availabilityZoneId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private AvailabilityZoneFeClient availabilityZoneFeClient;

  @Override
  protected void setUpResources() throws Exception {
    AvailabilityZoneResource resource = new AvailabilityZoneResource(availabilityZoneFeClient);
    addResource(resource);
  }

  @Test
  public void testGetAvailabilityZoneById() throws Exception {
    AvailabilityZone availabilityZone = new AvailabilityZone();
    availabilityZone.setId(availabilityZoneId);
    availabilityZone.setKind("availabilityZone");
    when(availabilityZoneFeClient.get(availabilityZoneId)).thenReturn(availabilityZone);

    Response response = client().target(availabilityZoneRoutePath).request().get();
    assertThat(response.getStatus(), is(200));

    AvailabilityZone responseAvailabilityZone = response.readEntity(AvailabilityZone.class);
    assertThat(responseAvailabilityZone, is(availabilityZone));
    assertThat(new URI(responseAvailabilityZone.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseAvailabilityZone.getSelfLink().endsWith(availabilityZoneRoutePath), is(true));
  }

  @Test
  public void testGetAvailabilityZoneByInvalidId() throws Exception {
    when(availabilityZoneFeClient.get(availabilityZoneId))
        .thenThrow(new AvailabilityZoneNotFoundException(availabilityZoneId));

    Response response = client().target(availabilityZoneRoutePath).request().get();
    assertThat(response.getStatus(), is(404));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("AvailabilityZoneNotFound"));
    assertThat(errors.getMessage(), containsString("AvailabilityZone " + availabilityZoneId + " not found"));
  }

  @Test
  public void testDeleteAvailabilityZone() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(availabilityZoneFeClient.delete(availabilityZoneId)).thenReturn(task);

    Response response = client().target(availabilityZoneRoutePath).request().delete();

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testDeleteAvailabilityZoneByInvalidId() throws Exception {
    when(availabilityZoneFeClient.delete(availabilityZoneId))
        .thenThrow(new AvailabilityZoneNotFoundException(availabilityZoneId));

    Response response = client().target(availabilityZoneRoutePath).request().delete();
    assertThat(response.getStatus(), is(404));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("AvailabilityZoneNotFound"));
    assertThat(errors.getMessage(), containsString("AvailabilityZone " + availabilityZoneId + " not found"));
  }
}
