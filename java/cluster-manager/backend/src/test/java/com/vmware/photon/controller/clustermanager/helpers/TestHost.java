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
package com.vmware.photon.controller.clustermanager.helpers;

import com.vmware.photon.controller.clustermanager.ClusterManagerFactory;
import com.vmware.photon.controller.clustermanager.ClusterManagerTestServiceGroup;
import com.vmware.photon.controller.common.clients.AgentControlClientFactory;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.CloudStoreHelper;
import com.vmware.photon.controller.common.xenon.OperationLatch;
import com.vmware.photon.controller.common.xenon.OperationUtils;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.host.XenonConfig;
import com.vmware.photon.controller.common.zookeeper.ServiceConfigFactory;
import com.vmware.photon.controller.nsxclient.NsxClientFactory;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.annotations.VisibleForTesting;

import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.util.function.Predicate;
import java.util.logging.LogManager;

/**
 * This class implements helper routines used to test service hosts in isolation.
 */
public class TestHost extends PhotonControllerXenonHost {

  public static final String FIELD_NAME_SELF_LINK = "SELF_LINK";
  public static final String SERVICE_URI = "/TestService";
  public static final int WAIT_ITERATION_SLEEP = 500;
  public static final int WAIT_ITERATION_COUNT = 10000 / WAIT_ITERATION_SLEEP;
  public static final String BIND_ADDRESS = "0.0.0.0";
  public static final Integer BIND_PORT = 0;
  public static final String REFERRER = "test-basic-service-host";
  public static final String STORAGE_PATH_PREFIX = ".xenon_test_sandbox";

  @VisibleForTesting
  protected String serviceUri;

  @VisibleForTesting
  protected int waitIterationSleep;

  @VisibleForTesting
  protected int waitIterationCount;

  public TestHost(XenonConfig xenonConfig,
                  HostClientFactory hostClientFactory,
                  AgentControlClientFactory agentControlClientFactory,
                  ServiceConfigFactory serviceConfigFactory,
                  NsxClientFactory nsxClientFactory,
                  CloudStoreHelper cloudStoreHelper) throws Throwable {
    super(xenonConfig, hostClientFactory, agentControlClientFactory, serviceConfigFactory, nsxClientFactory,
        cloudStoreHelper);
    this.serviceUri = SERVICE_URI;
    this.waitIterationSleep = WAIT_ITERATION_SLEEP;
    this.waitIterationCount = WAIT_ITERATION_COUNT;
  }

  public static TestHost create() throws Throwable {
    return create(null);
  }

  public static TestHost create(ClusterManagerFactory clusterManagerFactory) throws Throwable {
    String sandbox = Files.createTempDirectory(STORAGE_PATH_PREFIX).toAbsolutePath().toString();
    XenonConfig xenonConfig = new XenonConfig();
    xenonConfig.setBindAddress(BIND_ADDRESS);
    xenonConfig.setPort(0);
    xenonConfig.setStoragePath(sandbox);

    HostClientFactory hostClientFactory = mock(HostClientFactory.class);
    AgentControlClientFactory agentControlClientFactory = mock(AgentControlClientFactory.class);
    ServiceConfigFactory serviceConfigFactory = mock(ServiceConfigFactory.class);
    NsxClientFactory nsxClientFactory = mock(NsxClientFactory.class);
    ServerSet cloudStoreServerSet = mock(ServerSet.class);
    CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(cloudStoreServerSet);

    TestHost host = new TestHost(
        xenonConfig,
        hostClientFactory,
        agentControlClientFactory,
        serviceConfigFactory,
        nsxClientFactory,
        cloudStoreHelper);

    ClusterManagerTestServiceGroup clusterManagerTestServiceGroup =
        new ClusterManagerTestServiceGroup(clusterManagerFactory);

    host.registerDeployer(clusterManagerTestServiceGroup);
    host.start();
    return host;
  }

  public static  void destroy(TestHost host) throws Throwable {
    host.destroy();
    LogManager.getLogManager().reset();
  }

  public synchronized void destroy() throws Throwable {
    ServiceHostUtils.destroy(this);
  }

  public Operation startFactoryServiceSynchronously(Service service, String path) throws Throwable {
    if (!FactoryService.class.isAssignableFrom(service.getClass())) {
      throw new IllegalArgumentException("Service " + service.getClass().getName() + " is not FactoryService");
    }

    Operation post = Operation
        .createPost(UriUtils.buildUri(this, path));

    OperationLatch syncPost = new OperationLatch(post);
    startService(post, service);
    return syncPost.awaitOperationCompletion();
  }

  public Operation startServiceSynchronously(Service service, ServiceDocument body) throws Throwable {
    return startServiceSynchronously(service, body, this.serviceUri);
  }

  public Operation startServiceSynchronously(Service service, ServiceDocument body, String path) throws Throwable {
    return startServiceSynchronously(service, body, path, true);
  }

  public Operation startServiceSynchronously(
      Service service, ServiceDocument body, String path, Boolean disableOptions) throws Throwable {

    if (disableOptions) {
      service.toggleOption(Service.ServiceOption.OWNER_SELECTION, false);
      service.toggleOption(Service.ServiceOption.REPLICATION, false);
    }

    Operation post = Operation
        .createPost(UriUtils.buildUri(this, path));
    if (body != null) {
      post.setBody(body);
    }

    OperationLatch syncPost = new OperationLatch(post);
    startService(post, service);
    Operation completedOperation = syncPost.awaitOperationCompletion();
    return OperationUtils.handleCompletedOperation(post, completedOperation);
  }

  public Operation deleteServiceSynchronously() throws Throwable {
    return deleteServiceSynchronously(SERVICE_URI);
  }

  public Operation deleteServiceSynchronously(String path) throws Throwable {

    Operation delete = Operation
        .createDelete(UriUtils.buildUri(this, path))
        .setBody("{}")
        .setReferer(UriUtils.buildUri(this, REFERRER));

    OperationLatch syncDelete = new OperationLatch(delete);
    sendRequest(delete);
    return syncDelete.awaitOperationCompletion();
  }

  public Operation sendRequestAndWait(Operation op) throws Throwable {
    Operation operation = ServiceHostUtils.sendRequestAndWait(this, op, REFERRER);
    // For tests we check status code 200 to see if the response is OK
    // If nothing is changed in patch, it returns 304 which means not modified.
    // We will treat 304 as 200
    if (operation.getStatusCode() == 304) {
      operation.setStatusCode(200);
    }
    return operation;
  }

  public <T> T getServiceState(Class<T> type) throws Throwable {
    return getServiceState(type, this.serviceUri);
  }

  public <T> T getServiceState(Class<T> type, String path) throws Throwable {
    return ServiceHostUtils.getServiceState(this, type, path, "test-basic-service-host");
  }

  /**
   * Wait for the state change.
   *
   * @param type
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T> T waitForState(Class<T> type, Predicate<T> predicate) throws Throwable {
    return waitForState(this.serviceUri, type, predicate);
  }

  /**
   * Wait for the state change.
   *
   * @param type
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T> T waitForState(String uri, Class<T> type, Predicate<T> predicate) throws Throwable {
    return ServiceHostUtils.waitForServiceState(
        type, uri, predicate, this, this.waitIterationSleep,
        this.waitIterationCount, null);
  }

  /**
   * Wait for a query to returns particular information.
   *
   * @param query
   * @param predicate
   * @return
   * @throws Throwable
   */
  public QueryTask waitForQuery(QueryTask query, Predicate<QueryTask> predicate) throws Throwable {
    return ServiceHostUtils.waitForQuery(this, REFERRER, query, predicate,
        this.waitIterationCount, this.waitIterationSleep);
  }
}
