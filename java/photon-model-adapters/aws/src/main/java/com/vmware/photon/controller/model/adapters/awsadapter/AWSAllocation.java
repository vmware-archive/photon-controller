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

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.DiskService.DiskType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.AuthCredentialsService;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;

import java.util.Map;

/**
 * AWS Allocation.
 */
public class AWSAllocation {
  public AWSStages stage;

  transient Operation awsOperation;
  public ComputeInstanceRequest computeRequest;
  public ComputeStateWithDescription child;
  public ComputeStateWithDescription parent;
  public AmazonEC2AsyncClient amazonEC2Client;
  public AuthCredentialsService.AuthCredentialsServiceState parentAuth;
  public Map<DiskType, DiskState> childDisks;
  public String securityGroupId;
  public Throwable error;

  public AWSFirewallService fwService;

  /**
   * Initialize with request info and first stage.
   */
  public AWSAllocation(ComputeInstanceRequest computeReq) {
    this.fwService = new AWSFirewallService();
    this.computeRequest = computeReq;
    this.stage = AWSStages.VMDESC;
  }
}
