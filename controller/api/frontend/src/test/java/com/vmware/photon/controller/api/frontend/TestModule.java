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

import com.vmware.photon.controller.api.frontend.backends.FlavorLoader;
import com.vmware.photon.controller.api.frontend.exceptions.external.FlavorNotFoundException;
import com.vmware.photon.controller.api.model.FlavorCreateSpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Map;

/**
 * Provides common test dependencies.
 */
public class TestModule extends AbstractModule {
  @Override
  protected void configure() {
  }

  @Provides
  @Singleton
  public FlavorLoader getFlavorLoader() throws IOException, FlavorNotFoundException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    FlavorCreateSpec[] vmFlavors = mapper.readValue(
        TestModule.class.getResourceAsStream("/kinds/vm.yml"),
        FlavorCreateSpec[].class
    );

    FlavorCreateSpec[] persistentDiskFlavors = mapper.readValue(
        TestModule.class.getResourceAsStream("/kinds/persistent-disk.yml"),
        FlavorCreateSpec[].class
    );

    FlavorCreateSpec[] ephemeralDiskFlavors = mapper.readValue(
        Data.class.getResourceAsStream("/kinds/ephemeral-disk.yml"),
        FlavorCreateSpec[].class
    );

    ImmutableMap.Builder<String, Map<String, FlavorCreateSpec>> builder = ImmutableMap.builder();

    for (FlavorCreateSpec[] flavorList : ImmutableList.of(vmFlavors, persistentDiskFlavors, ephemeralDiskFlavors)) {
      ImmutableMap.Builder<String, FlavorCreateSpec> flavorMapBuilder = ImmutableMap.builder();
      String kind = null;
      for (FlavorCreateSpec flavor : flavorList) {
        flavorMapBuilder.put(flavor.getName(), flavor);
        kind = flavor.getKind();
      }
      builder.put(kind, flavorMapBuilder.build());
    }

    return new FlavorLoader(builder.build());
  }
}
