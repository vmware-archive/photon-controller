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

package com.vmware.photon.controller.deployer.healthcheck;

import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link XenonBasedHealthChecker}.
 */
public class XenonBasedHealthCheckerTest {

  public static final String ADDRESS = "127.0.0.1";
  public static final int PORT = 666;

  private Service service;

  private XenonBasedHealthChecker target;

  @BeforeMethod
  public void beforeMethod() {
    service = mock(Service.class);
    target = new XenonBasedHealthChecker(service, ADDRESS, PORT);
  }

  @Test
  public void returnsHealthyOnReady() throws Throwable {
    mockSendRequest(StatusType.READY);

    assertThat(target.isReady(), is(true));
  }

  @Test(dataProvider = "notReadyStatus")
  public void returnsUnhealthyOnNotReadyStatus(StatusType statusType) {
    mockSendRequest(statusType);

    assertThat(target.isReady(), is(false));
  }

  @Test
  public void returnsUnhealthyOnException() {
    mockSendRequest(new RuntimeException());

    assertThat(target.isReady(), is(false));
  }

  private void mockSendRequest(StatusType statusType) {
    doAnswer(invocation -> {
      Operation op = (Operation) invocation.getArguments()[0];
      op.setBody(Utils.toJson(false, false, new Status(statusType)));
      op.complete();
      return null;
    }).when(service).sendRequest(any(Operation.class));
  }

  private void mockSendRequest(Throwable throwable) {
    doAnswer(invocation -> {
      Operation op = (Operation) invocation.getArguments()[0];
      op.fail(throwable);
      return null;
    }).when(service).sendRequest(any(Operation.class));
  }

  @DataProvider(name = "notReadyStatus")
  public Object[][] getNotRreadyStatus() {
    return new Object[][]{
        {StatusType.ERROR},
        {StatusType.INITIALIZING},
        {StatusType.PARTIAL_ERROR},
        {StatusType.UNREACHABLE},
    };
  }
}
