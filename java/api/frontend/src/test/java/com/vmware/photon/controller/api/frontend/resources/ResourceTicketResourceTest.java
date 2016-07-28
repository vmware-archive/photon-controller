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

import com.vmware.photon.controller.api.frontend.clients.ResourceTicketFeClient;
import com.vmware.photon.controller.api.frontend.resources.resourceticket.ResourceTicketResource;
import com.vmware.photon.controller.api.frontend.resources.routes.ResourceTicketResourceRoutes;
import com.vmware.photon.controller.api.model.ResourceTicket;

import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.resourceticket.ResourceTicketResource}.
 */
public class ResourceTicketResourceTest extends ResourceTest {

  private String ticketId = "rt1";

  private String resourceTicketRoutePath =
      UriBuilder.fromPath(ResourceTicketResourceRoutes.RESOURCE_TICKET_PATH).build(ticketId).toString();

  @Mock
  private ResourceTicketFeClient resourceTicketFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ResourceTicketResource(resourceTicketFeClient));
  }

  @Test
  public void testGetResourceTicketById() throws Exception {
    ResourceTicket ticket = new ResourceTicket();
    ticket.setId(ticketId);
    ticket.setName("rt1name");

    when(resourceTicketFeClient.get(ticketId)).thenReturn(ticket);

    Response response = client()
        .target(resourceTicketRoutePath)
        .request()
        .get();
    assertThat(response.getStatus(), is(200));

    ResourceTicket responseTicket = response.readEntity(ResourceTicket.class);
    assertThat(responseTicket, CoreMatchers.is(ticket));
    assertThat(new URI(responseTicket.getSelfLink()).isAbsolute(), CoreMatchers.is(true));
    assertThat(responseTicket.getSelfLink().endsWith(resourceTicketRoutePath), CoreMatchers.is(true));
  }
}
