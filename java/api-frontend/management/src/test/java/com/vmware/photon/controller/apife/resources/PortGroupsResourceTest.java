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

import com.vmware.photon.controller.api.PortGroup;
import com.vmware.photon.controller.api.ResourceList;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.apife.clients.PortGroupFeClient;
import com.vmware.photon.controller.apife.resources.routes.PortGroupResourceRoutes;

import com.google.common.base.Optional;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doReturn;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests {@link PortGroupsResource}.
 */
public class PortGroupsResourceTest extends ResourceTest {
  private static final Logger logger = LoggerFactory.getLogger(PortGroupsResourceTest.class);

  private String portGroupId = "network1";
  private String portGroupRoutePath =
      UriBuilder.fromPath(PortGroupResourceRoutes.PORT_GROUP_PATH).build(portGroupId).toString();

  @Mock
  private PortGroupFeClient portGroupFeClient;

  @Override
  protected void setUpResources() throws Exception {
    addResource(new PortGroupsResource(portGroupFeClient));
  }

  @Test
  public void testGetEmptyPortGroupList() {
    ResourceList<PortGroup> resourceList = new ResourceList<>(new ArrayList<PortGroup>());
    doReturn(resourceList).when(portGroupFeClient).find(Optional.<String>absent(), Optional.<UsageTag>absent());

    Response clientResponse = client()
        .target(PortGroupResourceRoutes.API)
        .request("application/json")
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<PortGroup> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<PortGroup>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(0));
  }

  @Test
  public void testGetPortGroupList() throws URISyntaxException {
    PortGroup portGroup = new PortGroup();
    portGroup.setId(portGroupId);
    portGroup.setName("VMNetwork");
    portGroup.setUsageTags(Arrays.asList(UsageTag.CLOUD));

    List<PortGroup> portGroupList = new ArrayList<>();
    portGroupList.add(portGroup);

    ResourceList<PortGroup> resourceList = new ResourceList<>(portGroupList);
    doReturn(resourceList).when(portGroupFeClient).find(Optional.of("P1"), Optional.of(UsageTag.CLOUD));

    Response clientResponse = client()
        .target(PortGroupResourceRoutes.API + "?name=P1&usageTag=CLOUD")
        .request(MediaType.APPLICATION_JSON)
        .get();

    assertThat(clientResponse.getStatus(), is(200));

    ResourceList<PortGroup> retrievedResources = clientResponse.readEntity(new GenericType<ResourceList<PortGroup>>() {
    });
    assertThat(retrievedResources.getItems().size(), is(1));

    PortGroup retrievedPortGroup = retrievedResources.getItems().get(0);
    assertThat(retrievedPortGroup, is(portGroup));

    assertThat(new URI(retrievedPortGroup.getSelfLink()).isAbsolute(), is(true));
    assertThat(retrievedPortGroup.getSelfLink().endsWith(portGroupRoutePath), is(true));
  }

}
