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

package com.vmware.photon.controller.apife.utils;

import com.vmware.photon.controller.api.model.SecurityGroup;
import com.vmware.photon.controller.apife.entities.SecurityGroupEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidSecurityGroupFormatException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Util classes for Security Group.
 */
public class SecurityGroupUtils {
  /**
   * Merge the 'self' security groups to existing security groups.
   *
   * @param existingSecurityGroups Existing security groups including both inherited and self ones.
   * @param selfSecurityGroups     'self' security groups to be merged.
   * @return The merging result and security groups not being merged.
   */
  public static Pair<List<SecurityGroup>, List<String>> mergeSelfSecurityGroups(
      List<SecurityGroup> existingSecurityGroups,
      List<String> selfSecurityGroups) {

    checkNotNull(existingSecurityGroups, "Provided value for existingSecurityGroups is unacceptably null");
    checkNotNull(selfSecurityGroups, "Provided value for selfSecurityGroups is unacceptably null");

    List<SecurityGroup> mergedSecurityGroups = new ArrayList<>();
    List<String> securityGroupsNotMerged = new ArrayList<>();
    Set<String> inheritedSecurityGroupNames = new HashSet<>();

    existingSecurityGroups.stream()
        .filter(g -> g.isInherited())
        .forEach(g -> {
          mergedSecurityGroups.add(g);
          inheritedSecurityGroupNames.add(g.getName());
        });

    selfSecurityGroups.forEach(g -> {
      if (!inheritedSecurityGroupNames.contains(g)) {
        mergedSecurityGroups.add(new SecurityGroup(g, false));
      } else {
        securityGroupsNotMerged.add(g);
      }
    });

    return Pair.of(mergedSecurityGroups, securityGroupsNotMerged);
  }

  /**
   * Merge the security inherited from parent to the current security groups.
   *
   * @param existingSecurityGroups Existing security groups including both inherited and self ones.
   * @param parentSecurityGroups   Security groups inherited from parent.
   * @return The merged security groups and the ones removed from 'self' ones due to duplication with parent.
   */
  public static Pair<List<SecurityGroup>, List<String>> mergeParentSecurityGroups(
      List<SecurityGroup> existingSecurityGroups,
      List<String> parentSecurityGroups) {

    checkNotNull(existingSecurityGroups, "Provided value for existingSecurityGroups is unacceptably null");
    checkNotNull(parentSecurityGroups, "Provided value for parentSecurityGroups is unacceptably null");

    List<SecurityGroup> mergedSecurityGroups = new ArrayList<>();
    List<String> selfSecurityGroupsRemoved = new ArrayList<>();
    Set<String> inheritedSecurityGroupsNames = new HashSet<>();

    parentSecurityGroups.forEach(g -> {
      mergedSecurityGroups.add(new SecurityGroup(g, true));
      inheritedSecurityGroupsNames.add(g);
    });

    existingSecurityGroups.stream()
        .filter(g -> !g.isInherited())
        .forEach(g -> {
          if (inheritedSecurityGroupsNames.contains(g.getName())) {
            selfSecurityGroupsRemoved.add(g.getName());
          } else {
            mergedSecurityGroups.add(g);
          }
        });

    return Pair.of(mergedSecurityGroups, selfSecurityGroupsRemoved);
  }

  /**
   * Validate the security groups' format.
   *
   * @param securityGroups   The list of security groups.
   */
  public static void validateSecurityGroupsFormat(List<String> securityGroups) throws
      InvalidSecurityGroupFormatException {
    if (securityGroups != null) {
      for (String sg : securityGroups) {
        // match domain\group" format
        if (StringUtils.isBlank(sg) || sg.split("\\\\").length != 2) {
          throw new InvalidSecurityGroupFormatException("The security group format should match domain\\group");
        }
      }
    }
  }

  /**
   * Transform a security group from internal entity to api representation.
   *
   * @param entity Security group entity.
   * @return Security group.
   */
  public static SecurityGroup toApiRepresentation(SecurityGroupEntity entity) {
    return new SecurityGroup(entity.getName(), entity.isInherited());
  }

  /**
   * Transform a security group to internal entity representation.
   *
   * @param securityGroup Security group.
   * @return Internal security group entity.
   */
  public static SecurityGroupEntity fromApiRepresentation(SecurityGroup securityGroup) {
    return new SecurityGroupEntity(securityGroup.getName(), securityGroup.isInherited());
  }

  /**
   * Transform a list of security group entities to api representation.
   *
   * @param entities List of security group entities.
   * @return List of security groups.
   */
  public static List<SecurityGroup> toApiRepresentation(List<SecurityGroupEntity> entities) {
    return entities.stream().map(entity -> toApiRepresentation(entity)).collect(Collectors.toList());
  }

  /**
   * Transform a list of security groups to internal entity representation.
   *
   * @param securityGroups List of security groups.
   * @return List of security group entities.
   */
  public static List<SecurityGroupEntity> fromApiRepresentation(List<SecurityGroup> securityGroups) {
    return securityGroups.stream().map(sg -> fromApiRepresentation(sg)).collect(Collectors.toList());
  }
}
