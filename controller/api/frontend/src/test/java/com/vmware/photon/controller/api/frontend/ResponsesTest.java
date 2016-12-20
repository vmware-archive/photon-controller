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

package com.vmware.photon.controller.api.frontend;


import com.vmware.photon.controller.api.frontend.serialization.BaseSerializationTest;
import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

import org.glassfish.jersey.server.ContainerRequest;
import org.testng.annotations.Test;
import static org.powermock.api.mockito.PowerMockito.mock;

import javax.ws.rs.core.Response;


/**
 * Tests {@link Responses}.
 */
public class ResponsesTest {

  @Test(expectedExceptions = NullPointerException.class)
  public void generateCustomResponseWithSelfLinkMissingEntity() {
    generateCustomResponse(Response.Status.OK,
        null,
        mock(ContainerRequest.class),
        "linkTemplate");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void generateCustomResponseWithSelfLinkMissingRequest() {
    generateCustomResponse(Response.Status.OK,
        new BaseSerializationTest.ConcreteBase(),
        null,
        "linkTemplate");
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void generateCustomResponseWithSelfLinkMissingLinkTemplate() {
    generateCustomResponse(Response.Status.OK,
        new BaseSerializationTest.ConcreteBase(),
        mock(ContainerRequest.class),
        null);
  }
}
