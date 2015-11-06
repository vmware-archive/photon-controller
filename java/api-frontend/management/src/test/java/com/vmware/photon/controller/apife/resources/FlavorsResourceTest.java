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

import com.vmware.photon.controller.api.Flavor;
import com.vmware.photon.controller.api.FlavorCreateSpec;
import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.Task;
import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.clients.FlavorFeClient;
import com.vmware.photon.controller.apife.resources.routes.FlavorsResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.List;

/**
 * Tests {@link FlavorsResource}.
 */
public class FlavorsResourceTest extends ResourceTest {

  @Mock
  private FlavorFeClient flavorFeClient;

  private FlavorCreateSpec spec;

  private String taskId = "task1";

  private String taskRoutePath =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId).toString();

  @Override
  protected void setUpResources() throws Exception {
    spec = new FlavorCreateSpec();
    spec.setName("test-100");
    spec.setKind("vm");
    spec.setCost(ImmutableList.of(new QuotaLineItem("foo.bar", 2.0, QuotaUnit.COUNT)));

    addResource(new FlavorsResource(flavorFeClient));
  }

  @Test
  public void testSuccessfulCreateFlavor() throws Exception {
    Task task = new Task();
    task.setId(taskId);
    when(flavorFeClient.create(spec)).thenReturn(task);

    Response response = createFlavor(spec);

    assertThat(response.getStatus(), is(201));
    Task responseTask = response.readEntity(Task.class);
    assertThat(responseTask, is(task));
    assertThat(new URI(responseTask.getSelfLink()).isAbsolute(), is(true));
    assertThat(responseTask.getSelfLink().endsWith(taskRoutePath), is(true));
  }

  @Test
  public void testFailedCreateFlavor() throws Exception {
    when(flavorFeClient.create(spec)).thenThrow(new ExternalException("failed"));
    assertThat(createFlavor(spec).getStatus(), is(500));
  }

  @Test
  public void testFailedCreateFlavorWithInvalidKind() throws Exception {
    FlavorCreateSpec wrongKindSpec = new FlavorCreateSpec();
    wrongKindSpec.setName("test-100");
    wrongKindSpec.setKind("disk");
    wrongKindSpec.setCost(ImmutableList.of(new QuotaLineItem("foo.bar", 2.0, QuotaUnit.COUNT)));

    assertThat(createFlavor(wrongKindSpec).getStatus(), is(400));
  }

  @Test
  public void testFilterFlavors() throws Exception {
    Flavor f1 = new Flavor();
    f1.setId("f1");
    f1.setName("f1");
    f1.setKind("k1");

    Flavor f2 = new Flavor();
    f2.setId("f2");
    f2.setName("f2");
    f2.setKind("k2");

    Optional<String> name = Optional.fromNullable(null);
    Optional<String> kind = Optional.fromNullable(null);
    when(flavorFeClient.list(name, kind)).thenReturn(new ResourceList<>(ImmutableList.of(f1, f2)));
    Response response = getFlavors();
    assertThat(response.getStatus(), is(200));

    List<Flavor> flavors = response.readEntity(
        new GenericType<ResourceList<Flavor>>() {
        }
    ).getItems();

    assertThat(flavors.size(), is(2));
    assertThat(flavors.get(0), is(f1));
    assertThat(f1.getSelfLink().endsWith(
        UriBuilder.fromPath(FlavorsResourceRoutes.FLAVOR_PATH).build("f1").toString()), is(true));
    assertThat(flavors.get(1), is(f2));
    assertThat(f2.getSelfLink().endsWith(
        UriBuilder.fromPath(FlavorsResourceRoutes.FLAVOR_PATH).build("f2").toString()), is(true));

    for (Flavor t : flavors) {
      assertThat(new URI(t.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    }
  }

  private Response createFlavor(FlavorCreateSpec spec) {
    return client()
        .target(FlavorsResourceRoutes.API)
        .request()
        .post(Entity.entity(spec, MediaType.APPLICATION_JSON_TYPE));
  }

  private Response getFlavors() {
    WebTarget resource = client().target(FlavorsResourceRoutes.API);
    return resource.request().get();
  }
}
