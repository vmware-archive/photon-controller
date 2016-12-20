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

package com.vmware.photon.controller.api.model.base;

import com.vmware.photon.controller.api.model.helpers.Validator;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.testng.Assert.assertTrue;

import java.util.List;

/**
 * Tests {@link VisibleModel}.
 */
public class VisibleModelTest {

  Validator validator = new Validator();

  @Test
  public void testIsABase() throws Exception {
    assertThat(new VisibleModelExample(), isA(Base.class));
  }

  @Test
  public void testValidObject() throws Exception {
    VisibleModelExample infra = new VisibleModelExample();
    infra.setName("foo");

    ImmutableList<String> violations = validator.validate(infra);
    assertTrue(violations.isEmpty());
  }

  @Test
  public void testNullName() throws Exception {
    VisibleModelExample infra = new VisibleModelExample();

    ImmutableList<String> violations = validator.validate(infra);

    assertThat(violations.size(), is(1));
    assertThat(violations.get(0), containsString("name may not be null"));
  }

  @Test
  public void testLongName() throws Exception {
    String longName = new String(new char[64]).replace('\0', 'a');
    VisibleModelExample infra = new VisibleModelExample();
    infra.setName(longName);

    ImmutableList<String> violations = validator.validate(infra);

    assertThat(violations.size(), is(1));
    assertThat(violations.get(0), containsString("name size must be between 1 and 63"));
  }

  @Test
  public void testBadNames() throws Exception {
    List<String> badNames = ImmutableList.of("9to5", "^aaa", "test&me", "name with spaces", "  no-trim  ");

    for (String badName : badNames) {
      VisibleModelExample infra = new VisibleModelExample();
      infra.setName(badName);

      ImmutableList<String> violations = validator.validate(infra);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), containsString("name must match \"^[a-zA-Z][a-zA-Z0-9-]*\""));
    }
  }

  @Test
  public void testDoNotTrimSpacesFromName() throws Exception {
    VisibleModelExample infra = new VisibleModelExample();
    infra.setName(" sloppy name ");
    assertThat(infra.getName(), is(" sloppy name "));
  }

  class VisibleModelExample extends VisibleModel {
    @Override
    public String getKind() {
      return "example";
    }
  }
}
