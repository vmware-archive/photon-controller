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

package com.vmware.photon.controller.api;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;
import org.testng.collections.Sets;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.EnumSet;

/**
 * Tests {@link Component}.
 */
public class ComponentTest {
  @Test
  public void testFromString() {
    assertThat(Component.fromString("photon-controller"), is(Component.PHOTON_CONTROLLER));
  }

  @Test
  public void testFromStrings() {
    assertThat(Component.fromStrings(ImmutableList.of("photon-controller")), is
        (Sets.newHashSet(EnumSet.of(Component.PHOTON_CONTROLLER))));
  }

  @Test (expectedExceptions = IllegalArgumentException.class)
  public void testSingleInvalid() {
    Component.fromString("invalid");
  }

  @Test (expectedExceptions = IllegalArgumentException.class)
  public void testContainsInvalid() {
    Component.fromStrings(ImmutableList.of("photon-controller", "invalid"));
  }
}
