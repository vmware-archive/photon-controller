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

package com.vmware.photon.controller.housekeeper.service;

import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.tests.TestServiceIgnoresPosts;
import com.vmware.photon.controller.housekeeper.dcp.ImageRemoverService;
import com.vmware.photon.controller.housekeeper.dcp.ImageRemoverServiceFactory;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageRequest;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResponse;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageStatusResponse;
import com.vmware.photon.controller.housekeeper.helpers.TestHelper;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.xenon.common.Operation;

import com.google.inject.Injector;
import org.hamcrest.Matchers;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Test {@link ImageRemover}.
 */
public class ImageRemoverTest {

  private static final String configFilePath = "/config.yml";

  private Injector injector;
  private TestHost dcpHost;
  private ImageRemover remover;


  private String startTestServiceInStage(ImageRemoverService.TaskState.TaskStage stage) throws Throwable {
    ImageRemoverService.State state = new ImageRemoverService.State();
    state.taskInfo = new ImageRemoverService.TaskState();
    state.taskInfo.stage = stage;

    return startTestService(state);
  }

  private String startTestService(ImageRemoverService.State state) throws Throwable {
    TestServiceIgnoresPosts service = new TestServiceIgnoresPosts(ImageRemoverService.State.class);

    Operation op = dcpHost.startServiceSynchronously(service, state, ImageRemoverServiceFactory.SELF_LINK);
    return op.getBody(ImageRemoverService.State.class).documentSelfLink;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the RemoveImage method.
   */
  public class RemoveImageTest {
    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);
      HostClient hostClient = injector.getInstance(HostClient.class);
      dcpHost = spy(TestHost.create(hostClient));
      remover = spy(new ImageRemover(dcpHost));

      LoggingUtils.setRequestId(null);
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (dcpHost != null) {
        TestHost.destroy(dcpHost);
      }
    }

    @Test
    public void testOperationContainsContextId() throws Throwable {
      LoggingUtils.setRequestId("validRequestId");

      String[] contextId = TestHelper.setupOperationContextIdCaptureOnSendRequest(dcpHost);
      assertThat("Unexpected operation RequestId", contextId[0], not("validRequestId"));

      RemoveImageRequest requestStart = new RemoveImageRequest();
      remover.removeImage(requestStart);

      assertThat("Operation RequestId does not match.", contextId[0], is("validRequestId"));
    }

    @Test
    public void testOperationDoesNotContainContextId() throws Throwable {
      String[] contextId = TestHelper.setupOperationContextIdCaptureOnSendRequest(dcpHost);

      RemoveImageRequest requestStart = new RemoveImageRequest();
      remover.removeImage(requestStart);

      assertThat("Operation RequestId is not 'null'", contextId[0], nullValue());
    }

    @Test
    public void testRemoveTriggerFails() throws Throwable {
      doThrow(Exception.class).when(dcpHost).sendRequest(any(Operation.class));

      TestServiceIgnoresPosts service = new TestServiceIgnoresPosts(ImageRemoverService.State.class);
      dcpHost.startServiceSynchronously(service, null, ImageRemoverServiceFactory.SELF_LINK);

      RemoveImageRequest request = new RemoveImageRequest();
      RemoveImageResponse response = remover.removeImage(request);

      assertThat(response.getResult().getCode(), Matchers.is(RemoveImageResultCode.SYSTEM_ERROR));
    }

    @Test
    public void testRemoveTriggerTimesOut() throws Throwable {
      doNothing().when(dcpHost).sendRequest(any(Operation.class));
      remover.setDcpOperationTimeout(1);

      TestServiceIgnoresPosts service = new TestServiceIgnoresPosts(ImageRemoverService.State.class);
      dcpHost.startServiceSynchronously(service, null, ImageRemoverServiceFactory.SELF_LINK);

      RemoveImageRequest request = new RemoveImageRequest();
      RemoveImageResponse response = remover.removeImage(request);

      assertThat(response.getResult().getCode(), is(RemoveImageResultCode.SYSTEM_ERROR));
    }
  }

  /**
   * Tests for the GetRemoveImageStatus method.
   */
  public class GetRemoveImageStatusTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);
      HostClient hostClient = injector.getInstance(HostClient.class);
      dcpHost = spy(TestHost.create(hostClient));
      remover = spy(new ImageRemover(dcpHost));

      LoggingUtils.setRequestId(null);
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (dcpHost != null) {
        TestHost.destroy(dcpHost);
      }
    }

    @Test
    public void testOperationContainsContextId() throws Throwable {
      LoggingUtils.setRequestId("validRequestId");
      String[] contextId = TestHelper.setupOperationContextIdCaptureOnSendRequest(dcpHost);

      RemoveImageStatusRequest request = new RemoveImageStatusRequest(
          startTestServiceInStage(ImageRemoverService.TaskState.TaskStage.FINISHED));
      remover.getImageRemovalStatus(request);

      assertThat("Operation RequestId does not match.", contextId[0], is("validRequestId"));
    }

    @Test
    public void testOperationDoesNotContainContextId() throws Throwable {
      String[] contextId = TestHelper.setupOperationContextIdCaptureOnSendRequest(dcpHost);

      RemoveImageStatusRequest request = new RemoveImageStatusRequest(
          startTestServiceInStage(ImageRemoverService.TaskState.TaskStage.FINISHED));
      remover.getImageRemovalStatus(request);

      assertThat("Operation RequestId is not 'null'", contextId[0], nullValue());
    }

    @Test(dataProvider = "RemovalCompletionStates")
    public void testRemovalCompletionStates(ImageRemoverService.TaskState.TaskStage taskStage,
                                            RemoveImageStatusCode statusCode) throws Throwable {
      RemoveImageStatusRequest request = new RemoveImageStatusRequest(
          startTestServiceInStage(taskStage));

      RemoveImageStatusResponse response = remover.getImageRemovalStatus(request);
      assertThat(response.getStatus().getCode(), is(statusCode));
    }

    @DataProvider(name = "RemovalCompletionStates")
    public Object[][] getRemovalCompletionStatesData() {
      return new Object[][]{
          {ImageRemoverService.TaskState.TaskStage.FINISHED, RemoveImageStatusCode.FINISHED},
          {ImageRemoverService.TaskState.TaskStage.FAILED, RemoveImageStatusCode.FAILED},
          {ImageRemoverService.TaskState.TaskStage.CANCELLED, RemoveImageStatusCode.CANCELLED},
      };
    }

    @Test
    public void testRemovalFailedWithGivenErrorMessage() throws Throwable {
      ImageRemoverService.State state = new ImageRemoverService.State();
      state.taskInfo = new ImageRemoverService.TaskState();
      state.taskInfo.stage = ImageRemoverService.TaskState.TaskStage.FAILED;
      state.taskInfo.failure = new com.vmware.xenon.common.ServiceErrorResponse();
      state.taskInfo.failure.message = "Removal fails";

      String operationId = startTestService(state);

      RemoveImageStatusRequest request = new RemoveImageStatusRequest(operationId);
      RemoveImageStatusResponse response = remover.getImageRemovalStatus(request);

      assertThat(response.getStatus().getCode(), is(RemoveImageStatusCode.FAILED));
      assertThat(response.getStatus().getError(), is("Image removal failed. Error details: " + state
          .taskInfo.failure.message));
    }

    @Test
    public void testServiceNotFound() throws Throwable {
      RemoveImageStatusRequest request = new RemoveImageStatusRequest("service-id");
      RemoveImageStatusResponse response = remover.getImageRemovalStatus(request);
      assertThat(response.getResult().getCode(), is(RemoveImageResultCode.SERVICE_NOT_FOUND));
      assertThat(response.getResult().getError(), is("ImageRemoverService is unavailable"));
    }
  }
}
