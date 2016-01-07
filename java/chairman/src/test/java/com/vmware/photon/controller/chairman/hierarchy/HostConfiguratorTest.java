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

package com.vmware.photon.controller.chairman.hierarchy;

import com.vmware.photon.controller.chairman.HierarchyConfig;
import com.vmware.photon.controller.chairman.service.AvailabilityZone;
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;

import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link HostConfigurator}.
 */
public class HostConfiguratorTest extends PowerMockTestCase {

  @Mock
  private FlowFactory flowFactory;

  private HostConfigurator hostConfigurator;

  @BeforeMethod
  public void setUp() throws Exception {
    hostConfigurator = new HostConfigurator(flowFactory, new HierarchyConfig());
  }

  @Test
  public void testConfigureHost() throws Exception {
    ConfigureHostFlow flow1 = mock(ConfigureHostFlow.class);
    ConfigureHostFlow flow2 = mock(ConfigureHostFlow.class);

    Host host1 = new Host("foo", new AvailabilityZone("bar"), "foo", 1234);
    Host host2 = new Host("foo2", new AvailabilityZone("bar"), "foo", 1234);

    Scheduler sc1 = new Scheduler("sc-1");
    Scheduler sc2 = new Scheduler("sc-2");
    sc1.setOwner(host1);
    sc2.setOwner(host2);
    host1.setParentScheduler(sc1);
    host2.setParentScheduler(sc2);
    Scheduler root = new Scheduler("root");
    root.addChild(sc1);
    root.addChild(sc2);

    ConfigureRequest req1 = HierarchyUtils.getConfigureRequest(host1);
    ConfigureRequest req2 = HierarchyUtils.getConfigureRequest(host2);

    when(flowFactory.createConfigureHostFlow(any(Host.class), any(ConfigureRequest.class))).thenReturn(flow1, flow2);

    final List<String> result = Collections.synchronizedList(new ArrayList<String>());
    final CountDownLatch done = new CountDownLatch(2);

    doAnswer(
        new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            result.add("done1");
            done.countDown();
            return null;
          }
        }
    ).when(flow1).run();

    doAnswer(
        new Answer() {
          @Override
          public Object answer(InvocationOnMock invocation) throws Throwable {
            result.add("done2");
            done.countDown();
            return null;
          }
        }
    ).when(flow2).run();

    hostConfigurator.configure(host1, req1);
    hostConfigurator.configure(host2, req2);

    assertThat(done.await(10, TimeUnit.SECONDS), is(true));
    assertThat(result.contains("done1"), is(true));
    assertThat(result.contains("done2"), is(true));

    verify(flow1).run();
    verify(flow2).run();
  }

}
