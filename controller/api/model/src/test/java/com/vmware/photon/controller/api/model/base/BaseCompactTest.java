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
import static org.testng.Assert.assertTrue;

import java.util.List;

/**
 * Tests {@link BaseCompact}.
 */
public class BaseCompactTest {

  private final Validator validator = new Validator();

  @Test
  public void testValidObject() throws Exception {
    BaseCompact baseCompact = BaseCompact.create("id-1", "test-name");
    assertTrue(validator.validate(baseCompact).isEmpty());
  }

  @Test
  public void testNullName() throws Exception {
    BaseCompact baseCompact = BaseCompact.create("id-1", null);
    ImmutableList<String> violations = validator.validate(baseCompact);

    assertThat(violations.size(), is(1));
    assertThat(violations.get(0), containsString("may not be null"));
  }

  @Test
  public void testLongName() throws Exception {
    BaseCompact baseCompact = BaseCompact.create("id-1", new String(new char[64]).replace('\0', 'a'));

    ImmutableList<String> violations = validator.validate(baseCompact);

    assertThat(violations.size(), is(1));
    assertThat(violations.get(0), containsString("size must be between 1 and 63"));
  }

  @Test
  public void testBadNames() throws Exception {
    List<String> badNames = ImmutableList.of("9to5", "^aaa", "test&me", "name with spaces", "  no-trim  ");

    for (String badName : badNames) {
      BaseCompact baseCompact = BaseCompact.create("id-1", badName);

      ImmutableList<String> violations = validator.validate(baseCompact);

      assertThat(violations.size(), is(1));
      assertThat(violations.get(0), containsString("must match \"^[a-zA-Z][a-zA-Z0-9-]*\""));
    }
  }

  @Test
  public void testSetNameDoesNotTrimSpaces() throws Exception {
    BaseCompact baseCompact = BaseCompact.create("id", "name");
    baseCompact.setName(" sloppy name ");
    assertThat(baseCompact.getName(), is(" sloppy name "));
  }
}
