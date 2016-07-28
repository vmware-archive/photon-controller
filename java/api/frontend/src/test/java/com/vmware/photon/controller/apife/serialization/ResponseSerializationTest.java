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

package com.vmware.photon.controller.apife.serialization;

import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.apife.Responses;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import static com.vmware.photon.controller.apife.Responses.generateCustomResponse;
import static com.vmware.photon.controller.apife.Responses.generateResourceListResponse;
import static com.vmware.photon.controller.apife.exceptions.external.ErrorCode.INTERNAL_ERROR;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.apife.helpers.JsonHelpers.jsonFixture;

import org.glassfish.jersey.server.ContainerRequest;
import org.mockito.Mock;
import org.powermock.modules.testng.PowerMockTestCase;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import javax.ws.rs.core.Response;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests response serialization.
 */
public class ResponseSerializationTest extends PowerMockTestCase {

  private static final String responseWithoutSelfLinkFixtureFile =
      "fixtures/response-without-self-link.json";

  private static final String responseWithSelfLinkFixtureFile =
      "fixtures/response-with-self-link.json";

  private static final String responseWithResourceListFixtureFile =
      "fixtures/response-with-resource-list.json";

  @Mock
  private ContainerRequest request;

  @BeforeMethod
  public void setUp() throws URISyntaxException {
    when(request.getBaseUri()).thenReturn(new URI("http://localhost:9080/"));
    MDC.put(LoggingUtils.REQUEST_ID_KEY, "requestId");
  }

  @AfterMethod
  public void tearDown() {
    MDC.clear();
  }

  @Test
  public void serializeWithoutSelfLink() throws Exception {
    Response response = generateCustomResponse(Response.Status.CREATED, getConcreteBase());
    assertThat(asJson(response), is(sameJSONAs(jsonFixture(responseWithoutSelfLinkFixtureFile))));
  }

  @Test
  public void serializeWithSelfLink() throws Exception {
    Response response = generateCustomResponse(Response.Status.CREATED,
        getConcreteBase(), request, "/bases/{id}");
    assertThat(asJson(response), is(sameJSONAs(jsonFixture(responseWithSelfLinkFixtureFile))));
  }

  @Test
  public void serializeResourceListResponse() throws Exception {
    List<BaseSerializationTest.ConcreteBase> l = new ArrayList<>(2);
    l.add(getConcreteBase());
    l.add(getConcreteBase());

    Response response = generateResourceListResponse(
        Response.Status.CREATED,
        new ResourceList<>(l),
        request,
        "/bases/{id}");

    assertThat(asJson(response), is(sameJSONAs(jsonFixture(responseWithResourceListFixtureFile))));
  }

  @Test
  public void negativeResponseContainRequestId() {
    Response response = Responses.externalException(new ExternalException(INTERNAL_ERROR, "foo", null));
    assertThat(response.getMetadata().getFirst("REQUEST-ID").toString(), is("requestId"));
  }

  private BaseSerializationTest.ConcreteBase getConcreteBase() {
    BaseSerializationTest.ConcreteBase base = new BaseSerializationTest.ConcreteBase();
    base.setId("base-id");

    return base;
  }

}
