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

package com.vmware.photon.controller.api.frontend.utils;

import com.vmware.photon.controller.api.frontend.entities.SecurityGroupEntity;
import com.vmware.photon.controller.api.model.SecurityGroup;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests {@link SecurityGroupUtils}.
 */
public class SecurityGroupUtilsTest {
  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for method mergeSelfSecurityGroups.
   */
  public static class MergeSelfSecurityGroupsTest {

    @Test
    public void testNullArguments() {
      try {
        SecurityGroupUtils.mergeSelfSecurityGroups(null, new ArrayList<>());
        fail("Should have failed for null argument");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Provided value for existingSecurityGroups is unacceptably null"));
      }

      try {
        SecurityGroupUtils.mergeSelfSecurityGroups(new ArrayList<>(), null);
        fail("should have failed for null argument");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Provided value for selfSecurityGroups is unacceptably null"));
      }
    }

    @Test
    public void testNoExistingSecurityGroups() {
      List<String> selfSecurityGroups = Arrays.asList(new String[]{"adminGroup1", "adminGroup2"});
      List<SecurityGroup> expectedMergedSecurityGroups = selfSecurityGroups.stream()
          .map(g -> new SecurityGroup(g, false))
          .collect(Collectors.toList());

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeSelfSecurityGroups(
          new ArrayList<>(),
          selfSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(result.getRight().size(), is(0));
    }

    @Test
    public void testNoExistingSelfSecurityGroupsNoDuplicates() {
      List<SecurityGroup> existingSecurityGroups = Arrays.asList(
          new SecurityGroup[]{new SecurityGroup("adminGroup1", true)});
      List<String> selfSecurityGroups = Arrays.asList(new String[]{"adminGroup2"});

      List<SecurityGroup> expectedMergedSecurityGroups = new ArrayList<>();
      expectedMergedSecurityGroups.addAll(existingSecurityGroups);
      expectedMergedSecurityGroups.addAll(selfSecurityGroups.stream()
              .map(g -> new SecurityGroup(g, false))
              .collect(Collectors.toList())
      );

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeSelfSecurityGroups(
          existingSecurityGroups,
          selfSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(result.getRight().size(), is(0));
    }

    @Test
    public void testExistingSelfSecurityGroupsNoDuplicates() {
      List<SecurityGroup> existingSecurityGroups = new ArrayList<>();
      existingSecurityGroups.add(new SecurityGroup("adminGroup1", true));
      existingSecurityGroups.add(new SecurityGroup("adminGroup2", false));

      List<String> selfSecurityGroups = Arrays.asList(new String[]{"adminGroup3"});

      List<SecurityGroup> expectedMergedSecurityGroups = new ArrayList<>();
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup1", true));
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup3", false));

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeSelfSecurityGroups(
          existingSecurityGroups,
          selfSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(result.getRight().size(), is(0));
    }

    @Test
    public void testInheritedSelfDuplicates() {
      List<SecurityGroup> existingSecurityGroups = new ArrayList<>();
      existingSecurityGroups.add(new SecurityGroup("adminGroup1", true));
      existingSecurityGroups.add(new SecurityGroup("adminGroup2", false));

      List<String> selfSecurityGroups = Arrays.asList(new String[]{"adminGroup1", "adminGroup3"});

      List<SecurityGroup> expectedMergedSecurityGroups = new ArrayList<>();
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup1", true));
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup3", false));

      List<String> expectedSelfSecurityGroupsLeftOut = Arrays.asList(new String[]{"adminGroup1"});

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeSelfSecurityGroups(
          existingSecurityGroups,
          selfSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(ListUtils.isEqualList(result.getRight(), expectedSelfSecurityGroupsLeftOut), is(true));
    }
  }

  /**
   * Tests for method mergeParentSecurityGroups.
   */
  public static class MergeParentSecurityGroupsTest {

    @Test
    public void testNullArguments() {
      try {
        SecurityGroupUtils.mergeParentSecurityGroups(null, new ArrayList<>());
        fail("Should have failed for null argument");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Provided value for existingSecurityGroups is unacceptably null"));
      }

      try {
        SecurityGroupUtils.mergeParentSecurityGroups(new ArrayList<>(), null);
        fail("Should have failed for null argument");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Provided value for parentSecurityGroups is unacceptably null"));
      }
    }

    @Test
    public void testNoExistingSecurityGroups() {
      List<String> parentSecurityGroups = Arrays.asList(new String[]{"adminGroup1", "adminGroup2"});
      List<SecurityGroup> expectedMergedSecurityGroups = parentSecurityGroups.stream()
          .map(g -> new SecurityGroup(g, true))
          .collect(Collectors.toList());

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeParentSecurityGroups(
          new ArrayList<>(),
          parentSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(result.getRight().size(), is(0));
    }


    @Test
    public void testNoExistingParentSecurityGroupsNoDuplicates() {
      List<SecurityGroup> existingSecurityGroups = Arrays.asList(
          new SecurityGroup[]{new SecurityGroup("adminGroup1", false)}
      );
      List<String> parentSecurityGroups = Arrays.asList(new String[]{"adminGroup2"});

      List<SecurityGroup> expectedMergedSecurityGroups = new ArrayList<>();
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup2", true));
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup1", false));

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeParentSecurityGroups(
          existingSecurityGroups,
          parentSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(result.getRight().size(), is(0));
    }

    @Test
    public void testExistingParentSecurityGroupsNoDuplicates() {
      List<SecurityGroup> existingSecurityGroups = new ArrayList<>();
      existingSecurityGroups.add(new SecurityGroup("adminGroup1", true));
      existingSecurityGroups.add(new SecurityGroup("adminGroup2", false));

      List<String> parentSecurityGroups = Arrays.asList(new String[]{"adminGroup3"});

      List<SecurityGroup> expectedMergedSecurityGroups = new ArrayList<>();
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup3", true));
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup2", false));

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeParentSecurityGroups(
          existingSecurityGroups,
          parentSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(result.getRight().size(), is(0));
    }

    @Test
    public void testInheritedSelfDuplicates() {
      List<SecurityGroup> existingSecurityGroups = new ArrayList<>();
      existingSecurityGroups.add(new SecurityGroup("adminGroup1", true));
      existingSecurityGroups.add(new SecurityGroup("adminGroup2", false));
      existingSecurityGroups.add(new SecurityGroup("adminGroup3", false));

      List<String> parentSecurityGroups = Arrays.asList(new String[]{"adminGroup2", "adminGroup4"});

      List<SecurityGroup> expectedMergedSecurityGroups = new ArrayList<>();
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup2", true));
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup4", true));
      expectedMergedSecurityGroups.add(new SecurityGroup("adminGroup3", false));

      Pair<List<SecurityGroup>, List<String>> result = SecurityGroupUtils.mergeParentSecurityGroups(
          existingSecurityGroups,
          parentSecurityGroups
      );
      assertThat(ListUtils.isEqualList(result.getLeft(), expectedMergedSecurityGroups), is(true));
      assertThat(result.getRight().size(), is(1));
      assertThat(result.getRight().get(0), is("adminGroup2"));
    }
  }

  /**
   * Tests for methods that tranform security groups to/from internal representation.
   */
  public static class SecurityGroupsTransformTest {

    @Test
    public static void testToApiRepresentation() {
      SecurityGroupEntity entity = new SecurityGroupEntity("sg1", true);

      SecurityGroup securityGroup = SecurityGroupUtils.toApiRepresentation(entity);
      assertThat(securityGroup.getName(), is("sg1"));
      assertThat(securityGroup.isInherited(), is(true));
    }

    @Test
    public static void testListToApiRepresentation() {
      List<SecurityGroupEntity> entities = new ArrayList<>();
      entities.add(new SecurityGroupEntity("sg1", true));
      entities.add(new SecurityGroupEntity("sg2", false));

      List<SecurityGroup> securityGroups = SecurityGroupUtils.toApiRepresentation(entities);
      assertThat(securityGroups.size(), is(2));
      assertThat(securityGroups.get(0), is(new SecurityGroup("sg1", true)));
      assertThat(securityGroups.get(1), is(new SecurityGroup("sg2", false)));
    }

    @Test
    public static void testFromApiRepresentation() {
      SecurityGroup securityGroup = new SecurityGroup("sg1", true);

      SecurityGroupEntity entity = SecurityGroupUtils.fromApiRepresentation(securityGroup);
      assertThat(entity.getName(), is("sg1"));
      assertThat(entity.isInherited(), is(true));
    }

    @Test
    public static void testListFromApiRepresentation() {
      List<SecurityGroup> securityGroups = new ArrayList<>();
      securityGroups.add(new SecurityGroup("sg1", true));
      securityGroups.add(new SecurityGroup("sg2", false));

      List<SecurityGroupEntity> entities = SecurityGroupUtils.fromApiRepresentation(securityGroups);
      assertThat(entities.size(), is(2));
      assertThat(entities.get(0), is(new SecurityGroupEntity("sg1", true)));
      assertThat(entities.get(1), is(new SecurityGroupEntity("sg2", false)));
    }
  }
}
