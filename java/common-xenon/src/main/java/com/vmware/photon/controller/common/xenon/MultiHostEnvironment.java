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

import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.QueryTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * TestMachine class hosting a Xenon host.
 *
 * @param <H> Subclass of ServiceHost as well as implementing XenonHostInfoProvider interface.
 */
public abstract class MultiHostEnvironment<H extends ServiceHost & XenonHostInfoProvider> {

  public static final int WAIT_ITERATION_SLEEP = 1;
  public static final int WAIT_ITERATION_COUNT = 30000 / WAIT_ITERATION_SLEEP; // 30 seconds.
  public static final int MAINTENANCE_INTERVAL_MS = 500;
  public static final int TEST_NODE_GROUP_CONVERGENCE_SLEEP = 20;
  public static final int DEFAULT_MULTI_HOST_COUNT = 3;
  protected static final String BIND_ADDRESS = "0.0.0.0";
  protected static final String STORAGE_PATH_PREFIX = ".xenon_test_sandbox";
  private static final Logger logger = LoggerFactory.getLogger(MultiHostEnvironment.class);
  protected H[] hosts;

  public H[] getHosts() {
    return this.hosts;
  }

  public ServerSet getServerSet() {
    StaticServerSet serverSet = new StaticServerSet();

    InetSocketAddress[] servers = new InetSocketAddress[this.hosts.length];
    for (int i = 0; i < this.hosts.length; i++) {
      // Public IP does not work here using local ip
      servers[i] = new InetSocketAddress("127.0.0.1", this.hosts[i].getPort());
    }
    return new StaticServerSet(servers);
  }

  /**
   * Start the Xenon host.
   *
   * @throws Throwable
   */
  public void start() throws Throwable {
    for (final H host : hosts) {
      host.start();
      host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(MAINTENANCE_INTERVAL_MS));
      waitForHostReady(host);
    }

    if (hosts.length > 1) {

      // join peer node group
      H host = hosts[0];
      for (int i = 1; i < hosts.length; i++) {
        H peerHost = hosts[i];
        ServiceHostUtils.joinNodeGroup(peerHost, host.getUri().getHost(), host.getPort());
      }

      // set quorum
      for (int i = 0; i < hosts.length; i++) {
        ServiceHostUtils.setQuorumSize(hosts[i], hosts.length, null);
      }

      // wait for hosts to reach AVAILABLE state
      ServiceHostUtils.waitForNodeGroupConvergence(
          hosts,
          ServiceUriPaths.DEFAULT_NODE_GROUP,
          ServiceHostUtils.DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES,
          // Since the default sleep time is 200 we will use a shorter time for tests
          MultiHostEnvironment.TEST_NODE_GROUP_CONVERGENCE_SLEEP);

      /**
       * In Xenon 0.7.5, the factories for replicated services are not reported as available on all
       * hosts in a node group; instead, they show up as available only on the node to which the
       * Xenon owner selection mechanism assigns ownership of the factory service. This is a change
       * from previous versions, where factory service availability was entirely opaque to outside
       * entities (0.7.0-0.7.2) or was handled as part of host availability (pre-0.7.0).
       *
       * Wait for factory services to be reported as available as appropriate given the replication
       * characteristics of the services.
       */
      for (int i = 0; i < hosts.length; i++) {
        waitForReplicatedFactoryServices(hosts[i]);
      }
    }
  }

  private boolean checkFactoryServiceAvailable(H host, Class c) {
    try {
      if (FactoryService.class.isAssignableFrom(c)) {
        if (((FactoryService) c.newInstance()).getOptions().contains(Service.ServiceOption.REPLICATION)) {
          return checkFactoryServiceAvailable(host, c, ServiceUriPaths.DEFAULT_NODE_SELECTOR);
        } else {
          return checkFactoryServiceAvailable(host, c, null);
        }
      } else {
        return true;
      }
    } catch (Throwable t) {
      logger.error("Checking factory service availability failed: {}", t);
      throw new RuntimeException(t);
    }
  }

  private boolean checkFactoryServiceAvailable(H host, Class<? extends Service> c, String selector) throws Throwable {

    URI availableUri = UriUtils.buildAvailableUri(UriUtils.buildUri(host, c));
    if (selector != null) {
      availableUri = UriUtils.buildBroadcastRequestUri(availableUri, selector);
    }

    boolean isReady[] = new boolean[1];
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Operation getOp = Operation
        .createGet(availableUri)
        .setReferer(UriUtils.buildUri(host, "/multi-host-environment"))
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                logger.info("Checking availability failed: " + e);
                isReady[0] = false;
                countDownLatch.countDown();
                return;
              }

              if (selector == null) {
                isReady[0] = true;
                countDownLatch.countDown();
                return;
              }

              NodeGroupBroadcastResponse rsp = o.getBody(NodeGroupBroadcastResponse.class);
              logger.info("Received {} failures, {} available nodes", rsp.failures.size(), rsp.availableNodeCount);
              isReady[0] = (rsp.failures.size() < rsp.availableNodeCount);
              countDownLatch.countDown();
            });

    host.sendRequest(getOp);
    countDownLatch.await();
    return isReady[0];
  }

  /**
   * Stop the Xenon host.
   *
   * @throws Throwable
   */
  public void stop() throws Throwable {
    this.dumpHosts();
    for (H host : hosts) {
      ServiceHostUtils.destroy(host);
    }
  }

  /**
   * Starts a factory service on all nodes.
   *
   * @param factoryClass
   * @param path
   * @throws Throwable
   */
  public void startFactoryServiceSynchronously(Class factoryClass, String path) throws Throwable {
    if (!FactoryService.class.isAssignableFrom(factoryClass)) {
      throw new IllegalArgumentException("Service " + factoryClass.getName() + " is not FactoryService");
    }

    for (ServiceHost host : hosts) {
      Operation post = Operation
          .createPost(UriUtils.buildUri(host, path));

      OperationLatch syncPost = new OperationLatch(post);
      host.startService(post, (FactoryService) factoryClass.newInstance());
      syncPost.awaitOperationCompletion();
    }
  }

  public <T extends ServiceDocument> Operation sendDeleteAndWait(String serviceUri) throws Throwable {
    Operation op = Operation.createDelete(UriUtils.buildUri(hosts[0], serviceUri, null))
        .setBody(new ServiceDocument());
    return sendRequestAndWait(op, hosts[0]);
  }

  /**
   * Issue a POST with the given parameters.
   *
   * @param serviceUri
   * @param parameters
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T extends ServiceDocument> Operation sendPostAndWait(
      String serviceUri, T parameters)
      throws Throwable {
    Operation op = Operation.createPost(UriUtils.buildUri(hosts[0], serviceUri, null))
        .setBody(parameters);
    return sendRequestAndWait(op, hosts[0]);
  }

  /**
   * Issue a POST with the given parameters and waits for replication.
   *
   * @param serviceUri
   * @param parameters
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T extends ServiceDocument> Operation sendPostAndWaitForReplication(
      String serviceUri, T parameters)
      throws Throwable {
    Operation op = sendPostAndWait(serviceUri, parameters);
    String serviceLink = op.getBody(ServiceDocument.class).documentSelfLink;
    waitForReplication(serviceUri, serviceLink);
    return op;
  }

  /**
   * Issue a POST with the given parameters.
   *
   * @param serviceUri
   * @param parameters
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T extends ServiceDocument> Operation sendPatchAndWait(
      String serviceUri, T parameters)
      throws Throwable {
    Operation op = Operation.createPatch(UriUtils.buildUri(hosts[0], serviceUri, null))
        .setBody(parameters);
    return sendRequestAndWait(op, hosts[0]);
  }

  /**
   * Send a POST operation to a service and wait for the response.
   */
  public <T extends ServiceDocument> T callServiceSynchronously(
      String serviceUri, T parameters, Class<T> type) throws Throwable {

    Operation postOperation = Operation
        .createPost(UriUtils.buildUri(hosts[0], serviceUri))
        .setBody(parameters);

    Operation result = sendRequestAndWait(postOperation, hosts[0]);
    if (result.getStatusCode() >= Operation.STATUS_CODE_FAILURE_THRESHOLD) {
      throw new XenonRuntimeException("Operation failed with code " + result.getStatusCode());
    }

    return result.getBody(type);
  }

  /**
   * Issue a POST with the given parameters and wait until the given predicate it true.
   */
  public <T extends ServiceDocument> T callServiceAndWaitForState(String serviceUri, T parameters, Class<T> type,
                                                                  Predicate<T> test) throws Throwable {
    return callServiceAndWaitForState(serviceUri, parameters, type, test, WAIT_ITERATION_SLEEP, WAIT_ITERATION_COUNT);
  }

  public <T extends ServiceDocument> T callServiceAndWaitForState(String serviceUri, T parameters, Class<T> type,
                                                                  Predicate<T> test, int waitIterationSleep,
                                                                  int waitIterationCount) throws Throwable {
    // Call service with POST operation.
    Operation op = Operation.createPost(UriUtils.buildUri(hosts[0], serviceUri, null))
        .setBody(parameters);
    Operation resultOp = sendRequestAndWait(op, hosts[0]);
    if (resultOp.getStatusCode() >= Operation.STATUS_CODE_FAILURE_THRESHOLD) {
      throw new XenonRuntimeException("Operation failed with code " + resultOp.getStatusCode());
    }

    // Verify result.
    String serviceLink = resultOp.getBody(ServiceDocument.class).documentSelfLink;
    return waitForServiceState(type, serviceLink, test, waitIterationSleep, waitIterationCount);
  }

  public <T> T getServiceState(String serviceUri, Class<T> type) throws Throwable {
    return ServiceHostUtils.getServiceState(hosts[0], type, serviceUri, "test-host");
  }

  public <T extends ServiceDocument> T waitForServiceState(
      Class<T> type, String serviceUri, Predicate<T> test)
      throws Throwable {
    return waitForServiceState(type, serviceUri, test, WAIT_ITERATION_SLEEP, WAIT_ITERATION_COUNT);
  }

  /**
   * Issue GET and wait until the given predicate is true.
   *
   * @throws Throwable
   */
  public <T extends ServiceDocument> T waitForServiceState(
      Class<T> type, String serviceUri, Predicate<T> test, int waitIterationSleep, int waitIterationCount)
      throws Throwable {
    T result = null;
    for (int i = 0; i < hosts.length; i++) {
      ServiceHost host = hosts[i];
      T r = ServiceHostUtils.waitForServiceState(
          type, serviceUri, test, host, waitIterationSleep, waitIterationCount, getEnvironmentCleanup());
      assert (r != null);
      logger.info("host " + host.getState().id + " has owner " + r.documentOwner);
      if (result != null && !ServiceUtils.documentEquals(type, result, r)) {
        logger.info(String.format("current %s last %s", Utils.toJson(r), Utils.toJson(result)));
        throw new IllegalStateException("response is not consistent across node group");
      }
      result = r;
    }

    return result;
  }

  /**
   * Wait for a query to returns particular information.
   *
   * @param query
   * @return
   * @throws Throwable
   */
  public QueryTask sendQueryAndWait(QueryTask query) throws Throwable {
    return ServiceHostUtils.sendQueryAndWait(hosts[0], "test-host", query);
  }

  public NodeGroupBroadcastResponse sendBroadcastQueryAndWait(QueryTask query) throws Throwable {
    return ServiceHostUtils.sendBroadcastQueryAndWait(hosts[0], "test-host", query);
  }

  public QueryTask waitForQuery(QueryTask query, Predicate<QueryTask> predicate) throws Throwable {
    return ServiceHostUtils.waitForQuery(hosts[0], "test-host", query, predicate);
  }

  /**
   * Issue GET and expect an answer.
   *
   * @param serviceUri
   * @param type
   * @param test
   * @param <T>
   * @return
   * @throws Throwable
   */
  public <T extends ServiceDocument> T checkServiceIsResponding(String serviceUri, Class<T> type, Predicate<T> test)
      throws Throwable {
    T result = null;
    for (int i = 0; i < hosts.length; i++) {
      ServiceHost host = hosts[i];
      result = ServiceHostUtils.waitForServiceState(type, serviceUri, test, host, getEnvironmentCleanup());
      assert (result != null);
      logger.info("host " + host.getState().id + " has owner " + result.documentOwner);
    }

    return result;
  }

  public void deleteService(String serviceSelfLink) throws Throwable {
    Operation op = Operation.createDelete(UriUtils.buildUri(hosts[0], serviceSelfLink))
        .setBody(new ServiceDocument());
    sendRequestAndWait(op, hosts[0]);
  }

  /**
   * Get ServiceStats from Service Owner.
   *
   * @param state
   * @return
   * @throws Throwable
   */
  public ServiceStats getOwnerServiceStats(ServiceDocument state) throws Throwable {
    ServiceHost host = this.hosts[0];

    Operation get = Operation.createGet(UriUtils.buildStatsUri(host, state.documentSelfLink));
    return forwardRequestAndWait(state.documentSelfLink, get, host).getBody(ServiceStats.class);
  }

  /**
   * Generates a unique storage sandbox path.
   *
   * @return
   */
  protected String generateStorageSandboxPath() {
    Path sandboxPath = FileSystems.getDefault().getPath(
        System.getProperty("user.home"), STORAGE_PATH_PREFIX, UUID.randomUUID().toString());
    return sandboxPath.toAbsolutePath().toString();
  }

  /**
   * Send given operation and wait for the response.
   *
   * @param op
   * @param host
   * @return
   * @throws Throwable
   */
  public Operation sendRequestAndWait(Operation op, ServiceHost host) throws Throwable {
    return ServiceHostUtils.sendRequestAndWait(host, op, "test-host");
  }

  /**
   * Send given operation and wait for the response.
   *
   * @param op
   * @param host
   * @return
   * @throws Throwable
   */
  private Operation forwardRequestAndWait(String serviceSelfLink, Operation op, ServiceHost host) throws Throwable {
    OperationLatch syncOp = new OperationLatch(op);
    op.setReferer(UriUtils.buildUri(host, "test-host"))
        .setExpiration(Utils.getNowMicrosUtc() + host.getOperationTimeoutMicros());
    host.forwardRequest(ServiceUriPaths.DEFAULT_NODE_SELECTOR, serviceSelfLink, op);

    return syncOp.awaitOperationCompletion();
  }

  private void waitForHostReady(H host) throws Throwable {
    String timeoutMessage = String.format("Timeout waiting for host ready, host=[%s]", host.getUri());
    ServiceHostUtils.waitForState(() -> host, (h) -> h.isReady(), getEnvironmentCleanup(), timeoutMessage);
  }

  /**
   * Waits for replicated service factories to become available for the host.
   * @param host
   * @throws Throwable
   */
  public void waitForReplicatedFactoryServices(H host) throws Throwable {
    String timeoutMessage = String.format("Timeout waiting for factory services [host %s]", host.getUri());
    ServiceHostUtils.waitForState(() -> host,
        (H h) -> Stream.of(h.getFactoryServices()).allMatch((c) -> checkFactoryServiceAvailable(h, c)),
        getEnvironmentCleanup(), timeoutMessage);
  }

  private void waitForReplication(String serviceUri, String serviceLink) throws Throwable {
    for (ServiceHost host : getHosts()) {
      ServiceHostUtils.waitForServiceState(
          ServiceDocumentQueryResult.class,
          serviceUri,
          new Predicate<ServiceDocumentQueryResult>() {
            @Override
            public boolean test(ServiceDocumentQueryResult serviceDocumentQueryResult) {
              for (String documentLink : serviceDocumentQueryResult.documentLinks) {
                if (documentLink.equals(serviceLink)) {
                  return true;
                }
              }
              return false;
            }
          },
          host,
          WAIT_ITERATION_SLEEP,
          WAIT_ITERATION_COUNT,
          null);
    }
  }

  /**
   * Logs the contents of all factories on the hosts.
   *
   * @throws Throwable
   */
  private void dumpHosts() throws Throwable {
    for (H host : hosts) {
      ServiceHostUtils.dumpHost(host, "test-host");
    }
  }

  private Runnable getEnvironmentCleanup() {
    return new Runnable() {
      @Override
      public void run() {
        try {
          MultiHostEnvironment.this.dumpHosts();
        } catch (Throwable t) {
          logger.error("Error when dumpHosts", t);
        }
      }
    };
  }
}
