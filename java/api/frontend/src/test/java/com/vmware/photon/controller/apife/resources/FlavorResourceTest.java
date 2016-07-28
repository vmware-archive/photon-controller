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

import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.Flavor;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.FlavorFeClient;
import com.vmware.photon.controller.apife.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.apife.resources.flavor.FlavorResource;
import com.vmware.photon.controller.apife.resources.routes.FlavorsResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import org.hamcrest.CoreMatchers;
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
 * Tests {@link com.vmware.photon.controller.apife.resources.flavor.FlavorResource}.
 */
public class FlavorResourceTest extends ResourceTest {

  private String flavorId = "f1";

  private String flavorRoutePath =
      UriBuilder.fromPath(FlavorsResourceRoutes.FLAVOR_PATH).build(flavorId).toString();

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Mock
  private FlavorFeClient flavorFeClient;

  @Override
  protected void setUpResources() throws Exception {
    FlavorResource resource = new FlavorResource(flavorFeClient);
    addResource(resource);
  }

  @Test
  public void testGetFlavorById() throws Exception {
    Flavor flavor = new Flavor();
    flavor.setId(flavorId);
    flavor.setKind("persistent-disk");
    when(flavorFeClient.get(flavorId)).thenReturn(flavor);

    Response response = client().target(flavorRoutePath).request().get();
    assertThat(response.getStatus(), is(200));

    Flavor responseFlavor = response.readEntity(Flavor.class);
    assertThat(responseFlavor, CoreMatchers.is(flavor));
    assertThat(new URI(responseFlavor.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseFlavor.getSelfLink().endsWith(flavorRoutePath), is(true));
  }

  @Test
  public void testGetFlavorByInvalidId() throws Exception {
    when(flavorFeClient.get(flavorId)).thenThrow(new FlavorNotFoundException(flavorId));

    Response response = client().target(flavorRoutePath).request().get();
    assertThat(response.getStatus(), is(404));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("FlavorNotFound"));
    assertThat(errors.getMessage(), containsString("Flavor " + flavorId + " not found"));
  }

  @Test
  public void testDeleteFlavor() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(flavorFeClient.delete(flavorId)).thenReturn(task);

    Response response = client().target(flavorRoutePath).request().delete();

    assertThat(response.getStatus(), is(Response.Status.CREATED.getStatusCode()));

    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), CoreMatchers.is(true));
  }

  @Test
  public void testDeleteFlavorByInvalidId() throws Exception {
    when(flavorFeClient.delete(flavorId)).thenThrow(new FlavorNotFoundException(flavorId));

    Response response = client().target(flavorRoutePath).request().delete();
    assertThat(response.getStatus(), is(404));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), equalTo("FlavorNotFound"));
    assertThat(errors.getMessage(), containsString("Flavor " + flavorId + " not found"));
  }
}
