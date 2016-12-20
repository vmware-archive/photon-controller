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

package com.vmware.photon.controller.api.frontend.lib;

import com.vmware.photon.controller.api.model.UsageTag;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests {@link UsageTagHelper}.
 */
public class UsageTagHelperTest {

  private static final String usageTagsSortedString = "[\"MGMT\",\"CLOUD\"]";
  private List<UsageTag> usageTagsSortedList;
  private Set<String> usageTagsSet;

  @BeforeMethod
  public void setup() {
    usageTagsSortedList = new ArrayList<>();
    usageTagsSortedList.add(UsageTag.MGMT);
    usageTagsSortedList.add(UsageTag.CLOUD);

    usageTagsSet = new HashSet<>();
    usageTagsSet.add(UsageTag.MGMT.name());
    usageTagsSet.add(UsageTag.CLOUD.name());
  }


  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for method {@link UsageTagHelper#serialize(List)}}.
   */
  public class UsageTagHelperSerializeTest {

    @BeforeMethod
    private void setUp() {
      setup();
    }

    @Test
    public void testEmptyListSerialize() {
      String usageTags = UsageTagHelper.serialize(new ArrayList<UsageTag>());
      assertThat(usageTags, is("[]"));
    }

    @Test
    public void testSerializeSuccess() {
      String usageTags = UsageTagHelper.serialize(usageTagsSortedList);
      assertThat(usageTags, is(usageTagsSortedString));
    }

    @Test
    public void testSerializeOrder() {
      String usageTags1 = UsageTagHelper.serialize(usageTagsSortedList);
      List<UsageTag> usageTagsUnsortedList = new ArrayList<UsageTag>();
      usageTagsUnsortedList.add(UsageTag.CLOUD);
      usageTagsUnsortedList.add(UsageTag.MGMT);

      String usageTags2 = UsageTagHelper.serialize(usageTagsUnsortedList);

      assertThat(usageTags1, is(usageTags2));
      assertThat(usageTagsUnsortedList.get(0), is(UsageTag.CLOUD));
      assertThat(usageTagsUnsortedList.get(1), is(UsageTag.MGMT));
    }

  }

  /**
   * This class implements tests for method
   * {@link UsageTagHelper#deserialize(String)}}.
   */
  public class UsageTagHelperDeserializeToStringSetTest {

    @BeforeMethod
    private void setUp() {
      setup();
    }

    @Test
    public void testNullStringDeserialize() {
      try {
        UsageTagHelper.deserialize(null);
        fail("Should have been failed with exception for null string");
      } catch (IllegalArgumentException e) {
      }
    }

    @Test
    public void testEmptyStringDeserialize() {
      List<UsageTag> usageTags = UsageTagHelper.deserialize("[]");
      List<UsageTag> emptyList = new ArrayList<>();
      assertThat(usageTags, is(emptyList));
    }

    @Test
    public void testDeserializeSuccess() {
      List<UsageTag> usageTags = UsageTagHelper.deserialize(usageTagsSortedString);
      assertEquals(usageTags, usageTagsSortedList);
    }

    @Test
    public void testDeserializeOrder() {
      String usageTagsUnsortedString = "[\"CLOUD\",\"MGMT\"]";
      List<UsageTag> usageTags = UsageTagHelper.deserialize(usageTagsUnsortedString);
      assertEquals(usageTags, usageTagsSortedList);
    }
  }

  /**
   * This class implements tests for method
   * {@link UsageTagHelper#deserialize(String)}}.
   */
  public class UsageTagHelperDeserializeTest {

    @BeforeMethod
    private void setUp() {
      setup();
    }

    @Test
    public void testNullStringDeserialize() {
      try {
        UsageTagHelper.deserializeToStringSet(null);
        fail("Should have been failed with exception for null string");
      } catch (IllegalArgumentException e) {
      }
    }

    @Test
    public void testEmptyStringDeserializeToString() {
      Set<String> usageTags = UsageTagHelper.deserializeToStringSet("[]");
      Set<String> emptySet = new HashSet<>();
      assertThat(usageTags, is(emptySet));
    }

    @Test
    public void testDeserializeToStringSuccess() {
      Set<String> usageTags = UsageTagHelper.deserializeToStringSet(usageTagsSortedString);
      assertEquals(usageTags, usageTagsSet);
    }

  }
}
