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

package com.vmware.photon.controller.swagger.resources;

import com.vmware.photon.controller.swagger.api.SwaggerResourceListing;

import static com.vmware.photon.controller.swagger.resources.helpers.JsonHelpers.asJson;
import static com.vmware.photon.controller.swagger.resources.helpers.JsonHelpers.jsonFixture;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.co.datumedge.hamcrest.json.SameJSONAs.sameJSONAs;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link SwaggerJsonListing} class.
 */
public class SwaggerJsonListingTest {

  private void assertResourcesJsonIsEqualTo(List<Class<?>> resources, String jsonFile,
                                            String resourceListingPath) throws Exception {
    SwaggerJsonListing swaggerJsonListing = new SwaggerJsonListing(resources, "1.2", "v1");
    SwaggerResourceListing output = swaggerJsonListing.getSwaggerResourceListing(resourceListingPath);

    assertThat("test serializes ok", asJson(output), sameJSONAs(jsonFixture(jsonFile)).allowingAnyArrayOrdering());
    SwaggerJsonListing.clearCaches();
  }

  @Test
  public void testTestResource() throws Exception {
    List<Class<?>> resources = new ArrayList<>();
    resources.add(TestResource.class);
    assertResourcesJsonIsEqualTo(resources, "fixtures/testresource.json", "/test");
  }

  @Test
  public void testMultiplePaths() throws Exception {
    List<Class<?>> resources = new ArrayList<>();
    resources.add(TestMultiplePaths.class);
    assertResourcesJsonIsEqualTo(resources, "fixtures/testmultiplepaths.json", "/test");
  }

  @Test
  public void testMultipleOpsInPath() throws Exception {
    List<Class<?>> resources = new ArrayList<>();
    resources.add(TestMultipleOpsInPath.class);
    assertResourcesJsonIsEqualTo(resources, "fixtures/testmultipleopsinpath.json", "/test");
  }
}
