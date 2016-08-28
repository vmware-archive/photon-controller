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

import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithSelfLink;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStage;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithStageFactory;
import com.vmware.photon.controller.common.xenon.helpers.services.TestServiceWithoutSelfLink;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.NodeGroupService;
import com.vmware.xenon.services.common.NodeState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.security.InvalidParameterException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Tests {@link ServiceHostUtils}.
 */
public class ServiceHostUtilsTest {

  private static final long SLEEP_TIME_MILLIS = 1;
  private static final long MAX_ITERATIONS = 60000;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests the waitForNodeGroupConvergence method.
   */
  public class WaitForNodeGroupConvergenceTest {

    private BasicServiceHost[] hosts;

    @AfterMethod
    public void tearDown() throws Throwable {
      if (hosts != null) {
        for (BasicServiceHost host : hosts) {
          if (host == null) {
            continue;
          }

          BasicServiceHost.destroy(host);
        }
      }

      hosts = null;
    }

    /**
     * Test that exception is raised when hosts param is invalid.
     *
     * @param hosts
     * @param expectedError
     */
    @Test(dataProvider = "InvalidHostParam")
    public void testInvalidHostsParam(
        ServiceHost[] hosts,
        String expectedError
    ) throws Throwable {
      try {
        ServiceHostUtils.waitForNodeGroupConvergence(hosts, "", 1, 20);
        fail("did not validate that hosts param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is(expectedError));
      }
    }

    @DataProvider(name = "InvalidHostParam")
    public Object[][] getInvalidHostParamData() {
      return new Object[][]{
          {null, "hosts cannot be null"},
          {new ServiceHost[0], "hosts cannot be empty"}
      };
    }

    /**
     * Test that exception is raised when nodeGroupPath param is invalid.
     *
     * @param nodeGroupPath
     * @param expectedError
     */
    @Test(dataProvider = "InvalidNodeGroupPathParam")
    public void testInvalidNodeGroupPathParam(
        String nodeGroupPath,
        String expectedError
    ) throws Throwable {
      try {
        ServiceHostUtils.waitForNodeGroupConvergence(new ServiceHost[1], nodeGroupPath, 1, 20);
        fail("did not validate that nodeGroupPath param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is(expectedError));
      }
    }

    @DataProvider(name = "InvalidNodeGroupPathParam")
    public Object[][] getInvalidNodeGroupPathParamData() {
      return new Object[][]{
          {null, "nodeGroupPath cannot be null or empty"},
          {"", "nodeGroupPath cannot be null or empty"}
      };
    }

    /**
     * Test that exception is raised when maxRetries param is invalid.
     *
     * @param maxRetries
     * @param expectedError
     */
    @Test(dataProvider = "InvalidMaxRetriesParam")
    public void testInvalidMaxRetriesParam(
        Integer maxRetries,
        String expectedError
    ) throws Throwable {
      try {
        ServiceHostUtils.waitForNodeGroupConvergence(new ServiceHost[1], "/tmp", maxRetries, 20);
        fail("did not validate that maxRetries param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is(expectedError));
      }
    }

    @DataProvider(name = "InvalidMaxRetriesParam")
    public Object[][] getInvalidMaxRetriesParamData() {
      return new Object[][]{
          {-10, "maxRetries must be > 0"},
          {0, "maxRetries must be > 0"}
      };
    }

    /**
     * Tests that method waits for convergence.
     *
     * @param hostCount
     */
    @Test(dataProvider = "HostCount")
    public void testSuccess(
        int hostCount
    ) throws Throwable {
      hosts = buildHosts(hostCount, ServiceUriPaths.DEFAULT_NODE_GROUP, true);
      ServiceHostUtils.waitForNodeGroupConvergence(hosts, ServiceUriPaths.DEFAULT_NODE_GROUP, 500, 20);

      // check the final state
      for (ServiceHost host : hosts) {
        NodeGroupService.NodeGroupState response = ServiceHostUtils.getNodeGroupState(host,
            ServiceUriPaths.DEFAULT_NODE_GROUP);

        assertThat(response, notNullValue());
        assertThat(response.nodes.size(), is(hostCount));
        for (NodeState nodeState : response.nodes.values()) {
          assertThat(nodeState.status, is(NodeState.NodeStatus.AVAILABLE));
        }
      }
    }

    @DataProvider(name = "HostCount")
    public Object[][] getHostCountData() {
      return new Object[][]{
          {1},
          {3}
      };
    }

    /**
     * Test that TimeoutExcpetion is raised when nodes do not converge in time.
     *
     * @throws Throwable
     */
    @Test
    public void testTimeout() throws Throwable {
      hosts = buildHosts(3, ServiceUriPaths.DEFAULT_NODE_GROUP, false);

      try {
        ServiceHostUtils.waitForNodeGroupConvergence(hosts, ServiceUriPaths.DEFAULT_NODE_GROUP, 10, 20);
        fail("did not throw exception when waited for convergence");
      } catch (IllegalStateException ex) {
        assertThat(ex.getMessage(), is("Update time did not converge"));
      }
    }

    private BasicServiceHost[] buildHosts(int count, String nodeGroupPath, boolean join) throws Throwable {
      BasicServiceHost[] hosts = new BasicServiceHost[count];
      for (int i = 0; i < count; i++) {
        hosts[i] = BasicServiceHost.create();
        if (i > 0 && join) {
          // join the previous host we created
          hosts[i].joinPeers(
              ImmutableList.of(UriUtils.buildUri(hosts[i - 1], "")), nodeGroupPath);
        }
      }

      return hosts;
    }
  }

  /**
   * Tests for the getNodeGroupState method.
   */
  public class GetNodeGroupStateTest {

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = BasicServiceHost.create();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      host = null;
    }

    /**
     * Test that exception is raised when host param is invalid.
     */
    @Test
    public void testInvalidHostsParam() throws Throwable {
      try {
        ServiceHostUtils.getNodeGroupState(null, "/path");
        fail("did not validate that hosts param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is("host cannot be null"));
      }
    }

    /**
     * Test that exception is raised when nodeGroupPath param is invalid.
     *
     * @param nodeGroupPath
     * @param expectedError
     */
    @Test(dataProvider = "InvalidNodeGroupPathParam")
    public void testInvalidNodeGroupPathParam(
        String nodeGroupPath,
        String expectedError
    ) throws Throwable {
      try {
        ServiceHostUtils.getNodeGroupState(host, nodeGroupPath);
        fail("did not validate that nodeGroupPath param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is(expectedError));
      }
    }

    @DataProvider(name = "InvalidNodeGroupPathParam")
    public Object[][] getInvalidNodeGroupPathParamData() {
      return new Object[][]{
          {null, "nodeGroupPath cannot be null or empty"},
          {"", "nodeGroupPath cannot be null or empty"}
      };
    }

    /**
     * Tests that method retrieves the state.
     */
    @Test
    public void testSuccess() throws Throwable {
      host = BasicServiceHost.create();
      NodeGroupService.NodeGroupState response =
          ServiceHostUtils.getNodeGroupState(host, ServiceUriPaths.DEFAULT_NODE_GROUP);

      assertThat(response, notNullValue());
      assertThat(response.nodes.size(), is(1));
      for (NodeState nodeState : response.nodes.values()) {
        assertThat(nodeState.status, notNullValue());
      }
    }
  }

  /**
   * Tests for the waitForServiceAvailability method.
   */
  public class WaitForServiceAvailabilityTest {
    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = spy(BasicServiceHost.create());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      host = null;
    }

    /**
     * Test timeout due to service not being available.
     *
     * @throws Throwable
     */
    @Test
    public void testTimeout() throws Throwable {
      doNothing().when(host).registerForServiceAvailability(any(Operation.CompletionHandler.class), anyString());

      try {
        ServiceHostUtils.waitForServiceAvailability(host, 10, "/test");
        fail("TimeoutException was not thrown when service did not start.");
      } catch (TimeoutException ex) {
        assertThat("Timeout exception did not match!", ex.getMessage(), is("One or several of service(s) [\"/test\"] " +
            "not available"));
      }
    }

    /**
     * Test failure when host method throws an exception.
     *
     * @throws Throwable
     */
    @Test
    public void testErrorSingle() throws Throwable {
      doAnswer(buildRegisterForServiceAvailabilityAnswer(new Exception("Service error")))
          .when(host).registerForServiceAvailability(any(Operation.CompletionHandler.class), anyString());

      try {
        ServiceHostUtils.waitForServiceAvailability(host, 10, "/test");
        fail("Exception was not thrown when service did not start.");
      } catch (Throwable ex) {
        assertThat(
            "Exception message did not match!", ex.getMessage(), is("Error: registerForAvailability returned errors"));
        assertThat("Count of suppressed errors did not match!", ex.getSuppressed().length, is(1));
        assertThat("First suppressed error did not match!", ex.getSuppressed()[0].getMessage(), is("Service error"));
      }
    }

    /**
     * Test failure when host method throws an exception.
     *
     * @throws Throwable
     */
    @Test
    public void testErrorMultiple() throws Throwable {
      doAnswer(buildRegisterForServiceAvailabilityAnswer(new Exception("Service error")))
          .when(host).registerForServiceAvailability(any(Operation.CompletionHandler.class), anyString(), anyString());

      try {
        ServiceHostUtils.waitForServiceAvailability(host, 10, "/test", "/test2");
        fail("Exception was not thrown when service did not start.");
      } catch (Throwable ex) {
        assertThat(
            "Exception message did not match!", ex.getMessage(), is("Error: registerForAvailability returned errors"));
        assertThat("Count of suppressed errors did not match!", ex.getSuppressed().length, is(2));
        assertThat("First suppressed error did not match!", ex.getSuppressed()[0].getMessage(), is("Service error"));
        assertThat("Second suppressed error di not match!", ex.getSuppressed()[1].getMessage(), is("Service error"));
      }
    }

    /**
     * Test successful invocation of method.
     *
     * @throws Throwable
     */
    @Test
    public void testSuccessSingle() throws Throwable {
      doAnswer(buildRegisterForServiceAvailabilityAnswer(null))
          .when(host).registerForServiceAvailability(any(Operation.CompletionHandler.class), anyString());

      ServiceHostUtils.waitForServiceAvailability(host, 1000, "/test");
      verify(host).registerForServiceAvailability(any(Operation.CompletionHandler.class), eq("/test"));
    }

    /**
     * Test successful invocation of method.
     *
     * @throws Throwable
     */
    @Test
    public void testSuccessMultiple() throws Throwable {
      doAnswer(buildRegisterForServiceAvailabilityAnswer(null))
          .when(host).registerForServiceAvailability(any(Operation.CompletionHandler.class), anyString(), anyString());

      ServiceHostUtils.waitForServiceAvailability(host, 1000, "/test", "/test2");
      verify(host).registerForServiceAvailability(any(Operation.CompletionHandler.class), eq("/test"), eq("/test2"));
    }

    private Answer buildRegisterForServiceAvailabilityAnswer(final Throwable t) {
      return new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation.CompletionHandler handler = (Operation.CompletionHandler) invocation.getArguments()[0];
          for (int i = 1; i < invocation.getArguments().length; i++) {
            handler.handle(null, t);
          }
          return null;
        }
      };
    }
  }

  /**
   * Tests for the startServices method.
   */
  public class StartServicesTest {

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = spy(BasicServiceHost.create());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
        host = null;
      }
    }

    /**
     * Test that an exception is raised when host param is invalid.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidHostParam() throws Throwable {
      try {
        ServiceHostUtils.startServices(null, new Class[1]);
        fail("did not validate that hosts param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is("host cannot be null"));
      }
    }

    /**
     * Tests that an exception is raised when services param is invalid.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidServicesParam() throws Throwable {
      try {
        ServiceHostUtils.startServices(mock(BasicServiceHost.class), (Class[]) null);
        fail("did not validate that services param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is("services cannot be null"));
      }
    }

    /**
     * Tests that if an error is trying to instantiate one service no preceding services are
     * started.
     *
     * @throws Throwable
     */
    @Test
    public void testPartialServiceListStartDueToError() throws Throwable {
      try {
        ServiceHostUtils.startServices(
            host,
            TestServiceWithStageFactory.class,
            TestServiceWithoutSelfLink.class,
            TestServiceWithSelfLink.class);
      } catch (InvalidParameterException ex) {
        assertThat(ex.getMessage(), startsWith("No SELF_LINK field"));
      }

      ServiceHostUtils.waitForServiceAvailability(host, 1000, TestServiceWithStageFactory.SELF_LINK);
      assertTrue(
          host.checkServiceAvailable(TestServiceWithStageFactory.SELF_LINK),
          "TestServiceWithStageFactory is not available!");
      assertFalse(
          host.checkServiceAvailable(TestServiceWithSelfLink.SELF_LINK),
          "TestServiceWithSelfLink is not available!");
    }

    /**
     * Tests that services starts successfully.
     */
    @Test
    public void testSuccess() throws Throwable {
      ServiceHostUtils.startServices(host, TestServiceWithStageFactory.class, TestServiceWithSelfLink.class);
      ServiceHostUtils.waitForServiceAvailability(host, 1000, TestServiceWithStageFactory.SELF_LINK);
      ServiceHostUtils.waitForServiceAvailability(host, 1000, TestServiceWithSelfLink.SELF_LINK);

      assertTrue(
          host.checkServiceAvailable(TestServiceWithStageFactory.SELF_LINK),
          "TestServiceWithStageFactory is not available!");
      assertTrue(
          host.checkServiceAvailable(TestServiceWithSelfLink.SELF_LINK),
          "TestServiceWithSelfLink is not available!");
    }
  }

  /**
   * Tests for startFactoryServices method.
   */
  public class StartFactoryServicesTest {

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = spy(BasicServiceHost.create());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
        host = null;
      }
    }

    @Test
    public void testInvalidHostParam() throws Throwable {
      try {
        ServiceHostUtils.startFactoryServices(null, ImmutableMap.of());
        fail("Should have failed due to null host param");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("host cannot be null"));
      }
    }

    @Test
    public void testInvalidFactoryServicesParam() throws Throwable {
      try {
        ServiceHostUtils.startFactoryServices(mock(BasicServiceHost.class), null);
        fail("Should have failed due to null factoryServicesMap param");
      } catch (NullPointerException e) {
        assertThat(e.getMessage(), is("factoryServicesMap cannot be null"));
      }
    }

    @Test
    public void testFactoryServicesSuccessfullyStarted() throws Throwable {
      ServiceHostUtils.startFactoryServices(host,
          ImmutableMap.of(TestServiceWithStage.class, TestServiceWithStage::createFactory));
      ServiceHostUtils.waitForServiceAvailability(host, 1000, TestServiceWithStage.FACTORY_LINK);
      assertThat(host.checkServiceAvailable(TestServiceWithStage.FACTORY_LINK), is(true));
    }
  }

  /**
   * Tests for the startService method.
   */
  public class StartServiceTest {

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = spy(BasicServiceHost.create());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      host = null;
    }

    /**
     * Test that an exception is raised when host param is invalid.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidHostParam() throws Throwable {
      try {
        ServiceHostUtils.startService(null, null);
        fail("did not validate that hosts param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is("host cannot be null"));
      }
    }

    /**
     * Tests that an exception is raised when services param is invalid.
     *
     * @throws Throwable
     */
    @Test
    public void testInvalidServiceParam() throws Throwable {
      try {
        ServiceHostUtils.startService(mock(BasicServiceHost.class), null);
        fail("did not validate that services param is not null");
      } catch (IllegalArgumentException ex) {
        assertThat(ex.getMessage(), is("service cannot be null"));
      }
    }

    /**
     * Tests that if an error is trying to instantiate one service no preceding services are
     * started.
     *
     * @throws Throwable
     */
    @Test
    public void testServiceWithoutSelfLink() throws Throwable {
      try {
        ServiceHostUtils.startService(
            host,
            TestServiceWithoutSelfLink.class);
      } catch (InvalidParameterException ex) {
        assertThat(ex.getMessage(), startsWith("No SELF_LINK field"));
      }
    }

    /**
     * Tests that error is thrown when class does not inherit from Service.
     *
     * @throws Throwable
     */
    @Test
    public void testClassThatIsNotAService() throws Throwable {
      try {
        ServiceHostUtils.startService(
            host,
            Object.class);
      } catch (ClassCastException ex) {
        assertThat(ex.getMessage(), startsWith("java.lang.Object"));
      }
    }

    /**
     * Tests that services starts successfully.
     */
    @Test
    public void testSuccessWithoutPath() throws Throwable {
      ServiceHostUtils.startService(host, TestServiceWithSelfLink.class);
      ServiceHostUtils.waitForServiceAvailability(host, 1000, TestServiceWithSelfLink.SELF_LINK);

      assertTrue(
          host.checkServiceAvailable(TestServiceWithSelfLink.SELF_LINK),
          "ExampleFactoryService is not available!");
    }

    /**
     * Tests that services starts successfully.
     */
    @Test
    public void testSuccessWithPath() throws Throwable {
      String path = "/test-path";

      ServiceHostUtils.startService(host, TestServiceWithSelfLink.class, path);
      ServiceHostUtils.waitForServiceAvailability(host, 1000, path);

      assertTrue(
          host.checkServiceAvailable(path),
          "TestServiceWithSelfLink is not available!");
    }
  }

  /**
   * Tests for the service ready method.
   */
  public class ServiceReadyTest {

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = spy(BasicServiceHost.create());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      host = null;
    }

    /**
     * Tests that service availability checks successfully.
     */
    @Test
    public void testSuccess() throws Throwable {
      ServiceHostUtils.startServices(host, TestServiceWithStageFactory.class, TestServiceWithSelfLink.class);
      ServiceHostUtils.waitForServiceAvailability(host, 1000, TestServiceWithStageFactory.SELF_LINK);
      ServiceHostUtils.waitForServiceAvailability(host, 1000, TestServiceWithSelfLink.SELF_LINK);

      assertTrue(
          ServiceHostUtils.isServiceReady(host, "SELF_LINK", TestServiceWithStageFactory.class),
          "TestServiceWithStageFactory is not available!");
      assertTrue(
          ServiceHostUtils.isServiceReady(host, "SELF_LINK", TestServiceWithSelfLink.class),
          "TestServiceWithSelfLink is not available!");
      assertTrue(
          ServiceHostUtils.areServicesReady(host, "SELF_LINK",
              TestServiceWithStageFactory.class, TestServiceWithSelfLink.class),
          "Services are not available!");
    }
  }

  /**
   * Tests for deleting services.
   */
  public class DeleteServicesTest {

    private static final String FACTORY_LINK = "/photon/example";

    private BasicServiceHost host;

    @BeforeMethod
    public void setUp() throws Throwable {
      host = spy(BasicServiceHost.create());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        BasicServiceHost.destroy(host);
      }

      host = null;
    }

    @Test
    public void testDeleteAllDocuments() throws Throwable {

      /**
       * Create some {@link ExampleService} documents using the standard factory path.
       */

      host.startServiceSynchronously(ExampleService.createFactory(), null, ExampleService.FACTORY_LINK);

      ExampleService.ExampleServiceState exampleServiceState1 = new ExampleService.ExampleServiceState();
      exampleServiceState1.name = UUID.randomUUID().toString();

      Operation createOperation = Operation.createPost(UriUtils.buildUri(host, ExampleService.FACTORY_LINK))
          .setBody(exampleServiceState1);

      Operation result = host.sendRequestAndWait(createOperation);
      String path1 = result.getBody(ExampleService.ExampleServiceState.class).documentSelfLink;

      ExampleService.ExampleServiceState savedState = host.getServiceState(ExampleService.ExampleServiceState.class,
          path1);

      assertThat(savedState.name, is(exampleServiceState1.name));

      /**
       * Create some {@link ExampleService} documents using a Photon-specific factory path.
       */

      host.startServiceSynchronously(ExampleService.createFactory(), null, FACTORY_LINK);

      ExampleService.ExampleServiceState exampleServiceState2 = new ExampleService.ExampleServiceState();
      exampleServiceState2.name = UUID.randomUUID().toString();

      createOperation = Operation.createPost(UriUtils.buildUri(host, FACTORY_LINK))
          .setBody(exampleServiceState2);

      result = host.sendRequestAndWait(createOperation);
      String path2 = result.getBody(ExampleService.ExampleServiceState.class).documentSelfLink;

      savedState = host.getServiceState(ExampleService.ExampleServiceState.class, path2);

      assertThat(savedState.name, is(exampleServiceState2.name));

      /**
       * Assert that all documents created using the Photon-specific factory path are deleted, and
       * that all documents created using the regular factory path are not deleted.
       */

      ServiceHostUtils.deleteAllDocuments(host, "test-host");

      ServiceHostUtils.waitForServiceState(
          ServiceDocumentQueryResult.class,
          FACTORY_LINK,
          (queryResult) -> queryResult.documentCount == 0,
          host,
          SLEEP_TIME_MILLIS,
          MAX_ITERATIONS,
          null);

      ServiceDocumentQueryResult queryResult = ServiceHostUtils.getServiceState(host, ServiceDocumentQueryResult.class,
          ExampleService.FACTORY_LINK, "test-host");
      assertThat(queryResult.documentCount, is(1L));

      // try again with no documents in host
      ServiceHostUtils.deleteAllDocuments(host, "test-host");

      queryResult = ServiceHostUtils.getServiceState(host, ServiceDocumentQueryResult.class,
          ExampleService.FACTORY_LINK, "test-host");
      assertThat(queryResult.documentCount, is(1L));
    }
  }
}
