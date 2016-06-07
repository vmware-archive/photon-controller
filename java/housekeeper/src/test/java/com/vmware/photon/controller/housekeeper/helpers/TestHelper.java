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

package com.vmware.photon.controller.housekeeper.helpers;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Helper class for tests.
 */
public class TestHelper {

  public static String[] setupOperationContextIdCaptureOnSendRequest(ServiceHost host) {
    final String[] contextId = new String[1];

    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Operation op = (Operation) invocation.getArguments()[0];
        contextId[0] = op.getContextId();
        return invocation.callRealMethod();
      }
    }).when(host).sendRequest(any(Operation.class));

    return contextId;
  }
}
