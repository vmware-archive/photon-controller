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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

/**
 * Tests {@link ImageToImageDatastoreMappingService}.
 */
public class ImageToImageDatastoreMappingServiceTest {

  private XenonRestClient dcpRestClient;
  private BasicServiceHost host;
  private ImageToImageDatastoreMappingService service;
  private ImageToImageDatastoreMappingService.State testState;

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
      service = new ImageToImageDatastoreMappingService();
    }

    /**
     * Test that the service starts with the expected options.
     */
    @Test
    public void testServiceOptions() {
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);
      assertThat(service.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the handleStart method.
   */
  public class HandleStartTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ImageToImageDatastoreMappingService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          ImageToImageDatastoreMappingServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new ImageToImageDatastoreMappingService.State();
      testState.imageId = UUID.randomUUID().toString();
      testState.imageDatastoreId = UUID.randomUUID().toString();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    /**
     * Test start of service with valid start state.
     *
     * @throws Throwable
     */
    @Test
    public void testStartState() throws Throwable {
      host.startServiceSynchronously(new ImageToImageDatastoreMappingServiceFactory(), null);

      Operation result = dcpRestClient.post(ImageToImageDatastoreMappingServiceFactory.SELF_LINK, testState);

      assertThat(result.getStatusCode(), is(200));
      ImageToImageDatastoreMappingService.State createdState =
          result.getBody(ImageToImageDatastoreMappingService.State.class);
      assertThat(createdState.imageId, is(equalTo(testState.imageId)));
      assertThat(createdState.imageDatastoreId, is(equalTo(testState.imageDatastoreId)));
      ImageToImageDatastoreMappingService.State savedState =
          host.getServiceState(ImageToImageDatastoreMappingService.State.class, createdState.documentSelfLink);
      assertThat(savedState.imageId, is(equalTo(testState.imageId)));
      assertThat(savedState.imageDatastoreId, is(equalTo(testState.imageDatastoreId)));
    }

    /**
     * Test service start with missing imageId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingImageId() throws Throwable {
      ImageToImageDatastoreMappingService.State startState = new ImageToImageDatastoreMappingService.State();
      startState.imageDatastoreId = "image-datastore-id";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'imageId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("imageId cannot be null"));
      }
    }

    /**
     * Test service start with missing imageDatastoreId in start state.
     *
     * @throws Throwable
     */
    @Test
    public void testMissingImageDatastoreId() throws Throwable {
      ImageToImageDatastoreMappingService.State startState = new ImageToImageDatastoreMappingService.State();
      startState.imageId = "image-id";

      try {
        host.startServiceSynchronously(service, startState);
        fail("Service start did not fail when 'imageDatastoreId' was null");
      } catch (BadRequestException e) {
        assertThat(e.getMessage(), is("imageDatastoreId cannot be null"));
      }
    }
  }

  /**
   * Tests for the handleDelete method.
   */
  public class HandleDeleteTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      service = new ImageToImageDatastoreMappingService();
      host = BasicServiceHost.create(BasicServiceHost.BIND_ADDRESS,
          BasicServiceHost.BIND_PORT,
          null,
          ImageToImageDatastoreMappingServiceFactory.SELF_LINK,
          10, 10);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpRestClient = new XenonRestClient(serverSet, Executors.newFixedThreadPool(1));
      dcpRestClient.start();

      testState = new ImageToImageDatastoreMappingService.State();
      testState.imageId = UUID.randomUUID().toString();
      testState.imageDatastoreId = UUID.randomUUID().toString();

      host.startServiceSynchronously(new ImageToImageDatastoreMappingServiceFactory(), null);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      service = null;
      dcpRestClient.stop();
    }

    /**
     * Test default expiration is not applied if it is already specified in current state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInCurrentState() throws Throwable {
      testState.documentExpirationTimeMicros = Long.MAX_VALUE;
      Operation result = dcpRestClient.post(ImageToImageDatastoreMappingServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));

      ImageToImageDatastoreMappingService.State createdState = result.getBody(
          ImageToImageDatastoreMappingService.State.class);
      assertThat(createdState.documentExpirationTimeMicros, is(testState.documentExpirationTimeMicros));

      ImageToImageDatastoreMappingService.State savedState =
          host.getServiceState(ImageToImageDatastoreMappingService.State.class, createdState.documentSelfLink);
      assertThat(savedState.documentExpirationTimeMicros, is(testState.documentExpirationTimeMicros));

      ImageToImageDatastoreMappingService.State deleteState = new ImageToImageDatastoreMappingService.State();
      deleteState.documentExpirationTimeMicros = 0;
      result = dcpRestClient.delete(createdState.documentSelfLink, deleteState);
      assertThat(result.getStatusCode(), is(200));

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ImageToImageDatastoreMappingService.State.class));

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.options.add(QueryTask.QuerySpecification.QueryOption.INCLUDE_DELETED);

      QueryTask queryTask = QueryTask.create(spec)
          .setDirect(true);

      host.waitForQuery(queryTask,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() == 1;
            }
          });
    }

    /**
     * Test default expiration is not applied if it is already specified in delete operation state.
     *
     * @throws Throwable
     */
    @Test
    public void testDefaultExpirationIsNotAppliedIfItIsAlreadySpecifiedInDeleteOperation() throws Throwable {
      Operation result = dcpRestClient.post(ImageToImageDatastoreMappingServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));

      ImageToImageDatastoreMappingService.State createdState = result.getBody(
          ImageToImageDatastoreMappingService.State.class);
      assertThat(createdState.documentExpirationTimeMicros, is(0L));

      ImageToImageDatastoreMappingService.State savedState =
          host.getServiceState(ImageToImageDatastoreMappingService.State.class, createdState.documentSelfLink);
      assertThat(savedState.documentExpirationTimeMicros, is(0L));

      ImageToImageDatastoreMappingService.State deleteState = new ImageToImageDatastoreMappingService.State();
      deleteState.documentExpirationTimeMicros = Long.MAX_VALUE;
      result = dcpRestClient.delete(createdState.documentSelfLink, deleteState);
      assertThat(result.getStatusCode(), is(200));

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ImageToImageDatastoreMappingService.State.class));

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.options.add(QueryTask.QuerySpecification.QueryOption.INCLUDE_DELETED);

      QueryTask queryTask = QueryTask.create(spec)
          .setDirect(true);

      host.waitForQuery(queryTask,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() == 1;
            }
          });
    }

    /**
     * Test expiration of deleted document using default value.
     *
     * @throws Throwable
     */
    @Test
    public void testDeleteWithDefaultExpiration() throws Throwable {
      Operation result = dcpRestClient.post(ImageToImageDatastoreMappingServiceFactory.SELF_LINK, testState);
      assertThat(result.getStatusCode(), is(200));

      ImageToImageDatastoreMappingService.State createdState = result.getBody(
          ImageToImageDatastoreMappingService.State.class);
      assertThat(createdState.documentExpirationTimeMicros, is(0L));

      ImageToImageDatastoreMappingService.State savedState =
          host.getServiceState(ImageToImageDatastoreMappingService.State.class, createdState.documentSelfLink);
      assertThat(savedState.documentExpirationTimeMicros, is(0L));

      result = dcpRestClient.delete(createdState.documentSelfLink, null);
      assertThat(result.getStatusCode(), is(200));

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ImageToImageDatastoreMappingService.State.class));

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.options.add(QueryTask.QuerySpecification.QueryOption.INCLUDE_DELETED);

      QueryTask queryTask = QueryTask.create(spec)
          .setDirect(true);

      host.waitForQuery(queryTask,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() == 0;
            }
          });
    }
  }

}
