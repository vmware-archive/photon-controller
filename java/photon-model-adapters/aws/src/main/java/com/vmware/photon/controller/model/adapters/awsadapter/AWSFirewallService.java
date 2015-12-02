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

package com.vmware.photon.controller.model.adapters.awsadapter;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Firewall service for AWS.  AWS Firewalls are implemented by a SecurityGroup
 * which will be the primary artifact created and managed.
 * <p>
 * The initial service will simply be a helper for the instance creation.  This will satisfy
 * EN-1404, but it will be reworked into a stateless service as part of EN-1251.
 */
public class AWSFirewallService {
  public static final String DEFAULT_SECURITY_GROUP_NAME = "cell-manager-security-group";
  public static final String DEFAULT_SECURITY_GROUP_DESC = "VMware Cell Manager security group";
  public static final int[] DEFAULT_ALLOWED_PORTS = {22, 443, 80, 8080, 2376, 2375, 1};
  public static final String DEFAULT_ALLOWED_NETWORK = "0.0.0.0/0";
  public static final String DEFAULT_PROTOCOL = "tcp";

  /*
   *   method will create new or validate existing security group
   *   has the necessary settings for CM to function.  It will return
   *   the security group id that is required during instance
   *   provisioning.
   */
  public String allocateSecurityGroup(AWSAllocation aws) {
    String groupId;
    SecurityGroup group;

    // if the group doesn't exist an exception is thrown.  We won't throw a missing group exception
    // we will continue and create the group
    try {
      group = getSecurityGroup(aws);
      return group.getGroupId();
    } catch (AmazonServiceException t) {
      if (!t.getMessage().contains(
          AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME)) {
        throw t;
      }
    }

    AWSNetworkService netSvc = new AWSNetworkService();
    String subnet = netSvc.getDefaultVCPSubnet(aws);

    // no subnet is not an option...
    if (subnet == null) {
      throw new AmazonServiceException("default VPC not found");
    }

    try {
      groupId = createSecurityGroup(aws);
      updateSecurityGroupRules(aws, groupId, getDefaultRules(subnet));
    } catch (AmazonServiceException t) {
      if (t.getMessage().contains(AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME)) {
        return getSecurityGroup(aws).getGroupId();
      } else {
        throw t;
      }
    }

    return groupId;
  }

  public SecurityGroup getSecurityGroup(AWSAllocation aws) {
    return getSecurityGroup(aws, AWSFirewallService.DEFAULT_SECURITY_GROUP_NAME);
  }

  public SecurityGroup getSecurityGroup(AWSAllocation aws, String name) {
    SecurityGroup cellGroup = null;

    DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
        .withGroupNames(name);
    DescribeSecurityGroupsResult cellGroups = aws.amazonEC2Client.describeSecurityGroups(req);
    if (cellGroups != null) {
      cellGroup = cellGroups.getSecurityGroups().get(0);
    }
    return cellGroup;
  }

  public String createSecurityGroup(AWSAllocation aws) {
    return createSecurityGroup(aws, DEFAULT_SECURITY_GROUP_NAME, DEFAULT_SECURITY_GROUP_DESC);
  }

  public String createSecurityGroup(AWSAllocation aws, String name, String description) {

    CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
        .withDescription(description)
        .withGroupName(name);

    CreateSecurityGroupResult result = aws.amazonEC2Client.createSecurityGroup(req);

    return result.getGroupId();
  }

  public void deleteSecurityGroup(AWSAllocation aws) {
    SecurityGroup group = getSecurityGroup(aws, DEFAULT_SECURITY_GROUP_NAME);
    if (group != null) {
      deleteSecurityGroup(aws, group.getGroupId());
    }
  }

  public void deleteSecurityGroup(AWSAllocation aws, String groupId) {

    DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest()
        .withGroupId(groupId);

    aws.amazonEC2Client.deleteSecurityGroup(req);
  }

  public void updateSecurityGroupRules(AWSAllocation aws, String groupId, List<IpPermission> rules) {
    AuthorizeSecurityGroupIngressRequest req = new AuthorizeSecurityGroupIngressRequest()
        .withGroupId(groupId)
        .withIpPermissions(rules);
    aws.amazonEC2Client.authorizeSecurityGroupIngress(req);
  }

  private IpPermission createRule(int port) {
    return createRule(port, port, DEFAULT_ALLOWED_NETWORK);
  }

  private IpPermission createRule(int fromPort, int toPort, String subnet) {

    return new IpPermission()
        .withIpProtocol(DEFAULT_PROTOCOL)
        .withFromPort(fromPort)
        .withToPort(toPort)
        .withIpRanges(subnet);
  }

  protected List<IpPermission> getDefaultRules(String subnet) {
    List<IpPermission> rules = new ArrayList<>();
    for (int port : DEFAULT_ALLOWED_PORTS) {
      if (port > 1) {
        rules.add(createRule(port));
      } else {
        rules.add(createRule(1, 65535, subnet));
      }
    }
    return rules;
  }
}
