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

import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.common.xenon.host.LoggerControlService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.LimitedReplicationExampleFactoryService;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.collections.CollectionUtils;
import org.hamcrest.Matchers;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests {@link XenonRestClient}.
 */
public class XenonRestClientTest {

  private static final Integer MAX_ITERATIONS = 10;

  private BasicServiceHost host;
  private XenonRestClient xenonRestClient;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test(enabled = true)
  public void dummy() {
  }

  private void setUpHostAndClient() throws Throwable {
    host = BasicServiceHost.create();
    host.startServiceSynchronously(ExampleService.createFactory(), null, ExampleService.FACTORY_LINK);
    assertThat(host.checkServiceAvailable(ExampleService.FACTORY_LINK), is(true));

    StaticServerSet serverSet = new StaticServerSet(
        new InetSocketAddress(host.getPreferredAddress(), host.getPort()));

    xenonRestClient = spy(new XenonRestClient(serverSet, Executors.newFixedThreadPool(1)));
  }

  private String createDocument(ExampleService.ExampleServiceState exampleServiceState) throws Throwable {
    return createDocument(xenonRestClient, exampleServiceState);
  }

  private String createDocument(XenonRestClient xenonRestClient, ExampleService.ExampleServiceState exampleServiceState)
      throws Throwable {
    Operation result = xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);

    assertThat(result.getStatusCode(), is(200));
    ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
    assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
    return createdState.documentSelfLink;
  }

  private BasicServiceHost[] setUpMultipleHosts(Integer hostCount) throws Throwable {

    BasicServiceHost[] hosts = new BasicServiceHost[hostCount];
    InetSocketAddress[] servers = new InetSocketAddress[hostCount];
    for (Integer i = 0; i < hostCount; i++) {
      hosts[i] = BasicServiceHost.create();
      hosts[i].setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(100));
      hosts[i].startServiceSynchronously(ExampleService.createFactory(), null, ExampleService.FACTORY_LINK);

      servers[i] = new InetSocketAddress(hosts[i].getPreferredAddress(), hosts[i].getPort());
    }

    if (hostCount > 1) {
      // join peer node group
      BasicServiceHost host = hosts[0];
      for (int i = 1; i < hosts.length; i++) {
        BasicServiceHost peerHost = hosts[i];
        ServiceHostUtils.joinNodeGroup(peerHost, host.getUri().getHost(), host.getPort());
      }

      ServiceHostUtils.waitForNodeGroupConvergence(
          hosts,
          com.vmware.xenon.services.common.ServiceUriPaths.DEFAULT_NODE_GROUP,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_SLEEP);
    }

    StaticServerSet serverSet = new StaticServerSet(servers);
    xenonRestClient = spy(new XenonRestClient(serverSet, Executors.newFixedThreadPool(1)));
    xenonRestClient.start();
    return hosts;
  }

  private XenonRestClient[] setupXenonRestClients(BasicServiceHost[] hosts) {
    XenonRestClient[] xenonRestClients = new XenonRestClient[hosts.length];

    for (Integer i = 0; i < hosts.length; i++) {
      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(hosts[i].getPreferredAddress(), hosts[i].getPort()));
      xenonRestClients[i] = spy(new XenonRestClient(serverSet, Executors.newFixedThreadPool(1)));
      xenonRestClients[i].start();
    }

    return xenonRestClients;
  }

  /**
   * Tests for the post operation.
   */
  public class PostTest {
    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test
    public void testWithStartedClient() throws Throwable {
      xenonRestClient.start();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      Operation result = xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);

      assertThat(result.getStatusCode(), is(200));
      ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
      ExampleService.ExampleServiceState savedState = host.getServiceState(ExampleService.ExampleServiceState.class,
          createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(exampleServiceState.name)));
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testWithoutStartingClient() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);
    }

    @Test(expectedExceptions = XenonRuntimeException.class)
    public void testWithStoppedClient() throws Throwable {
      xenonRestClient.start();
      xenonRestClient.stop();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);
    }

    @Test
    public void testStartForDeletedServiceFail() throws Throwable {
      xenonRestClient.start();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      Operation result = xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);

      assertThat(result.getStatusCode(), is(200));
      ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
      ExampleService.ExampleServiceState savedState = host.getServiceState(ExampleService.ExampleServiceState.class,
          createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(exampleServiceState.name)));

      exampleServiceState.documentSelfLink = createdState.documentSelfLink;

      xenonRestClient.delete(exampleServiceState.documentSelfLink, new ExampleService.ExampleServiceState());

      try {
        xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);
        fail("POST for an existing service should fail");
      } catch (XenonRuntimeException e) {
        assertThat(e.getMessage(), Matchers.containsString("Service already exists or previously deleted"));
      }
    }

    @Test
    public void testStartForDeletedServiceSuccess() throws Throwable {
      xenonRestClient.start();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      Operation result = xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);

      assertThat(result.getStatusCode(), is(200));
      ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
      ExampleService.ExampleServiceState savedState = host.getServiceState(ExampleService.ExampleServiceState.class,
          createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(exampleServiceState.name)));

      exampleServiceState.documentSelfLink = createdState.documentSelfLink;

      xenonRestClient.delete(exampleServiceState.documentSelfLink, new ExampleService.ExampleServiceState());

      result = xenonRestClient.post(true, ExampleService.FACTORY_LINK, exampleServiceState);

      assertThat(result.getStatusCode(), is(200));
      createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
      savedState = host.getServiceState(ExampleService.ExampleServiceState.class,
          createdState.documentSelfLink);
      assertThat(savedState.name, is(equalTo(exampleServiceState.name)));
    }
  }

  /**
   * Tests for the get operation.
   */
  public class GetTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      xenonRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test
    public void testGetOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      Operation result = xenonRestClient.get(documentSelfLink);
      assertThat(result.getStatusCode(), is(200));

      ExampleService.ExampleServiceState savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(exampleServiceState.name)));
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testGetOfNonExistingDocument() throws Throwable {
      xenonRestClient.get(ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString());
    }

    @Test
    public void testGetOfCreatedDocuments() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState1 = new ExampleService.ExampleServiceState();
      exampleServiceState1.name = UUID.randomUUID().toString();
      String documentSelfLink1 = createDocument(exampleServiceState1);

      ExampleService.ExampleServiceState exampleServiceState2 = new ExampleService.ExampleServiceState();
      exampleServiceState2.name = UUID.randomUUID().toString();
      String documentSelfLink2 = createDocument(exampleServiceState2);

      ExampleService.ExampleServiceState exampleServiceState3 = new ExampleService.ExampleServiceState();
      exampleServiceState3.name = UUID.randomUUID().toString();
      String documentSelfLink3 = createDocument(exampleServiceState3);

      Map<String, Operation> results = xenonRestClient.get(
          Arrays.asList(documentSelfLink1, documentSelfLink2, documentSelfLink3), 3);

      assertThat(results.size(), is(3));
      assertTrue(results.containsKey(documentSelfLink1));
      assertThat(results.get(documentSelfLink1).getBody(ExampleService.ExampleServiceState.class).name,
          is(exampleServiceState1.name));
      assertTrue(results.containsKey(documentSelfLink2));
      assertThat(results.get(documentSelfLink2).getBody(ExampleService.ExampleServiceState.class).name,
          is(exampleServiceState2.name));
      assertTrue(results.containsKey(documentSelfLink3));
      assertThat(results.get(documentSelfLink3).getBody(ExampleService.ExampleServiceState.class).name,
          is(exampleServiceState3.name));
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testGetOfOneMissingDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState1 = new ExampleService.ExampleServiceState();
      exampleServiceState1.name = UUID.randomUUID().toString();
      String documentSelfLink1 = createDocument(exampleServiceState1);

      ExampleService.ExampleServiceState exampleServiceState2 = new ExampleService.ExampleServiceState();
      exampleServiceState2.name = UUID.randomUUID().toString();
      String documentSelfLink2 = createDocument(exampleServiceState2);

      String documentSelfLink3 = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();

      xenonRestClient.get(Arrays.asList(documentSelfLink1, documentSelfLink2, documentSelfLink3), 3);
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testGetOfThreeMissingDocuments() throws Throwable {

      String documentSelfLink1 = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();
      String documentSelfLink2 = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();
      String documentSelfLink3 = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();

      xenonRestClient.get(Arrays.asList(documentSelfLink1, documentSelfLink2, documentSelfLink3), 3);
    }
  }

  /**
   * Tests for the send operation.
   */
  public class SendTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      xenonRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test
    public void testTimeoutOfOperation() throws Throwable {
      String documentSelfLink = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();
      URI uri = xenonRestClient.getServiceUri(documentSelfLink);
      ServiceErrorResponse serviceErrorResponse = new ServiceErrorResponse();
      serviceErrorResponse.statusCode = Operation.STATUS_CODE_TIMEOUT;
      serviceErrorResponse.message = UUID.randomUUID().toString();

      Operation getOperation = Operation
          .createGet(uri)
          .setUri(uri)
          .setExpiration(1)
          .setReferer(OperationUtils.getLocalHostUri())
          .setStatusCode(Operation.STATUS_CODE_TIMEOUT)
          .setBody(serviceErrorResponse);

      Operation completedOperation = Operation
          .createGet(uri)
          .setUri(uri)
          .setExpiration(1)
          .setReferer(OperationUtils.getLocalHostUri())
          .setStatusCode(Operation.STATUS_CODE_TIMEOUT)
          .setBody(serviceErrorResponse);

      OperationLatch operationLatch = spy(new OperationLatch(getOperation));
      doReturn(completedOperation).when(operationLatch).getCompletedOperation();
      doReturn(operationLatch).when(xenonRestClient).createOperationLatch(any(Operation.class));

      try {
        xenonRestClient.send(getOperation);
        Assert.fail("send should have thrown TimeoutException");
      } catch (TimeoutException e) {
        assertThat(e.getMessage(), containsString(serviceErrorResponse.message));
      }
    }
  }

  /**
   * Tests for the postToBroadcastQueryService operation.
   */
  public class PostToBroadcastQueryServiceTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      xenonRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test
    public void testQueryOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName("name")
          .setTermMatchValue(exampleServiceState.name);

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(nameClause);
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = xenonRestClient.postToBroadcastQueryService(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(result);
      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.iterator().next(), is(equalTo(documentSelfLink)));

      List<ExampleService.ExampleServiceState> results =
          QueryTaskUtils.getBroadcastQueryDocuments(
              ExampleService.ExampleServiceState.class, result);
      assertThat(results.size(), is(1));
      assertThat(results.get(0).documentSelfLink, is(equalTo(documentSelfLink)));
      assertThat(results.get(0).name, is(equalTo(exampleServiceState.name)));
    }

    @Test
    public void testQueryWhenNoDocumentsExist() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query = kindClause;
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = xenonRestClient.postToBroadcastQueryService(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(result);
      assertThat(documentLinks.size(), is(0));
    }
  }

  /**
   * Tests for the patch operation.
   */
  public class PatchTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      xenonRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test
    public void testPatchOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();
      exampleServiceState.counter = 0L;

      String documentSelfLink = createDocument(exampleServiceState);

      //patch name only
      ExampleService.ExampleServiceState patchExampleServiceState = new ExampleService.ExampleServiceState();
      patchExampleServiceState.name = UUID.randomUUID().toString();

      Operation result = xenonRestClient.patch(documentSelfLink, patchExampleServiceState);

      assertThat(result.getStatusCode(), is(200));

      ExampleService.ExampleServiceState savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(exampleServiceState.counter));

      result = xenonRestClient.get(documentSelfLink);
      assertThat(result.getStatusCode(), is(200));

      savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(exampleServiceState.counter));

      //patch counter only
      ExampleService.ExampleServiceState patchExampleServiceState2 = new ExampleService.ExampleServiceState();
      patchExampleServiceState2.counter = 1L;

      result = xenonRestClient.patch(documentSelfLink, patchExampleServiceState2);

      assertThat(result.getStatusCode(), is(200));

      savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(patchExampleServiceState2.counter));

      result = xenonRestClient.get(documentSelfLink);
      assertThat(result.getStatusCode(), is(200));

      savedState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(savedState.name, is(equalTo(patchExampleServiceState.name)));
      assertThat(savedState.counter, is(patchExampleServiceState2.counter));
    }

    @Test(expectedExceptions = DocumentNotFoundException.class)
    public void testPatchOfNonExistingDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();
      exampleServiceState.counter = 0L;

      xenonRestClient.patch(
          ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString(),
          exampleServiceState);
    }
  }

  /**
   * Tests for the queryDocuments operation.
   */
  public class QueryDocumentsTest {
    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      setUpHostAndClient();
      xenonRestClient.start();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test
    public void testQueryOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      List<ExampleService.ExampleServiceState> documentList = xenonRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentList.size(), is(1));
      assertThat(documentList.get(0).name, is(equalTo(exampleServiceState.name)));
      assertThat(documentList.get(0).documentSelfLink, is(equalTo(documentSelfLink)));

      List<String> documentLinks = xenonRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.get(0), is(equalTo(documentSelfLink)));

      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<String, String>();

      documentList = xenonRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentList.size(), is(1));
      assertThat(documentList.get(0).name, is(equalTo(exampleServiceState.name)));
      assertThat(documentList.get(0).documentSelfLink, is(equalTo(documentSelfLink)));

      xenonRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.get(0), is(equalTo(documentSelfLink)));

      termsBuilder.put("name", exampleServiceState.name);

      documentList = xenonRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentList.size(), is(1));
      assertThat(documentList.get(0).name, is(equalTo(exampleServiceState.name)));
      assertThat(documentList.get(0).documentSelfLink, is(equalTo(documentSelfLink)));

      xenonRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.get(0), is(equalTo(documentSelfLink)));
    }

    @Test
    public void testQueryOfMultipleCreatedDocuments() throws Throwable {
      Map<String, ExampleService.ExampleServiceState> exampleServiceStateMap = new HashMap<>();
      for (int i = 0; i < 5; i++) {
        ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
        exampleServiceState.name = UUID.randomUUID().toString();
        String documentSelfLink = createDocument(exampleServiceState);

        exampleServiceStateMap.put(documentSelfLink, exampleServiceState);
      }

      List<ExampleService.ExampleServiceState> documentList = xenonRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentList.size(), is(5));

      List<String> documentLinks = xenonRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);

      assertThat(documentLinks.size(), is(5));

      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<String, String>();

      documentList = xenonRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentList.size(), is(5));

      documentLinks = xenonRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, termsBuilder.build());

      assertThat(documentLinks.size(), is(5));

      for (Map.Entry<String, ExampleService.ExampleServiceState> entry : exampleServiceStateMap.entrySet()) {
        termsBuilder = new ImmutableMap.Builder<String, String>();
        termsBuilder.put("name", entry.getValue().name);

        documentList = xenonRestClient.queryDocuments(
            ExampleService.ExampleServiceState.class, termsBuilder.build());

        assertThat(documentList.size(), is(1));
        assertThat(documentList.get(0).name, is(equalTo(entry.getValue().name)));
        assertThat(documentList.get(0).documentSelfLink, is(equalTo(entry.getKey())));

        documentLinks = xenonRestClient.queryDocumentsForLinks(
            ExampleService.ExampleServiceState.class, termsBuilder.build());

        assertThat(documentLinks.size(), is(1));
        assertThat(documentLinks.get(0), is(equalTo(entry.getKey())));
      }
    }

    @Test
    public void testQueryWhenNoDocumentsExist() throws Throwable {
      List<ExampleService.ExampleServiceState> documentList = xenonRestClient.queryDocuments(
          ExampleService.ExampleServiceState.class, null);
      assertThat(documentList.size(), is(0));
      Collection<String> documentLinks = xenonRestClient.queryDocumentsForLinks(
          ExampleService.ExampleServiceState.class, null);
      assertThat(documentLinks.size(), is(0));
    }

    @Test(dataProvider = "QueryOfCreateDocuments")
    public void testQueryOfCreatedDocuments(boolean broadCast) throws Throwable {
      final int numDocuments = 100;
      final int pageSize = 30;

      doReturn(1L).when(xenonRestClient).getServiceDocumentStatusCheckIntervalMillis();

      checkNoDocumentsRetrieved(Optional.<Integer>absent(), broadCast);
      checkNoDocumentsRetrieved(Optional.of(pageSize), broadCast);

      Map<String, ExampleService.ExampleServiceState> exampleServiceStateMap = new HashMap<>();
      for (int i = 0; i < numDocuments; i++) {
        ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
        exampleServiceState.name = UUID.randomUUID().toString();
        String documentSelfLink = createDocument(exampleServiceState);

        exampleServiceStateMap.put(documentSelfLink, exampleServiceState);
      }

      checkBroadcastQueryParams();

      checkDocumentsRetrievedInAll(
          numDocuments, null, true, broadCast, exampleServiceStateMap.values());
      checkDocumentsRetrievedPageByPage(
          numDocuments, pageSize, null, true, broadCast, exampleServiceStateMap.values());

      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      checkDocumentsRetrievedInAll(
          numDocuments, termsBuilder.build(), true, broadCast, exampleServiceStateMap.values());
      checkDocumentsRetrievedPageByPage(
          numDocuments, pageSize, termsBuilder.build(), true, broadCast, exampleServiceStateMap.values());

      for (Map.Entry<String, ExampleService.ExampleServiceState> entry : exampleServiceStateMap.entrySet()) {
        termsBuilder = new ImmutableMap.Builder<>();
        termsBuilder.put("name", entry.getValue().name);

        checkDocumentsRetrievedInAll(
            1, termsBuilder.build(), true, broadCast, ImmutableSet.of(entry.getValue()));
        checkDocumentsRetrievedPageByPage(
            1, pageSize, termsBuilder.build(), true, broadCast, ImmutableSet.of(entry.getValue()));
      }
    }

    @DataProvider(name = "QueryOfCreateDocuments")
    Object[][] queryOfCreateDocumentsParams() {
      return new Object[][] {
          {false},
          {true}
      };
    }

    private void checkBroadcastQueryParams() throws Throwable {
      try {
        xenonRestClient.queryDocuments(null, null, null, true);
        fail("Should have failed due to null DocumentType");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Cannot query documents with null documentType"));
      }

      try {
        xenonRestClient.queryDocuments(ExampleService.ExampleServiceState.class, null, Optional.of(0), true);
        fail("Should have failed due to illegal document page size");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Cannot query documents with a page size less than 1"));
      }

      try {
        xenonRestClient.queryDocumentPage(null);
        fail("Should have failed due to null page link");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("Cannot query documents with null pageLink"));
      }

      try {
        xenonRestClient.queryDocumentPage("");
        fail("Should have failed due to empty page link");
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Cannot query documents with empty pageLink"));
      }
    }

    private void setUpHostAndClient() throws Throwable {
      host = BasicServiceHost.create();
      host.startServiceSynchronously(ExampleService.createFactory(), null, ExampleService.FACTORY_LINK);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      xenonRestClient = spy(new XenonRestClient(serverSet, Executors.newFixedThreadPool(1)));
    }

    private String createDocument(ExampleService.ExampleServiceState exampleServiceState) throws Throwable {
      Operation result = xenonRestClient.post(ExampleService.FACTORY_LINK, exampleServiceState);

      assertThat(result.getStatusCode(), is(200));
      ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));
      return createdState.documentSelfLink;
    }
  }


  /**
   * Tests with multiple hosts.
   */
  public class MultiHostTest {

    private BasicServiceHost[] hosts;
    private XenonRestClient[] xenonRestClients;

    @BeforeMethod
    public void setUp() throws Throwable {
      hosts = setUpMultipleHosts(5);
      xenonRestClients = setupXenonRestClients(hosts);
      doReturn(TimeUnit.SECONDS.toMicros(5)).when(xenonRestClient).getGetOperationExpirationMicros();
      doReturn(TimeUnit.SECONDS.toMicros(5)).when(xenonRestClient).getQueryOperationExpirationMicros();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (hosts != null) {
        for (BasicServiceHost host : hosts) {
          host.destroy();
        }
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }

      if (xenonRestClients != null) {
        for (XenonRestClient xenonRestClient : xenonRestClients) {
          xenonRestClient.stop();
        }
      }
    }

    @Test
    public void testGetOfNonExistingDocument() throws Throwable {
      String documentSelfLink = null;
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (XenonRestClient xenonRestClient : xenonRestClients) {
          try {
            documentSelfLink = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();
            xenonRestClient.get(documentSelfLink);
            Assert.fail("get for a non-existing document should have failed");
          } catch (DocumentNotFoundException e) {
            assertThat(e.getMessage(), containsString(documentSelfLink));
          }
        }
      }
    }

    @Test
    public void testPatchOfNonExistingDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      String documentSelfLink = null;
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (XenonRestClient xenonRestClient : xenonRestClients) {
          try {
            exampleServiceState.name = UUID.randomUUID().toString();
            documentSelfLink = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();
            xenonRestClient.patch(documentSelfLink, exampleServiceState);
            Assert.fail("patch for a non-existing document should have failed");
          } catch (DocumentNotFoundException e) {
            assertThat(e.getMessage(), containsString(documentSelfLink));
          }
        }
      }
    }

    @Test
    public void testDeleteOfNonExistingDocument() throws Throwable {
      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (XenonRestClient xenonRestClient : xenonRestClients) {
          String documentSelfLink = ExampleService.FACTORY_LINK + "/" + UUID.randomUUID().toString();
          xenonRestClient.delete(documentSelfLink, null);
        }
      }
    }

    @Test
    public void testGetOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState[] exampleServiceStates =
          new ExampleService.ExampleServiceState[xenonRestClients.length];
      String[] documentSelfLinks = new String[xenonRestClients.length];

      for (int j = 0; j < xenonRestClients.length; j++) {
        exampleServiceStates[j] = new ExampleService.ExampleServiceState();
        exampleServiceStates[j].name = UUID.randomUUID().toString();
        documentSelfLinks[j] = createDocument(xenonRestClients[j], exampleServiceStates[j]);
      }

      for (int i = 0; i < MAX_ITERATIONS; i++) {
        for (int j = 0; j < xenonRestClients.length; j++) {
          Operation result = xenonRestClient.get(documentSelfLinks[j]);
          ExampleService.ExampleServiceState savedState = result.getBody(ExampleService.ExampleServiceState.class);
          assertThat(savedState.name, is(equalTo(exampleServiceStates[j].name)));
          for (int k = 0; k < xenonRestClients.length; k++) {
            result = xenonRestClients[k].get(documentSelfLinks[j]);
            savedState = result.getBody(ExampleService.ExampleServiceState.class);
            assertThat(savedState.name, is(equalTo(exampleServiceStates[j].name)));
          }
        }
      }
    }

    @Test
    public void testQueryOfCreatedDocument() throws Throwable {
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(exampleServiceState);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName("name")
          .setTermMatchValue(exampleServiceState.name);

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(nameClause);

      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = xenonRestClient.postToBroadcastQueryService(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(result);
      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.iterator().next(), is(equalTo(documentSelfLink)));

      List<ExampleService.ExampleServiceState> results =
          QueryTaskUtils.getBroadcastQueryDocuments(
              ExampleService.ExampleServiceState.class, result);
      assertThat(results.size(), is(1));
      assertThat(results.get(0).documentSelfLink, is(equalTo(documentSelfLink)));
      assertThat(results.get(0).name, is(equalTo(exampleServiceState.name)));

      result = xenonRestClient.get(results.get(0).documentSelfLink);
      ExampleService.ExampleServiceState document = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(results.get(0).documentSelfLink, is(equalTo(document.documentSelfLink)));
    }

    @Test
    public void testQueryOfCreatedDocumentWithDifferentHosts() throws Throwable {

      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      String documentSelfLink = createDocument(xenonRestClients[0], exampleServiceState);

      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName("name")
          .setTermMatchValue(exampleServiceState.name);

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(nameClause);

      Operation result = xenonRestClients[1].postToBroadcastQueryService(spec);
      assertThat(result.getStatusCode(), is(200));

      Collection<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(result);
      assertThat(documentLinks.size(), is(1));
      assertThat(documentLinks.iterator().next(), is(equalTo(documentSelfLink)));
    }

    @Test
    public void testQueryWhenNoDocumentsExist() throws Throwable {
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query = kindClause;
      spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);

      Operation result = xenonRestClient.postToBroadcastQueryService(spec);
      assertThat(result.getStatusCode(), is(200));

      QueryTask queryResult = result.getBody(QueryTask.class);
      assertThat(queryResult, is(notNullValue()));
      assertThat(queryResult.results, is(nullValue()));
    }

    @Test(dataProvider = "QueryOfCreateDocuments")
    public void testQueryOfCreatedDocuments(boolean broadCast) throws Throwable {
      final int numDocuments = 100;
      final int pageSize = 30;

      checkNoDocumentsRetrieved(Optional.<Integer>absent(), broadCast);
      checkNoDocumentsRetrieved(Optional.of(pageSize), broadCast);

      Map<String, ExampleService.ExampleServiceState> exampleServiceStateMap = new HashMap<>();
      for (int i = 0; i < numDocuments; i++) {
        ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
        exampleServiceState.name = UUID.randomUUID().toString();
        String documentSelfLink = createDocument(exampleServiceState);

        exampleServiceStateMap.put(documentSelfLink, exampleServiceState);
      }

      checkDocumentsRetrievedInAll(numDocuments, null, true, broadCast, exampleServiceStateMap.values());
      checkDocumentsRetrievedPageByPage(
          numDocuments, pageSize, null, true, broadCast, exampleServiceStateMap.values());

      ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
      checkDocumentsRetrievedInAll(
          numDocuments, termsBuilder.build(), true, broadCast, exampleServiceStateMap.values());
      checkDocumentsRetrievedPageByPage(
          numDocuments, pageSize, termsBuilder.build(), true, broadCast, exampleServiceStateMap.values());

      for (Map.Entry<String, ExampleService.ExampleServiceState> entry : exampleServiceStateMap.entrySet()) {
        termsBuilder = new ImmutableMap.Builder<>();
        termsBuilder.put("name", entry.getValue().name);

        checkDocumentsRetrievedInAll(
            1, termsBuilder.build(), true, broadCast, ImmutableSet.of(entry.getValue()));
        checkDocumentsRetrievedPageByPage(
            1, pageSize, termsBuilder.build(), true, broadCast, ImmutableSet.of(entry.getValue()));
      }
    }

    @DataProvider(name = "QueryOfCreateDocuments")
    Object[][] queryOfCreateDocumentsParams() {
      return new Object[][] {
          {false},
          {true}
      };
    }
  }

  private void checkNoDocumentsRetrieved(Optional<Integer> pageSize, boolean broadCast) throws Throwable {
    ServiceDocumentQueryResult queryResult = xenonRestClient.queryDocuments(ExampleService.ExampleServiceState.class,
        null, pageSize, false, broadCast);

    assertThat(queryResult.documentCount, is(0L));
    assertNull(queryResult.nextPageLink);
    assertNull(queryResult.prevPageLink);
  }

  private void checkDocumentsRetrievedInAll(int numDocuments,
                                            ImmutableMap<String, String> queryTerms,
                                            boolean expandContent,
                                            boolean broadCast,
                                            Collection<ExampleService.ExampleServiceState> expectedDocuments)
      throws Throwable {

    Integer iterations = 60;
    if (broadCast) {
      iterations = 1;
    }

    ServiceDocumentQueryResult queryResult = null;
    Set<String> expectedDocumentNames = null;
    Set<String> actualDocumentNames = null;

    for (int i = 0; i < iterations; i++) {
      queryResult = xenonRestClient.queryDocuments
          (ExampleService.ExampleServiceState.class, queryTerms, Optional.absent(), expandContent, broadCast);

      expectedDocumentNames = expectedDocuments.stream()
          .map(d -> d.name)
          .collect(Collectors.toSet());
      actualDocumentNames = queryResult.documents.values().stream()
          .map(d -> Utils.fromJson(d, ExampleService.ExampleServiceState.class).name)
          .collect(Collectors.toSet());

      if (queryResult.documentLinks.size() == numDocuments) {
        break;
      }

      Thread.sleep(1000L);
    }

    assertThat(queryResult.documentLinks.size(), is(numDocuments));
    assertThat(queryResult.documents.size(), is(numDocuments));
    assertThat(CollectionUtils.isEqualCollection(expectedDocumentNames, actualDocumentNames), is(true));
    assertNull(queryResult.nextPageLink);
    assertNull(queryResult.prevPageLink);
  }

  private void checkDocumentsRetrievedPageByPage(int numDocuments,
                                                 int pageSize,
                                                 ImmutableMap<String, String> queryTerms,
                                                 boolean expandContent,
                                                 boolean broadCast,
                                                 Collection<ExampleService.ExampleServiceState> expectedDocuments)
      throws Throwable {

    Integer iterations = 60;
    if (broadCast) {
      iterations = 1;
    }

    Set<String> expectedDocumentNames = null;
    Set<String> actualDocumentNames = null;

    for (int i = 0; i < iterations; i++) {
      ServiceDocumentQueryResult queryResult = xenonRestClient.queryDocuments
          (ExampleService.ExampleServiceState.class, queryTerms, Optional.of(pageSize), expandContent, broadCast);

      assertNotNull(queryResult.documents);
      assertNull(queryResult.prevPageLink);

      actualDocumentNames = new HashSet<>();
      actualDocumentNames.addAll(queryResult.documents.values().stream()
              .map(d -> Utils.fromJson(d, ExampleService.ExampleServiceState.class).name)
              .collect(Collectors.toSet())
      );

      expectedDocumentNames = expectedDocuments.stream()
          .map(d -> d.name)
          .collect(Collectors.toSet());

      while (queryResult.nextPageLink != null) {
        queryResult = xenonRestClient.queryDocumentPage(queryResult.nextPageLink);

        actualDocumentNames.addAll(queryResult.documents.values().stream()
                .map(d -> Utils.fromJson(d, ExampleService.ExampleServiceState.class).name)
                .collect(Collectors.toSet())
        );
      }

      if (actualDocumentNames.size() == numDocuments) {
        break;
      }

      Thread.sleep(1000L);
    }

    assertThat(actualDocumentNames.size(), is(numDocuments));
    assertThat(CollectionUtils.isEqualCollection(expectedDocumentNames, actualDocumentNames), is(true));
  }

  /**
   * Tests helper methods in XenonRestClient.
   */
  public class HelperMethodTest {

    @Test
    public void testGetServiceUri() throws Throwable {
      List<String> localIpAddresses = OperationUtils.getLocalHostIpAddresses();

      InetSocketAddress[] servers1 = new InetSocketAddress[3];
      servers1[0] = new InetSocketAddress("0.0.0.1", 1);
      servers1[1] = new InetSocketAddress("0.0.0.2", 2);
      servers1[2] = new InetSocketAddress("0.0.0.3", 3);

      StaticServerSet staticServerSet = new StaticServerSet(servers1);
      XenonRestClient testXenonRestClient = new XenonRestClient(staticServerSet, Executors.newFixedThreadPool(1));
      final URI result1 = testXenonRestClient.getServiceUri("/dummyPath");
      String result1Address = result1.getHost();
      if (result1Address.startsWith("[")) {
        // this is an Ipv6 address to which surrounding square brackets have been added by URI.getHost() method
        // since we use this method only in test code we need to undo this side effect for the test validations to work
        result1Address = result1Address.substring(1, result1Address.length() - 1);
      }

      String[] hosts1 = Stream.of(servers1).map((a) -> a.getAddress().getHostAddress()).toArray(String[]::new);
      assertThat(hosts1, hasItemInArray(result1Address));

      InetSocketAddress[] servers2 = new InetSocketAddress[localIpAddresses.size() + servers1.length];
      servers2[0] = servers1[0];
      servers2[1] = servers1[1];
      servers2[2] = servers1[2];
      for (int i = servers1.length; i < servers1.length + localIpAddresses.size(); i++) {
        servers2[i] = new InetSocketAddress(localIpAddresses.get(i - servers1.length), i);
      }

      staticServerSet = new StaticServerSet(servers2);
      testXenonRestClient = new XenonRestClient(staticServerSet, Executors.newFixedThreadPool(1));
      final URI result2 = testXenonRestClient.getServiceUri("/dummyPath");
      String result2Address = result2.getHost();
      if (result2Address.startsWith("[")) {
        // this is an Ipv6 address to which surrounding square brackets have been added by URI.getHost() method
        // since we use this method only in test code we need to undo this side effect for the test validations to work
        result2Address = result2Address.substring(1, result2Address.length() - 1);
      }
      String[] hosts2 = Stream.of(servers2).map((a) -> a.getAddress().getHostAddress()).toArray(String[]::new);

      //the selected URI should not be from the servers1 list as they do not match local address
      assertThat(hosts1, not(hasItemInArray(result2Address)));

      //the selected URI should be from servers2
      assertThat(hosts2, hasItemInArray(result2Address));

      //the selected URI should be using local address
      assertThat(localIpAddresses, hasItem(result2Address));
    }
  }

  /**
   * Tests broadcastPutObject method.
   */
  public class BroadcastPutObjectTest {
    private BasicServiceHost[] hosts;

    @BeforeMethod
    public void setUp() throws Throwable {
      hosts = setUpMultipleHosts(3);
      for (BasicServiceHost host : hosts) {
        host.startServiceSynchronously(new LoggerControlService(), null, LoggerControlService.SELF_LINK);
      }
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (hosts != null) {
        for (BasicServiceHost host : hosts) {
          host.destroy();
        }
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test
    public void testBroadcastPutObject() throws Throwable {
      String loggerPath = "com.vmware.photon.controller.common.xenon.XenonRestClientTest";
      Map<String, String> loggerMap = new HashMap<>();
      loggerMap.put(loggerPath, "trace");
      Operation result = xenonRestClient.broadcastPutObject(LoggerControlService.SELF_LINK, loggerMap);
      assertThat(result.getStatusCode(), is(Operation.STATUS_CODE_OK));
      for (BasicServiceHost host : hosts) {
        Operation operation = host.sendRequestAndWait(
            new Operation().createGet(xenonRestClient.getServiceUri(LoggerControlService.SELF_LINK)));
        Map loggerInfo = operation.getBody(Map.class);
        assertThat(loggerInfo.get(loggerPath), is("TRACE"));
      }

      loggerMap.clear();
      loggerMap.put(loggerPath, "info");
      xenonRestClient.broadcastPutObject(LoggerControlService.SELF_LINK, loggerMap);
    }
  }

  /**
   * Tests asymmetric replication with multiple hosts.
   */
  public class ReplicationTest {

    private BasicServiceHost[] hosts;

    @DataProvider(name = "hostCountProvider")
    private Object[][] getHostCount() {
      return new Object[][]{
          {1},
          {2},
          {3},
          {4},
          {5},
      };
    }

    @BeforeMethod
    public void setUp(Object[] testArgs) throws Throwable {
      hosts = setUpMultipleHosts((Integer) testArgs[0]);
      for (BasicServiceHost host : hosts) {
        host.startServiceSynchronously(new LimitedReplicationExampleFactoryService(), null,
            LimitedReplicationExampleFactoryService.SELF_LINK);
      }
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (hosts != null) {
        for (BasicServiceHost host : hosts) {
          host.destroy();
        }
      }

      if (xenonRestClient != null) {
        xenonRestClient.stop();
      }
    }

    @Test(dataProvider = "hostCountProvider")
    public void test3xReplication(final Integer hostCount) throws Throwable {
      final Integer replicationFactor = 3;
      final String exampleServiceStateName = UUID.randomUUID().toString();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = exampleServiceStateName;

      Operation result = xenonRestClient.post(LimitedReplicationExampleFactoryService.SELF_LINK, exampleServiceState);
      assertThat(result.getStatusCode(), is(200));
      ExampleService.ExampleServiceState createdState = result.getBody(ExampleService.ExampleServiceState.class);
      assertThat(createdState.name, is(equalTo(exampleServiceState.name)));

      final CountDownLatch countDownLatch = new CountDownLatch(1);
      final Integer expectedLinkCount = Math.min(hostCount, replicationFactor);
      Thread t = new Thread(() -> {
        Integer actualLinkCount = 0;
        while (!Thread.currentThread().isInterrupted()) {
          try {
            actualLinkCount = getActualLinkCount(exampleServiceStateName);
            if ((actualLinkCount == expectedLinkCount)) {
              countDownLatch.countDown();
              break;
            }
          } catch (Throwable e) {
            break;
          }
        }
      });

      t.start();

      Long timeoutMillis = TimeUnit.SECONDS.toMillis(10);
      Boolean linkCountMatchFound = countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
      if (!linkCountMatchFound) {
        t.interrupt();
        Assert.fail("Failed to find correct number of replicated links within specified timeout (Millis): "
            + timeoutMillis);
      }

      Integer actualLinkCount = getActualLinkCount(exampleServiceState.name);
      assertThat(actualLinkCount, is(expectedLinkCount));
    }

    private Integer getActualLinkCount(String exampleServiceStateName) throws Throwable {
      Integer actualLinkCount = 0;
      QueryTask.Query kindClause = new QueryTask.Query()
          .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
          .setTermMatchValue(Utils.buildKind(ExampleService.ExampleServiceState.class));

      QueryTask.Query nameClause = new QueryTask.Query()
          .setTermPropertyName("name")
          .setTermMatchValue(exampleServiceStateName);

      QueryTask.QuerySpecification spec = new QueryTask.QuerySpecification();
      spec.query.addBooleanClause(kindClause);
      spec.query.addBooleanClause(nameClause);

      Operation queryResult = xenonRestClient.postToBroadcastQueryService(spec);
      assertThat(queryResult.getStatusCode(), is(200));

      NodeGroupBroadcastResponse queryResponse = queryResult.getBody(NodeGroupBroadcastResponse.class);
      assertThat(queryResponse.failures.isEmpty(), is(true));

      for (Map.Entry<URI, String> entry : queryResponse.jsonResponses.entrySet()) {
        QueryTask queryTask = Utils.fromJson(entry.getValue(), QueryTask.class);
        if (null != queryTask.results
            && null != queryTask.results.documentLinks
            && queryTask.results.documentLinks.size() > 0) {
          actualLinkCount += queryTask.results.documentLinks.size();
        }
      }
      return actualLinkCount;
    }
  }
}
