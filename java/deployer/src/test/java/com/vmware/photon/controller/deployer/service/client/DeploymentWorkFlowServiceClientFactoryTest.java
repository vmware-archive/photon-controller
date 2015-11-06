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

package com.vmware.photon.controller.deployer.service.client;

import com.vmware.photon.controller.deployer.DeployerConfig;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Implements tests for {@link DeploymentWorkflowServiceClientFactory}.
. */
public class DeploymentWorkFlowServiceClientFactoryTest {
  private DeploymentWorkflowServiceClientFactory target;
  private DeployerConfig deployerConfig;

  @BeforeMethod
  public void before() {
    deployerConfig = mock(DeployerConfig.class);
    target = new DeploymentWorkflowServiceClientFactory(deployerConfig);
  }

  @Test
  public void instancesDiffer() {
    assertThat(target.getInstance(null), not(is(target.getInstance(null))));
  }
}
