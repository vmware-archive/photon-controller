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

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UtilsHelper;

import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.number.BigDecimalCloseTo.closeTo;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

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

  /**
   * Tests the expireDocumentOnDelete method.
   */
  public class ExpireDocumentOnDeleteTest {
    private Service service;
    private Operation deleteOperation;

    @BeforeMethod
    public void setup() {
      service = mock(StatefulService.class);
      deleteOperation = mock(Operation.class);
    }

    /**
     * Test default expiration is not applied if it is already specified in current state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInCurrentState() throws Throwable {
      ServiceDocument currentState = new ServiceDocument();
      currentState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(Integer.MAX_VALUE);
      when(service.getState(anyObject())).thenReturn(currentState);

      when(deleteOperation.hasBody()).thenReturn(true);
      ServiceDocument deleteState = new ServiceDocument();
      deleteState.documentExpirationTimeMicros = 0L;
      when(deleteOperation.getBody(ServiceDocument.class)).thenReturn(deleteState);

      ServiceUtils.expireDocumentOnDelete(service, ServiceDocument.class, deleteOperation);

      ArgumentCaptor<ServiceDocument> stateArgument = ArgumentCaptor.forClass(ServiceDocument.class);
      ArgumentCaptor<Operation> operationArgument = ArgumentCaptor.forClass(Operation.class);
      verify(service, times(1)).setState(operationArgument.capture(), stateArgument.capture());
      verify(deleteOperation, times(1)).complete();
      assertThat(stateArgument.getValue().documentExpirationTimeMicros, not(0L));
      assertThat(new BigDecimal(stateArgument.getValue().documentExpirationTimeMicros),
          is(closeTo(
              new BigDecimal(
                  ServiceUtils.computeExpirationTime(Integer.MAX_VALUE)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(1)))));
    }

    /**
     * Test default expiration is not applied if it is already specified in delete operation state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInDeleteOperation() throws Throwable {
      ServiceDocument currentState = new ServiceDocument();
      currentState.documentExpirationTimeMicros = 1L;
      when(service.getState(anyObject())).thenReturn(currentState);

      when(deleteOperation.hasBody()).thenReturn(true);
      ServiceDocument deleteState = new ServiceDocument();
      deleteState.documentExpirationTimeMicros = Long.MAX_VALUE;
      when(deleteOperation.getBody(anyObject())).thenReturn(deleteState);

      ServiceUtils.expireDocumentOnDelete(service, ServiceDocument.class, deleteOperation);

      ArgumentCaptor<ServiceDocument> stateArgument = ArgumentCaptor.forClass(ServiceDocument.class);
      ArgumentCaptor<Operation> operationArgument = ArgumentCaptor.forClass(Operation.class);
      verify(service, times(1)).setState(operationArgument.capture(), stateArgument.capture());
      verify(deleteOperation, times(1)).complete();
      assertThat(stateArgument.getValue().documentExpirationTimeMicros, is(Long.MAX_VALUE));
    }

    /**
     * Test expiration of deleted document using default value.
     *
     * @throws Throwable
     */
    @Test
    public void testDeleteWithDefaultExpiration() throws Throwable {
      ServiceDocument currentState = new ServiceDocument();
      when(service.getState(anyObject())).thenReturn(currentState);

      ServiceUtils.expireDocumentOnDelete(service, ServiceDocument.class, deleteOperation);

      ArgumentCaptor<ServiceDocument> stateArgument = ArgumentCaptor.forClass(ServiceDocument.class);
      ArgumentCaptor<Operation> operationArgument = ArgumentCaptor.forClass(Operation.class);
      verify(service, times(1)).setState(operationArgument.capture(), stateArgument.capture());
      verify(deleteOperation, times(1)).complete();
      assertThat(stateArgument.getValue().documentExpirationTimeMicros, not(0L));
      assertThat(new BigDecimal(stateArgument.getValue().documentExpirationTimeMicros),
          is(closeTo(
              new BigDecimal(
                  ServiceUtils.computeExpirationTime(ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS)),
              new BigDecimal(TimeUnit.MINUTES.toMicros(1)))));
    }
  }
}
