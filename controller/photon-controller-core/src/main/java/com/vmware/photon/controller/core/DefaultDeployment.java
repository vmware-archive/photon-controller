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

package com.vmware.photon.controller.core;

import com.vmware.photon.controller.api.frontend.config.AuthConfig;
import com.vmware.photon.controller.api.model.DeploymentState;
import com.vmware.photon.controller.api.model.StatsStoreType;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentServiceFactory;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.deployengine.AuthHelper;
import com.vmware.photon.controller.deployer.deployengine.AuthHelperFactory;
import com.vmware.photon.controller.deployer.xenon.DeployerContext;
import com.vmware.photon.controller.deployer.xenon.DeployerServiceGroup;
import com.vmware.photon.controller.deployer.xenon.constant.DeployerDefaults;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * PhotonControllerCore entry point.
 */
public class DefaultDeployment {

  private static final Logger logger = LoggerFactory.getLogger(DefaultDeployment.class);
  private String loginURI = null;
  private String logoutURI = null;

  private DeploymentService.State buildServiceStartState(DeployerConfig deployerConfig, AuthConfig authConfig) {
    DeploymentService.State startState = new DeploymentService.State();
    DeployerContext deploymentContext = deployerConfig.getDeployerContext();
    if (authConfig.getAuthSecurityGroups() != null) {
      startState.oAuthSecurityGroups = new ArrayList<>(authConfig.getAuthSecurityGroups());
    }

    startState.imageDataStoreUsedForVMs = deploymentContext.getImageDataStoreUsedForVMs();
    startState.dhcpRelayProfileId = deploymentContext.getDhcpRelayProfileId();
    startState.dhcpRelayServiceId = deploymentContext.getDhcpRelayServiceId();
    startState.imageId = deploymentContext.getImageId();
    startState.projectId = deploymentContext.getProjectId();
    startState.ntpEndpoint = deploymentContext.getNtpEndpoint();
    startState.oAuthEnabled = authConfig.isAuthEnabled();
    startState.oAuthTenantName = authConfig.getAuthDomain();
    startState.oAuthServerAddress = authConfig.getAuthServerAddress();
    startState.oAuthLoadBalancerAddress = authConfig.getAuthLoadBalancerAddress();
    startState.oAuthServerPort = authConfig.getAuthServerPort();
    startState.oAuthSwaggerLoginEndpoint = authConfig.getAuthSwaggerLoginEndpoint();
    startState.oAuthSwaggerLogoutEndpoint = authConfig.getAuthSwaggerLogoutEndpoint();
    startState.oAuthMgmtUiLoginEndpoint = loginURI;
    startState.oAuthMgmtUiLogoutEndpoint = logoutURI;
    startState.sdnEnabled = deploymentContext.getSdnEnabled();
    startState.networkManagerAddress = deploymentContext.getNetworkManagerAddress();
    startState.networkManagerUsername = deploymentContext.getNetworkManagerUsername();
    startState.networkManagerPassword = deploymentContext.getNetworkManagerPassword();
    startState.networkTopRouterId = deploymentContext.getNetworkTopRouterId();
    startState.networkZoneId = deploymentContext.getNetworkZoneId();
    startState.dhcpRelayProfileId = deploymentContext.getDhcpRelayProfileId();
    startState.dhcpRelayServiceId = deploymentContext.getDhcpRelayServiceId();
    startState.ipRange = deploymentContext.getIpRange();
    startState.floatingIpRange = deploymentContext.getFloatingIpRange();
    startState.syslogEndpoint = deploymentContext.getSyslogEndpoint();
    startState.statsEnabled = deploymentContext.getStatsEnabled();
    startState.statsStoreEndpoint = deploymentContext.getStatsStoreEndpoint();
    startState.statsStorePort = deploymentContext.getStatsStorePort();
    startState.imageDataStoreNames = new HashSet<>();
    String statsStoreType = deploymentContext.getStatsStoreType();
    startState.statsStoreType = statsStoreType == null ? null : StatsStoreType.valueOf(statsStoreType);
    startState.loadBalancerEnabled = deploymentContext.getLoadBalancerEnabled();
    startState.loadBalancerAddress = deploymentContext.getLoadBalancerAddress();
    startState.dhcpVmConfiguration = deploymentContext.getDhcpVmConfiguration();
    startState.state = DeploymentState.READY;
    startState.documentSelfLink = DeployerDefaults.DEFAULT_DEPLOYMENT_ID;
    return startState;
  }

  public void createDefaultDeployment(DeployerConfig deployerConfig,
                                      AuthConfig authConfig,
                                      ServiceHost xenonHost) throws Throwable {

    xenonHost.registerForServiceAvailability((Operation operation, Throwable throwable) -> {
      DeploymentService.State startState = buildServiceStartState(deployerConfig, authConfig);
      // Deployment service supports Idempotent POST, with that option we make sure that
      // a POST call to create new deployment service with same Id would not fail and
      // will be converted into PUT call.
      // We have that option so that we can create default deployment at startup.
      // In multi-host environment, hosts creating deployment service will not fail,
      // if its peer has already created this default deployment service object.
      Operation op = Operation.createPost(
          UriUtils.buildUri(xenonHost, DeploymentServiceFactory.SELF_LINK, null))
          .setReferer(xenonHost.getUri())
          .setBody(startState);
      xenonHost.sendRequest(op);
    }, DeploymentServiceFactory.SELF_LINK);
  }

  /**
   * Create default deployment after registering the client to Lotus, and producing the URL to access it.
   */
  public void createDefaultDeployment(final ServiceHost host,
                                      final AuthConfig authConfig,
                                      final DeployerConfig deployerConfig,
                                      final String lbIpAddress) throws Throwable {


    DeployerServiceGroup deployerServiceGroup =
        (DeployerServiceGroup) ((PhotonControllerXenonHost) host).getDeployer();
    AuthHelperFactory authHelperFactory = deployerServiceGroup.getAuthHelperFactory();
    final AuthHelper authHelper = authHelperFactory.create();

    logger.info("Starting a thread to register client {} at {}:{} using user {} on tenant %s.",
        lbIpAddress,
        authConfig.getAuthServerAddress(),
        authConfig.getAuthServerPort(),
        authConfig.getAuthUserName(),
        authConfig.getAuthDomain());
    createDefaultDeployment(deployerConfig, authConfig, host);
  }
}
