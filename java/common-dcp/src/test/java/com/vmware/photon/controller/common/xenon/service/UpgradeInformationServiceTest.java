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

package com.vmware.photon.controller.common.xenon.service;

import com.vmware.photon.controller.common.xenon.service.UpgradeInformationService.UpgradeInfo;
import com.vmware.photon.controller.common.xenon.service.UpgradeInformationService.UpgradeList;
import com.vmware.photon.controller.common.xenon.upgrade.MigrateDuringUpgrade;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This class implements tests for {@link UpgradeInformationService}.
 */
public class UpgradeInformationServiceTest {

  private UpgradeInformationService service;
  private ServiceHost host;

  @BeforeMethod
  public void setUp() {
    service = spy(new UpgradeInformationService());
    host = mock(ServiceHost.class);
  }

  @Test
  public void successFindsOneUpgradeService() throws Throwable {
    CountDownLatch latch = new CountDownLatch(1);
    UpgradeList[] out = new UpgradeList[1];
    Operation get = new Operation().setCompletion((o ,e) -> {
      out[0] = o.getBody(UpgradeList.class);
      latch.countDown();
    });

    ServiceDocumentQueryResult results = new ServiceDocumentQueryResult();
    results.documentLinks = new ArrayList<>(Arrays.asList("/some/service", "/some/other/service"));
    doReturn(host).when(service).getHost();
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        invocation.getArgumentAt(2, Operation.class).setBody(results).complete();
        return null;
      }
    }).when(host).queryServiceUris(eq(EnumSet.of(ServiceOption.FACTORY)), anyBoolean(), any(Operation.class));

    service.handleGet(get);
    latch.await();

    UpgradeList upgradeList = out[0];
    assertThat(upgradeList.list.size(), is(1));
    UpgradeInfo info = upgradeList.list.get(0);
    assertThat(info.destinationFactoryServicePath, is("/some/service"));
    assertThat(info.zookeeperServerSet, is("testService"));
    assertThat(info.destinationFactoryServicePath, is("/some/service"));
    assertThat(info.transformationServicePath, is("/transform"));
  }

  @MigrateDuringUpgrade(
      destinationFactoryServicePath = "/some/service",
      serviceName = "testService",
      sourceFactoryServicePath = "/some/service",
      transformationServicePath = "/transform")
  public static class SomeService extends ServiceDocument {

  }
}
