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

package com.vmware.photon.controller.common.xenon;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UtilsHelper;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.util.EnumSet;
import java.util.HashMap;

/**
 * Test {@link ServiceUtils} class.
 */
public class ServiceUtilsTest {

  @Test
  public void testSetTaskInfoErrorStage() throws Throwable {
    TaskState state = new TaskState();
    ServiceUtils.setTaskInfoErrorStage(state, new RuntimeException("foo"));

    assertThat(state.stage, is(TaskState.TaskStage.FAILED));
    assertThat(state.failure.message, containsString("foo"));
  }

  @Test
  public void testGetDocumentTemplateWithIndexedFields() {
    ServiceDocument serviceDocument = new ServiceDocument();
    serviceDocument.documentDescription = new ServiceDocumentDescription();
    serviceDocument.documentDescription.propertyDescriptions = new HashMap<>();

    ServiceDocumentDescription.PropertyDescription propertyDescription =
        new ServiceDocumentDescription.PropertyDescription();

    serviceDocument.documentDescription.propertyDescriptions.put("DUMMY_PROPERTY", propertyDescription);

    assertThat(propertyDescription.indexingOptions.size(), is(0));

    ServiceUtils.setExpandedIndexing(serviceDocument, "DUMMY_PROPERTY");

    assertThat(propertyDescription.indexingOptions,
        is(EnumSet.of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND)));
  }

  /**
   * Tests the getFmtMsg method.
   */
  public class GetFmtMsgTest {
    private static final String fmt = "LogMessage";
    private Service service;

    @BeforeMethod
    public void setup() {
      service = mock(Service.class);
      doReturn("/service-link").when(service).getSelfLink();

      UtilsHelper.setThreadContextId(null);
    }

    @Test
    public void testMessageIsFormatStringWithAdditionalParams() {
      String fmtString = "[%s]";
      String value = "LogMessage";

      String fmtMessage = ServiceUtils.getFmtMsg(service, fmtString, value);
      assertThat(fmtMessage,
          is(String.format("[%s] %s", service.getSelfLink(), String.format(fmtString, value))));
    }

    @Test
    public void testMessageIsFormatStringIsJustAString() {
      String value = "LogMessage";

      String fmtMessage = ServiceUtils.getFmtMsg(service, value);
      assertThat(fmtMessage, is(String.format("[%s] %s", service.getSelfLink(), value)));
    }

    @Test
    public void testRequestIdForNullRequestId() {
      String fmtMessage = ServiceUtils.getFmtMsg(service, fmt);
      assertThat(fmtMessage, is(String.format("[%s] %s", service.getSelfLink(), fmt)));
    }

    @Test
    public void testRequestIdForValidRequestId() {
      String requestId = "validRequestId";
      UtilsHelper.setThreadContextId(requestId);

      String fmtMessage = ServiceUtils.getFmtMsg(service, fmt);
      assertThat(fmtMessage, containsString(requestId));
      assertThat(fmtMessage, endsWith(String.format("[%s] %s", service.getSelfLink(), fmt)));
    }
  }
}
