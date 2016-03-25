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

package com.vmware.photon.controller.deployer.dcp.upgrade;

import com.vmware.photon.controller.api.HostState;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.common.config.ConfigBuilder;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeInformation;
import com.vmware.photon.controller.deployer.DeployerConfig;
import com.vmware.photon.controller.deployer.dcp.DeployerContext;
import com.vmware.photon.controller.deployer.dcp.DeployerXenonServiceHost;
import com.vmware.photon.controller.deployer.dcp.entity.SampleService;
import com.vmware.photon.controller.deployer.dcp.entity.SampleServiceFactory;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;


/**
 * Tests for the {@link ReflectionTransformationService} class.
 */
public class ReflectionTransformationServiceTest {
  private static final String configFilePath = "/config.yml";

  private TestEnvironment env;

  private DeployerConfig deployerConfig;

  private DeployerContext deployerContext;

  @BeforeMethod
  public void setUp() throws Throwable {
    deployerConfig = ConfigBuilder.build(DeployerConfig.class,
        this.getClass().getResource(configFilePath).getPath());
    deployerContext = spy(deployerConfig.getDeployerContext());

    env = new TestEnvironment.Builder()
        .hostCount(1)
        .deployerContext(deployerContext)
        .build();
    ReflectionTransformationService service = spy(new ReflectionTransformationService());

    UpgradeInformation info = new UpgradeInformation();
    info.destinationFactoryServicePath = "/some/path";
    info.serviceType = SampleService.State.class;

    doReturn(Collections.singletonList(info)).when(deployerContext).getUpgradeInformation();

    CountDownLatch l = new CountDownLatch(env.getHosts().length);
    Operation op = Operation.createPost(env.getHosts()[0].getPublicUri())
      .setCompletion((o, e) -> {
        l.countDown();
      })
      .setReferer(env.getHosts()[0].getPublicUri());

    for (DeployerXenonServiceHost host : env.getHosts()) {
      host.startService(op, service);
      ServiceHostUtils.startServices(host, HostServiceFactory.class);
      ServiceHostUtils.startServices(host, SampleServiceFactory.class);
    }
    l.await();
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    env.stop();
  }

  @Test
  public void success() throws Throwable {
    HostService.State hostState = new HostService.State();
    hostState.hostAddress = "address1";
    hostState.usageTags = Collections.singleton(UsageTag.CLOUD.name());
    hostState.state = HostState.READY;
    hostState.userName = "u1";
    hostState.password = "pwd";
    hostState = TestHelper.createHostService(env, hostState);

    Map<String, String> map = new HashMap<>();
    map.put(Utils.toJson(hostState), "/some/path");
    CountDownLatch l = new CountDownLatch(1);
    SampleService.State[] newState = new SampleService.State[1];

    Operation
        .createPost(UriUtils.buildUri(env.getHosts()[0], ReflectionTransformationService.SELF_LINK))
        .setBody(map)
        .setCompletion((o, e) -> {
          if (e != null) {
            e.printStackTrace();
            l.countDown();
            return;
          }
          Map<?, ?> body = o.getBody(Map.class);
          newState[0] = Utils.fromJson(body.entrySet().iterator().next().getKey(), SampleService.State.class);
          System.out.println(Utils.toJsonHtml(newState[0]));
          l.countDown();
        })
        .setReferer(env.getHosts()[0].getPublicUri())
        .sendWith(env.getHosts()[0]);
      l.await();

    assertThat(newState[0].field1, is(hostState.hostAddress));
    assertThat(newState[0].fieldTags, is(hostState.usageTags));
  }
}
