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

package com.vmware.photon.controller.cloudstore.xenon.upgrade;

import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.CloudStoreXenonHost;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Test for {@link HostTransformationService}.
 */
public class HostTransformationServiceTest {

  private TestEnvironment env;

  @BeforeMethod
  public void setUp() throws Throwable {
    env = TestEnvironment.create(1);
    for (CloudStoreXenonHost host : env.getHosts()) {
      host.startService(new HostTransformationService());
    }
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    env.stop();
  }

  @Test
  public void shouldChangeToCloudOnlyFromMixedHost() throws Throwable {
    HostService.State oldState = new HostService.State();
    oldState.usageTags = new HashSet<>(Arrays.asList(UsageTag.MGMT.name(), UsageTag.CLOUD.name()));

    HostService.State[] newState = new HostService.State[1];

    Map<String, String> map = new HashMap<>();
    map.put(Utils.toJson(false, false, oldState), "/some/path");
    CountDownLatch l = new CountDownLatch(1);

    Operation operation = Operation
      .createPost(UriUtils.buildUri(env.getHosts()[0], HostTransformationService.SELF_LINK))
      .setBody(map)
      .setCompletion((o, e) -> {
        if (e != null) {
          e.printStackTrace();
          l.countDown();
          return;
        }
        Map<?, ?> body = o.getBody(Map.class);
        newState[0] = Utils.fromJson(body.entrySet().iterator().next().getKey(), HostService.State.class);
        System.out.println(Utils.toJson(true, true, newState[0]));
        l.countDown();
      })
      .setReferer(env.getHosts()[0].getPublicUri());
    operation.sendWith(env.getHosts()[0]);
    l.await();

    assertThat(newState[0].usageTags.size(), is(1));
    assertThat(newState[0].usageTags.iterator().next(), is(UsageTag.CLOUD.name()));
  }

  @Test
  public void shouldChangeToCloudOnlyFromMgmtHost() throws Throwable {
    HostService.State oldState = new HostService.State();
    oldState.usageTags = new HashSet<>(Arrays.asList(UsageTag.MGMT.name()));

    HostService.State[] newState = new HostService.State[1];

    Map<String, String> map = new HashMap<>();
    map.put(Utils.toJson(false, false, oldState), "/some/path");
    CountDownLatch l = new CountDownLatch(1);

    Operation operation = Operation
      .createPost(UriUtils.buildUri(env.getHosts()[0], HostTransformationService.SELF_LINK))
      .setBody(map)
      .setCompletion((o, e) -> {
        if (e != null) {
          e.printStackTrace();
          l.countDown();
          return;
        }
        Map<?, ?> body = o.getBody(Map.class);
        newState[0] = Utils.fromJson(body.entrySet().iterator().next().getKey(), HostService.State.class);
        System.out.println(Utils.toJson(true, true, newState[0]));
        l.countDown();
      })
      .setReferer(env.getHosts()[0].getPublicUri());
    operation.sendWith(env.getHosts()[0]);
    l.await();

    assertThat(newState[0].usageTags.size(), is(1));
    assertThat(newState[0].usageTags.iterator().next(), is(UsageTag.CLOUD.name()));
  }

  @Test
  public void shouldNotChangeCloudOnlyHost() throws Throwable {
    HostService.State oldState = new HostService.State();
    oldState.usageTags = new HashSet<>(Arrays.asList(UsageTag.CLOUD.name()));

    HostService.State[] newState = new HostService.State[1];

    Map<String, String> map = new HashMap<>();
    map.put(Utils.toJson(false, false, oldState), "/some/path");
    CountDownLatch l = new CountDownLatch(1);

    Operation operation = Operation
      .createPost(UriUtils.buildUri(env.getHosts()[0], HostTransformationService.SELF_LINK))
      .setBody(map)
      .setCompletion((o, e) -> {
        if (e != null) {
          e.printStackTrace();
          l.countDown();
          return;
        }
        Map<?, ?> body = o.getBody(Map.class);
        newState[0] = Utils.fromJson(body.entrySet().iterator().next().getKey(), HostService.State.class);
        System.out.println(Utils.toJson(true, true, newState[0]));
        l.countDown();
      })
      .setReferer(env.getHosts()[0].getPublicUri());
    operation.sendWith(env.getHosts()[0]);
    l.await();

    assertThat(newState[0].usageTags.size(), is(1));
    assertThat(newState[0].usageTags.iterator().next(), is(UsageTag.CLOUD.name()));
  }
}
