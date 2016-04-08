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

import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreService;
import com.vmware.photon.controller.cloudstore.dcp.entity.DatastoreServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.tests.TestServiceIgnoresPosts;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.housekeeper.dcp.ImageReplicatorService;
import com.vmware.photon.controller.housekeeper.dcp.ImageReplicatorServiceFactory;
import com.vmware.photon.controller.housekeeper.dcp.ImageSeederService;
import com.vmware.photon.controller.housekeeper.dcp.ImageSeederServiceFactory;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusRequest;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusResponse;
import com.vmware.photon.controller.housekeeper.helpers.TestHelper;
import com.vmware.photon.controller.housekeeper.helpers.dcp.TestHost;
import com.vmware.photon.controller.resource.gen.ImageReplication;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

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
import static org.powermock.api.mockito.PowerMockito.doReturn;

import java.net.InetSocketAddress;

/**
 * Test {@link ImageReplicator}.
 */
public class ImageReplicatorTest {

  private static final String configFilePath = "/config.yml";

  private Injector injector;
  private TestHost dcpHost;
  private ImageReplicator replicator;
  private String datastoreId;

  private String startTestReplicatorServiceInStage(ImageReplicatorService.TaskState.TaskStage stage) throws Throwable {
    ImageReplicatorService.State state = new ImageReplicatorService.State();
    state.taskInfo = new ImageReplicatorService.TaskState();
    state.taskInfo.stage = stage;

    return startTestReplicatorService(state);
  }

  private String startTestReplicatorService(ImageReplicatorService.State state) throws Throwable {
    TestServiceIgnoresPosts service = new TestServiceIgnoresPosts(ImageReplicatorService.State.class);

    Operation op = dcpHost.startServiceSynchronously(service, state, ImageReplicatorServiceFactory.SELF_LINK);
    return op.getBody(ImageReplicatorService.State.class).documentSelfLink;
  }

  private String startTestSeederService(ImageSeederService.State state) throws Throwable {
    TestServiceIgnoresPosts service = new TestServiceIgnoresPosts(ImageSeederService.State.class);

    Operation op = dcpHost.startServiceSynchronously(service, state, ImageSeederServiceFactory.SELF_LINK);
    return op.getBody(ImageSeederService.State.class).documentSelfLink;
  }

  private void startTestDatastoreService(DatastoreService.State state) throws Throwable {
    dcpHost.startFactoryServiceSynchronously(new DatastoreServiceFactory(), DatastoreServiceFactory.SELF_LINK);
    Operation patchOp = Operation
        .createPost(UriUtils.buildUri(dcpHost, DatastoreServiceFactory.SELF_LINK, null))
        .setBody(state);
    dcpHost.sendRequestAndWait(patchOp);
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the ReplicateImage method.
   */
  public class ReplicateImageTest {
    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);
      HostClient hostClient = injector.getInstance(HostClient.class);
      dcpHost = spy(TestHost.create(hostClient));
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper();
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(dcpHost.getPreferredAddress(), dcpHost.getPort()));
      cloudStoreHelper.setServerSet(serverSet);
      doReturn(cloudStoreHelper)
          .when(dcpHost).getCloudStoreHelper();
      replicator = spy(new ImageReplicator(dcpHost));

      LoggingUtils.setRequestId(null);

      datastoreId = "datastore-id";
    }

    @AfterMethod
    private void tearDown() throws Throwable {
      if (dcpHost != null) {
        TestHost.destroy(dcpHost);
      }
    }

    @DataProvider(name = "replicationType")
    public Object[][] getReplicationType() {
      return new Object[][]{
          {ImageReplication.ON_DEMAND},
          {ImageReplication.EAGER},
      };
    }

    @Test(dataProvider = "replicationType")
    public void testOperationContainsContextId(ImageReplication imageReplication) throws Throwable {
      LoggingUtils.setRequestId("validRequestId");
      String[] contextId = TestHelper.setupOperationContextIdCaptureOnSendRequest(dcpHost);

      ReplicateImageRequest requestStart = new ReplicateImageRequest();
      requestStart.setReplicationType(imageReplication);
      replicator.replicateImage(requestStart);

      assertThat("Operation RequestId does not match.", contextId[0], is("validRequestId"));
    }

    @Test(dataProvider = "replicationType")
    public void testOperationDoesNotContainContextId(ImageReplication imageReplication) throws Throwable {
      String[] contextId = TestHelper.setupOperationContextIdCaptureOnSendRequest(dcpHost);

      ReplicateImageRequest requestStart = new ReplicateImageRequest();
      requestStart.setReplicationType(imageReplication);
      replicator.replicateImage(requestStart);

      assertThat("Operation RequestId is not 'null'", contextId[0], nullValue());
    }

    @Test
    public void testOperationWithOnDemandReplicationType() throws Throwable {
      ImageSeederService.State state = new ImageSeederService.State();
      state.taskInfo = new ImageSeederService.TaskState();
      state.taskInfo.stage = TaskState.TaskStage.FINISHED;
      String opId = startTestSeederService(state);

      ReplicateImageRequest request = new ReplicateImageRequest();
      request.setReplicationType(ImageReplication.ON_DEMAND);
      request.setDatastore(datastoreId);
      ReplicateImageResponse response = replicator.replicateImage(request);

      assertThat(response.getResult().getCode(), is(ReplicateImageResultCode.OK));
      assertThat(response.getOperation_id(), is(opId));
    }

    @Test
    public void testOperationWithEagerReplicationType() throws Throwable {
      ImageSeederService.State state = new ImageSeederService.State();
      state.taskInfo = new ImageSeederService.TaskState();
      state.taskInfo.stage = TaskState.TaskStage.FINISHED;

      String opId = startTestSeederService(state);
      startTestReplicatorServiceInStage(TaskState.TaskStage.FINISHED);
      ReplicateImageRequest request = new ReplicateImageRequest();
      request.setDatastore(datastoreId);
      request.setReplicationType(ImageReplication.EAGER);

      ReplicateImageResponse response = replicator.replicateImage(request);

      assertThat(response.getResult().getCode(), is(ReplicateImageResultCode.OK));
      assertThat(response.getOperation_id(), is(opId));
    }

    @Test(dataProvider = "replicationType")
    public void testCopyTriggerFails(ImageReplication imageReplication) throws Throwable {
      doThrow(Exception.class).when(dcpHost).sendRequest(any(Operation.class));

      TestServiceIgnoresPosts service = new TestServiceIgnoresPosts(ImageReplicatorService.State.class);
      dcpHost.startServiceSynchronously(service, null, ImageReplicatorServiceFactory.SELF_LINK);

      ReplicateImageRequest request = new ReplicateImageRequest();
      request.setReplicationType(imageReplication);
      ReplicateImageResponse response = replicator.replicateImage(request);

      assertThat(response.getResult().getCode(), Matchers.is(ReplicateImageResultCode.SYSTEM_ERROR));
    }

    @Test(dataProvider = "replicationType")
    public void testCopyTriggerTimesOut(ImageReplication imageReplication) throws Throwable {
      doNothing().when(dcpHost).sendRequest(any(Operation.class));
      replicator.setDcpOperationTimeout(1);

      TestServiceIgnoresPosts service = new TestServiceIgnoresPosts(ImageReplicatorService.State.class);
      dcpHost.startServiceSynchronously(service, null, ImageReplicatorServiceFactory.SELF_LINK);

      ReplicateImageRequest request = new ReplicateImageRequest();
      request.setReplicationType(imageReplication);
      ReplicateImageResponse response = replicator.replicateImage(request);

      assertThat(response.getResult().getCode(), is(ReplicateImageResultCode.SYSTEM_ERROR));
    }
  }

  /**
   * Tests for the GetReplicateImageStatus method.
   */
  public class GetReplicateImageStatusTest {

    @BeforeMethod
    private void setUp() throws Throwable {
      injector = TestHelper.createInjector(configFilePath);
      HostClient hostClient = injector.getInstance(HostClient.class);
      dcpHost = spy(TestHost.create(hostClient));
      replicator = spy(new ImageReplicator(dcpHost));

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

      ReplicateImageStatusRequest request = new ReplicateImageStatusRequest(
          startTestReplicatorServiceInStage(ImageReplicatorService.TaskState.TaskStage.FINISHED));

      replicator.getImageReplicationStatus(request);
      assertThat("Operation RequestId does not match.", contextId[0], is("validRequestId"));
    }

    @Test
    public void testOperationDoesNotContainContextId() throws Throwable {
      String[] contextId = TestHelper.setupOperationContextIdCaptureOnSendRequest(dcpHost);
      ReplicateImageStatusRequest request = new ReplicateImageStatusRequest(
          startTestReplicatorServiceInStage(ImageReplicatorService.TaskState.TaskStage.FINISHED));

      replicator.getImageReplicationStatus(request);
      assertThat("Operation RequestId is not 'null'", contextId[0], nullValue());
    }

    @Test(dataProvider = "ReplicationCompletionStatesData")
    public void testReplicationCompletionStates(ImageReplicatorService.TaskState.TaskStage taskStage,
                                                ReplicateImageStatusCode statusCode) throws Throwable {
      ReplicateImageStatusRequest request = new ReplicateImageStatusRequest(
          startTestReplicatorServiceInStage(taskStage));

      ReplicateImageStatusResponse response = replicator.getImageReplicationStatus(request);
      assertThat(response.getStatus().getCode(), is(statusCode));
    }

    @DataProvider(name = "ReplicationCompletionStatesData")
    public Object[][] getReplicationCompletionStatesData() {
      return new Object[][]{
          {ImageReplicatorService.TaskState.TaskStage.FINISHED, ReplicateImageStatusCode.FINISHED},
          {ImageReplicatorService.TaskState.TaskStage.FAILED, ReplicateImageStatusCode.FAILED},
          {ImageReplicatorService.TaskState.TaskStage.CANCELLED, ReplicateImageStatusCode.CANCELLED},
      };
    }

    @Test
    public void testReplicationFailedWithGivenErrorMessage() throws Throwable {
      ImageReplicatorService.State state = new ImageReplicatorService.State();
      state.taskInfo = new ImageReplicatorService.TaskState();
      state.taskInfo.stage = ImageReplicatorService.TaskState.TaskStage.FAILED;
      state.taskInfo.failure = new com.vmware.xenon.common.ServiceErrorResponse();
      state.taskInfo.failure.message = "Replication fails";

      String operationId = startTestReplicatorService(state);

      ReplicateImageStatusRequest request = new ReplicateImageStatusRequest(operationId);
      ReplicateImageStatusResponse response = replicator.getImageReplicationStatus(request);

      assertThat(response.getStatus().getCode(), is(ReplicateImageStatusCode.FAILED));
      assertThat(response.getStatus().getError(), is("Image replication failed. Error details: " + state
          .taskInfo.failure.message));
    }

    @Test
    public void testServiceNotFound() throws Throwable {
      ReplicateImageStatusRequest request = new ReplicateImageStatusRequest("service-id");
      ReplicateImageStatusResponse response = replicator.getImageReplicationStatus(request);
      assertThat(response.getResult().getCode(), is(ReplicateImageResultCode.SERVICE_NOT_FOUND));
      assertThat(response.getResult().getError(), is("ImageReplicatorService is unavailable"));
    }
  }
}
