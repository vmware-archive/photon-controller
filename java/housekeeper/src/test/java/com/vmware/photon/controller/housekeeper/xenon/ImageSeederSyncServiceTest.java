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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostService;
import com.vmware.photon.controller.cloudstore.xenon.entity.HostServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingServiceFactory;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.QueryTaskUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.housekeeper.helpers.xenon.TestEnvironment;
import com.vmware.photon.controller.housekeeper.helpers.xenon.TestHost;
import com.vmware.photon.controller.housekeeper.xenon.mock.HostClientMock;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.Random;

/**
 * Tests {@link ImageSeederSyncService}.
 */
public class ImageSeederSyncServiceTest {

  private TestHost host;
  private ImageSeederSyncService service;

  private ImageSeederSyncService.State buildValidStartupState() {
    ImageSeederSyncService.State state = new ImageSeederSyncService.State();

    return state;
  }

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {
    @BeforeMethod
    public void setUp() {
      service = new ImageSeederSyncService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.PERIODIC_MAINTENANCE);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageSeederSyncService());
      host = TestHost.create(mock(HostClient.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test start of service with minimal valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMinimalStartState() throws Throwable {
      Operation startOp = host.startServiceSynchronously(service, buildValidStartupState());
      assertThat(startOp.getStatusCode(), is(200));

      ImageSeederSyncService.State savedState = host.getServiceState(ImageSeederSyncService.State.class);
      assertThat(savedState.triggersError, is(new Long(0)));
      assertThat(savedState.triggersSuccess, is(new Long(0)));
    }
  }

  /**
   * Tests for the handlePatch method.
   */
  public class HandlePatchTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = spy(new ImageSeederSyncService());
      host = TestHost.create(mock(HostClient.class));
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        TestHost.destroy(host);
      }

      service = null;
    }

    /**
     * Test patch operation with invalid payload.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidPatchBody() throws Throwable {
      host.startServiceSynchronously(service, buildValidStartupState());

      Operation op = spy(Operation
          .createPatch(UriUtils.buildUri(host, TestHost.SERVICE_URI, null))
          .setBody("invalid body"));

      try {
        host.sendRequestAndWait(op);
        fail("handlePatch did not throw exception on invalid patch");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(),
            startsWith("Unparseable JSON body: java.lang.IllegalStateException: Expected BEGIN_OBJECT"));
      }
    }
  }

  /**
   * Tests for end-to-end scenarios.
   */
  public class EndToEndTest {
    private TestEnvironment machine;
    private TestEnvironment.Builder machineBuilder;

    private HostClientFactory hostClientFactory;
    private CloudStoreHelper cloudStoreHelper;

    private ImageSeederSyncService.State request;

    @BeforeMethod
    public void setup() throws Throwable {
      hostClientFactory = mock(HostClientFactory.class);
      doReturn(new HostClientMock()).when(hostClientFactory).create();
      cloudStoreHelper = new CloudStoreHelper();

      machineBuilder = new TestEnvironment.Builder()
          .cloudStoreHelper(cloudStoreHelper)
          .hostClientFactory(hostClientFactory);

      // Build input.
      request = buildValidStartupState();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (machine != null) {
        machine.stop();
      }
    }

    @DataProvider(name = "hostCount")
    public Object[][] getHostCount() {
      return new Object[][]{
          {1},
          {TestEnvironment.DEFAULT_MULTI_HOST_COUNT}
      };
    }

    @Test(dataProvider = "hostCount")
    public void testPatchThatShouldNotTriggerChildTasks(int hostCount) throws Throwable {
      Random random = new Random();

      request.shouldTriggerTasks = false;
      request.triggersError = (long) random.nextInt(Integer.MAX_VALUE);
      request.triggersSuccess = (long) random.nextInt(Integer.MAX_VALUE);
      machine = machineBuilder
          .hostCount(hostCount)
          .build();

      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);
      ImageService.State createdImageState = createNewImageEntity(ImageState.READY);
      String newImageId = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      createImageToImageDatastoreDocument(newImageId);

      // Send a patch to the trigger service that just updates the stats
      ImageSeederSyncService.State state = machine.callServiceSynchronously(
          ImageSeederSyncServiceFactory.SELF_LINK,
          request,
          ImageSeederSyncService.State.class);

      assertThat(state.triggersError, is(request.triggersError));
      assertThat(state.triggersSuccess, is(request.triggersSuccess));

      // Check that ImageSeederService was triggered.
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(
              ImageSeederService.State.class,
              TaskState.TaskStage.STARTED,
              TaskState.TaskStage.FINISHED,
              TaskState.TaskStage.FAILED);

      QueryTask query = QueryTask.create(spec)
          .setDirect(true);
      QueryTask queryResponse = machine.sendQueryAndWait(query);
      assertThat(queryResponse.results.documentLinks.size(), lessThanOrEqualTo(0));
    }

    @Test(dataProvider = "hostCount")
    public void testTriggerSuccess(int hostCount) throws Throwable {
      request.shouldTriggerTasks = true;
      machine = machineBuilder
          .hostCount(hostCount)
          .build();
      ServiceHost host = machine.getHosts()[0];
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      cloudStoreHelper.setServerSet(serverSet);

      // ImageSeederSyncTriggerService should not trigger image in ERROR or CREATING state.
      createNewImageEntity(ImageState.ERROR);
      createNewImageEntity(ImageState.CREATING);
      ImageService.State createdImageState = createNewImageEntity(ImageState.READY);

      String newImageId = ServiceUtils.getIDFromDocumentSelfLink(createdImageState.documentSelfLink);
      createImageToImageDatastoreDocument(newImageId);

      // Send a patch to the trigger service to simulate a trigger service patch kicking in
      machine.callServiceSynchronously(
          ImageSeederSyncServiceFactory.SELF_LINK,
          request,
          ImageSeederSyncService.State.class);

      // Check that ImageSeederService was triggered.
      QueryTask.QuerySpecification spec =
          QueryTaskUtils.buildTaskStatusQuerySpec(
              ImageSeederService.State.class,
              TaskState.TaskStage.STARTED,
              TaskState.TaskStage.FINISHED,
              TaskState.TaskStage.FAILED);

      QueryTask query = QueryTask.create(spec)
          .setDirect(true);
      QueryTask queryResponse = machine.waitForQuery(query,
          (QueryTask queryTask) ->
              queryTask.results.documentLinks.size() >= 1
      );
      assertThat(queryResponse.results.documentLinks.size(), equalTo(1));
    }

    private ImageService.State createNewImageEntity(ImageState imageState) throws Throwable {
      machine.startFactoryServiceSynchronously(ImageServiceFactory.class, ImageServiceFactory.SELF_LINK);
      ServiceHost host = machine.getHosts()[0];

      ImageService.State state = new ImageService.State();
      state.name = "image-1";
      state.replicationType = ImageReplicationType.EAGER;
      state.state = imageState;

      Operation op = cloudStoreHelper
          .createPost(ImageServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a reference image.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(ImageService.State.class);
    }

    private ImageToImageDatastoreMappingService.State createImageToImageDatastoreDocument(String imageId) throws
        Throwable {
      machine.startFactoryServiceSynchronously(ImageToImageDatastoreMappingServiceFactory.class,
          ImageToImageDatastoreMappingServiceFactory.SELF_LINK);
      ServiceHost host = machine.getHosts()[0];

      ImageToImageDatastoreMappingService.State state = new ImageToImageDatastoreMappingService.State();
      state.imageId = imageId;
      state.imageDatastoreId = "image-datastore-id";

      Operation op = cloudStoreHelper
          .createPost(ImageToImageDatastoreMappingServiceFactory.SELF_LINK)
          .setBody(state)
          .setCompletion((operation, throwable) -> {
            if (null != throwable) {
              Assert.fail("Failed to create a reference image.");
            }
          });
      Operation result = ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
      return result.getBody(ImageToImageDatastoreMappingService.State.class);
    }
  }
}
