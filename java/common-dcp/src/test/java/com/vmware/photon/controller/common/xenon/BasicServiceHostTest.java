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
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ExampleService;
import com.vmware.xenon.services.common.LuceneDocumentIndexService;
import com.vmware.xenon.services.common.LuceneQueryTaskFactoryService;
import com.vmware.xenon.services.common.QueryTask;

import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Tests {@link BasicServiceHost}.
 */
public class BasicServiceHostTest {

  public static final String BIND_ADDRESS = "0.0.0.0";
  public static final Integer BIND_PORT = 0;
  public static final String SERVICE_URI = ServiceUriPaths.SERVICES_ROOT + "/BasicServiceHostTest";
  public static final String STORAGE_PATH = "/tmp/dcp/BasicServiceHostTest/" + UUID.randomUUID().toString() + "/";
  public static final int WAIT_ITERATION_SLEEP = 10;
  public static final int WAIT_ITERATION_COUNT = 10;

  private static final File storageDir = new File(STORAGE_PATH);

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the host lifecycle operations - construction, initialization, start and destruction.
   */
  public class HostLifecycleTests {

    private BasicServiceHost host;

    @BeforeClass
    public void setUp() throws Throwable {
      FileUtils.deleteDirectory(storageDir);
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
        host = null;
      }

      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testInitializeWithStorageDirExisting() throws Throwable {

      host = new BasicServiceHost(BIND_ADDRESS,
          BIND_PORT,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      // make sure folder exists prior to initialization of host
      storageDir.mkdirs();
      host.initialize();
      assertThat(storageDir.exists(), is(true));
    }

    @Test
    public void testInitializeWithStorageDirNotExisting() throws Throwable {

      host = new BasicServiceHost(BIND_ADDRESS,
          BIND_PORT,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      // make sure folder does not exist prior to initialization of host
      FileUtils.deleteDirectory(storageDir);
      host.initialize();
      assertThat(storageDir.exists(), is(true));
    }

    @Test
    public void testParamsPassedToConstructor() throws Throwable {

      final Random random = new Random();
      Integer bindPort = 46000 + random.nextInt(999);

      host = new BasicServiceHost(BIND_ADDRESS,
          bindPort,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      host.initialize();
      assertThat(host.getPort(), is(bindPort));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(bindPort));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
      assertThat(host.serviceUri, is(SERVICE_URI));
      assertThat(host.waitIterationSleep, is(WAIT_ITERATION_SLEEP));
      assertThat(host.waitIterationCount, is(WAIT_ITERATION_COUNT));
    }

    @Test
    public void testParamsPassedToCreate() throws Throwable {

      final Random random = new Random();
      Integer bindPort = 46000 + random.nextInt(999);

      host = BasicServiceHost.create(BIND_ADDRESS,
          bindPort,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      assertThat(host.getPort(), is(bindPort));
      Path storagePath = Paths.get(storageDir.getPath()).resolve(Integer.toString(bindPort));
      assertThat(host.getStorageSandbox().getPath(), is(storagePath.toString()));
      assertThat(host.serviceUri, is(SERVICE_URI));
      assertThat(host.waitIterationSleep, is(WAIT_ITERATION_SLEEP));
      assertThat(host.waitIterationCount, is(WAIT_ITERATION_COUNT));
    }

    @Test
    public void testInitializationWithDefaultConstructor() throws Throwable {
      host = new BasicServiceHost();
      host.initialize();

      assertThat(host.getPort(), is(BasicServiceHost.BIND_PORT));
      assertThat(host.getStorageSandbox(), is(notNullValue()));
      assertThat(host.serviceUri, is(BasicServiceHost.SERVICE_URI));
      assertThat(host.waitIterationSleep, is(BasicServiceHost.WAIT_ITERATION_SLEEP));
      assertThat(host.waitIterationCount, is(BasicServiceHost.WAIT_ITERATION_COUNT));

      host.startWithCoreServices(); // need to start so that the non-default port gets assigned by Xenon

      assertThat(host.getPort(), not(BasicServiceHost.BIND_PORT));
    }

    @Test
    public void testDefaultCreateWithoutParams() throws Throwable {
      host = BasicServiceHost.create();

      assertThat(host.getPort(), not(BasicServiceHost.BIND_PORT));
      assertThat(host.getStorageSandbox(), is(notNullValue()));
      assertThat(host.serviceUri, is(BasicServiceHost.SERVICE_URI));
      assertThat(host.waitIterationSleep, is(BasicServiceHost.WAIT_ITERATION_SLEEP));
      assertThat(host.waitIterationCount, is(BasicServiceHost.WAIT_ITERATION_COUNT));
    }

    @Test
    public void startWithCoreServices() throws Throwable {
      //check if a few of the core services are available in the host

      host = new BasicServiceHost(BIND_ADDRESS,
          BIND_PORT,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      host.initialize();
      host.startWithCoreServices();
      assertThat(host.isStarted(), is(true));

      assertThat(
          host.checkServiceAvailable(com.vmware.xenon.services.common.ServiceUriPaths.DEFAULT_NODE_GROUP),
          is(true));
      assertThat(host.checkServiceAvailable(LuceneDocumentIndexService.SELF_LINK), is(true));
      assertThat(host.checkServiceAvailable(LuceneQueryTaskFactoryService.SELF_LINK), is(true));
    }

    @Test
    public void testCreate() throws Throwable {
      //check if a few of the core services are available in the host

      host = BasicServiceHost.create();
      assertThat(host.isStarted(), is(true));

      assertThat(
          host.checkServiceAvailable(com.vmware.xenon.services.common.ServiceUriPaths.DEFAULT_NODE_GROUP),
          is(true));
      assertThat(host.checkServiceAvailable(LuceneDocumentIndexService.SELF_LINK), is(true));
      assertThat(host.checkServiceAvailable(LuceneQueryTaskFactoryService.SELF_LINK), is(true));
    }

    @Test
    public void testDestroy() throws Throwable {

      host = new BasicServiceHost(BIND_ADDRESS,
          BIND_PORT,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      host.initialize();
      File storageDir = new File(host.getStorageSandbox());
      assertThat(storageDir.exists(), is(true));
      host.startWithCoreServices();
      assertThat(host.isStarted(), is(true));
      host.destroy();
      assertThat(storageDir.exists(), is(false));
      assertThat(host.isStarted(), is(false));
    }
  }

  /**
   * Test service used in ServiceStartTests.testStartServiceSynchronouslyFails.
   */
  public class AlwaysFailingToStartService extends ExampleService {

    public static final String ERROR_MESSAGE = "This service is expected to always fail to start";

    @Override
    public void handleStart(Operation startPost) {
      startPost.fail(new Exception(ERROR_MESSAGE));
    }
  }

  /**
   * Tests for the service start operation.
   */
  public class ServiceStartTests {

    private BasicServiceHost host;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    public void setUp() throws Throwable {

      host = new BasicServiceHost(BIND_ADDRESS,
          BIND_PORT,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      host.initialize();
      host.startWithCoreServices();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
        host = null;
      }

      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testStartServiceSynchronously() throws Throwable {
      host.startServiceSynchronously(ExampleService.createFactory(), null, ExampleService.FACTORY_LINK);
      assertThat(host.checkServiceAvailable(ExampleService.FACTORY_LINK), is(true));
    }

    @Test
    public void testStartServiceSynchronouslyFails() throws Throwable {
      AlwaysFailingToStartService alwaysFailingToStartService = new AlwaysFailingToStartService();
      try {
        host.startServiceSynchronously(alwaysFailingToStartService, null, ExampleService.FACTORY_LINK);
        Assert.fail("Service start did not fail as expected");
      } catch (Exception exception) {
        assertThat(exception.getMessage(), is(equalTo(AlwaysFailingToStartService.ERROR_MESSAGE)));
      }

      assertThat(host.checkServiceAvailable(ExampleService.FACTORY_LINK), is(false));
    }
  }

  /**
   * Tests for the service status check operations.
   */
  public class ServiceStateTests {

    private BasicServiceHost host;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDir);
    }

    @BeforeMethod
    public void setUpTest() throws Throwable {

      host = new BasicServiceHost(BIND_ADDRESS,
          BIND_PORT,
          STORAGE_PATH,
          SERVICE_URI,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT);

      host.initialize();
      host.startWithCoreServices();
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (host != null) {
        host.destroy();
        host = null;
      }

      FileUtils.deleteDirectory(storageDir);
    }

    @Test
    public void testGetServiceStateWithDefaultPath() throws Throwable {
      ExampleService exampleService = new ExampleService();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      Operation post = host.startServiceSynchronously(exampleService, exampleServiceState);
      assertThat(post.getStatusCode(), is(200));

      ExampleService.ExampleServiceState result = host.getServiceState(ExampleService.ExampleServiceState.class);
      assertThat(result.name, is(exampleServiceState.name));
    }

    @Test
    public void testGetServiceState() throws Throwable {
      ExampleService exampleService = new ExampleService();
      ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      Operation post = host.startServiceSynchronously(exampleService, exampleServiceState);
      assertThat(post.getStatusCode(), is(200));

      ExampleService.ExampleServiceState postResult = post.getBody(ExampleService.ExampleServiceState.class);
      ExampleService.ExampleServiceState result = host.getServiceState(ExampleService.ExampleServiceState.class,
          postResult.documentSelfLink);
      assertThat(result.name, is(exampleServiceState.name));
    }

    @Test
    public void testWaitForState() throws Throwable {
      ExampleService exampleService = new ExampleService();
      final ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

      Operation post = host.startServiceSynchronously(exampleService, exampleServiceState);
      assertThat(post.getStatusCode(), is(200));

      try {
        host.waitForState(ExampleService.ExampleServiceState.class,
            new Predicate<ExampleService.ExampleServiceState>() {
              @Override
              public boolean test(ExampleService.ExampleServiceState state) {
                return false;
              }
            });

        Assert.fail("this state transition should not have succeeded");
      } catch (TimeoutException e) {
        assertThat(e.getMessage(), containsString("Timeout waiting for state transition, serviceUri=[" +
            SERVICE_URI + "]"));
      }

      try {
        host.waitForState(ExampleService.ExampleServiceState.class,
            new Predicate<ExampleService.ExampleServiceState>() {
              @Override
              public boolean test(ExampleService.ExampleServiceState state) {
                return state.name.equals(exampleServiceState.name);
              }
            });
      } catch (RuntimeException runtimeException) {
        Assert.fail("failed to detect state transition");
      }
    }

    @Test
    public void testWaitForQuery() throws Throwable {
      ExampleService exampleService = new ExampleService();
      final ExampleService.ExampleServiceState exampleServiceState = new ExampleService.ExampleServiceState();
      exampleServiceState.name = UUID.randomUUID().toString();

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

      QueryTask query = QueryTask.create(spec)
          .setDirect(true);

      try {
        host.waitForQuery(query,
            new Predicate<QueryTask>() {
              @Override
              public boolean test(QueryTask queryTask) {
                return queryTask.results.documentLinks.size() > 0;
              }
            });

        Assert.fail("waitForQuery should not have succeeded before documents are created");
      } catch (RuntimeException runtimeException) {
        assertThat(runtimeException.getMessage(), is(equalToIgnoringCase("timeout waiting for query result.")));
      }

      Operation post = host.startServiceSynchronously(exampleService, exampleServiceState);
      assertThat(post.getStatusCode(), is(200));

      ExampleService.ExampleServiceState result = host.getServiceState(ExampleService.ExampleServiceState.class);
      assertThat(result.name, is(exampleServiceState.name));

      QueryTask response = host.waitForQuery(query,
          new Predicate<QueryTask>() {
            @Override
            public boolean test(QueryTask queryTask) {
              return queryTask.results.documentLinks.size() > 0;
            }
          });
      assertThat(response.results.documentLinks.size(), is(1));

      // verify fields are passed down correctly
      for (Map.Entry<String, Object> document : response.results.documents.entrySet()) {
        ExampleService.ExampleServiceState docState = Utils.fromJson(document.getValue(),
            ExampleService.ExampleServiceState.class);
        assertThat(docState.name, is(equalTo(exampleServiceState.name)));
      }
    }
  }
}
