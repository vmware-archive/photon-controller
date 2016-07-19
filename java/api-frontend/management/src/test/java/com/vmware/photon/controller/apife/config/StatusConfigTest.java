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

package com.vmware.photon.controller.apife.config;

import com.vmware.photon.controller.api.model.Component;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tests {@link StatusConfig}.
 */
public class StatusConfigTest {

  StatusConfig config;

  @BeforeMethod
  public void setup() {
    config = new StatusConfig();
  }

  @Test
  public void testEmptyConfig() {
    config.setComponents(ImmutableList.<String>of());
    Set<Component> expected = EnumSet.allOf(Component.class);

    assertThat(config.getComponents(), is(expected));
  }

  @Test
  public void testNullConfig() {
    config.setComponents(null);
    Set<Component> expected = EnumSet.allOf(Component.class);

    assertThat(config.getComponents(), is(expected));
  }

  @Test
  public void testAllConfig() {
    config.setComponents(ImmutableList.of(
        "photon-controller"));
    Set<Component> expected = EnumSet.allOf(Component.class);

    assertThat(config.getComponents(), is(expected));
  }

  @Test
  public void testPartialonfig() {
    config.setComponents(ImmutableList.of("photon-controller"));
    Set<Component> expected = EnumSet.of(Component.PHOTON_CONTROLLER);

    assertThat(config.getComponents(), is(expected));
  }

  @Test (expectedExceptions = IllegalArgumentException.class)
  public void testBadConfig() {
    config.setComponents(ImmutableList.of("housekeeper", "invalid"));
  }
}
