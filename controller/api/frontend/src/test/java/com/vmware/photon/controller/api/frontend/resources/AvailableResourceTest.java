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

import com.vmware.photon.controller.api.frontend.resources.routes.AvailableRoutes;
import com.vmware.photon.controller.api.frontend.resources.status.AvailableResource;
import com.vmware.photon.controller.api.model.Available;

import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import javax.ws.rs.core.UriBuilder;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.auth.AuthResource}.
 */
public class AvailableResourceTest extends ResourceTest {

  private String availableRoute = UriBuilder.fromPath(AvailableRoutes.API).build().toString();

  @Override
  protected void setUpResources() throws Exception {
    addResource(new AvailableResource());
  }

  /**
   * Contains tests for HTTP GET Method on availableResource.
   * Note that GET is the only supported operation
   */
  @Test
  public void testGetAvailableInfo() throws Exception {
    Available available = client().target(availableRoute).request().get(Available.class);

    assertThat(available, notNullValue());
  }
}
