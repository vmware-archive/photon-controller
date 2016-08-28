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

import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupBroadcastResponse;
import com.vmware.xenon.services.common.NodeGroupService;
import com.vmware.xenon.services.common.NodeState;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.net.URI;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Class implements utility methods for ServiceHost objects.
 */
public class ServiceHostUtils {

  public static final int FAST_MAINT_INTERVAL_MILLIS = 100;

  private static int timeoutSeconds = 300;
  /**
   * Maximum numbers of times to check for node group convergence.
   */
  public static final int DEFAULT_NODE_GROUP_CONVERGENCE_MAX_RETRIES = 200;
  /**
   * Number of milliseconds to sleep between node group convergence checks.
   */
  public static final int DEFAULT_NODE_GROUP_CONVERGENCE_SLEEP = 200;

  public static final long DEFAULT_DELETE_ALL_DOCUMENTS_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);

  private static final Logger logger = LoggerFactory.getLogger(ServiceHostUtils.class);
  /**
   * Value to use for the referrer in Xenon operations.
   */
  private static final String REFERRER_PATH = "/service-host-utils";

  /**
   * Number of checks status needs to stay stable before it is considered converged.
   */
  private static final int REQUIRED_STABLE_STATE_COUNT = 5;

  private static final long WAIT_ITERATION_SLEEP_MILLIS = 500;

  private static final long WAIT_ITERATION_COUNT = 30000 / WAIT_ITERATION_SLEEP_MILLIS; // 30 seconds.

  public static void waitForNodeGroupConvergence(
      ServiceHost[] hosts,
      String nodeGroupPath,
      int maxRetries,
      int retryInterval
  ) throws Throwable {

    checkArgument(hosts != null, "hosts cannot be null");
    checkArgument(hosts.length > 0, "hosts cannot be empty");
    checkArgument(!Strings.isNullOrEmpty(nodeGroupPath), "nodeGroupPath cannot be null or empty");
    checkArgument(maxRetries > 0, "maxRetries must be > 0");

    Collection<URI> nodeGroupUris = new HashSet<>();
    for (ServiceHost host : hosts) {
      nodeGroupUris.add(new URI("http", null, host.getState().bindAddress, host.getPort(), nodeGroupPath, null, null));
    }

    waitForNodeGroupConvergence(
        hosts[0],
        nodeGroupUris,
        nodeGroupUris.size(),
        nodeGroupUris.size(),
        true,
        maxRetries);
  }

  public static void waitForNodeGroupConvergence(
      ServiceHost localHost,
      String[] peerNodes,
      String nodeGroupPath,
      int maxRetries,
      int retryInterval
  ) throws Throwable {

    checkArgument(localHost != null, "hosts cannot be null");
    checkArgument(!Strings.isNullOrEmpty(nodeGroupPath), "nodeGroupPath cannot be null or empty");
    checkArgument(maxRetries > 0, "maxRetries must be > 0");

    List<Pair<String, Integer>> remoteHostIpAndPortPairs = new ArrayList<>();
    Collection<URI> nodeGroupUris = new HashSet<>();
    for (String peer : peerNodes) {
      URI uri = new URI(peer);
      remoteHostIpAndPortPairs.add(Pair.of(uri.getHost(), uri.getPort()));
      nodeGroupUris.add(new URI("http", null, uri.getHost(), uri.getPort(), nodeGroupPath, null, null));
    }

    waitForNodeGroupConvergence(
        localHost,
        nodeGroupUris,
        nodeGroupUris.size(),
        nodeGroupUris.size(),
        true,
        maxRetries);
  }

//  public static void waitForNodeGroupConvergence(
//      ServiceHost localHost,
//      Collection<Pair<String, Integer>> remoteHostIpAndPortPairs,
//      String nodeGroupPath,
//      int maxRetries,
//      int retryInterval) throws Throwable {
//    checkArgument(localHost != null, "localHost cannot be null");
//    checkArgument(remoteHostIpAndPortPairs != null, "remoteHostIpAndPortPairs cannot be null");
//    checkArgument(!Strings.isNullOrEmpty(nodeGroupPath), "nodeGroupPath cannot be null or empty");
//    checkArgument(maxRetries > 0, "maxRetries must be > 0");
//
//    for (Pair<String, Integer> remoteHostIpAndPortPair : remoteHostIpAndPortPairs) {
//
//      int checkRetries = maxRetries;
//      int checksToConvergence = REQUIRED_STABLE_STATE_COUNT;
//      while (checkRetries > 0 && checksToConvergence > 0) {
//        // update retry count and sleep
//        checkRetries--;
//        Thread.sleep(retryInterval * checksToConvergence);
//
//        // check the host response
//        NodeGroupService.NodeGroupState response = getNodeGroupState(
//            localHost,
//            remoteHostIpAndPortPair.getKey(),
//            remoteHostIpAndPortPair.getValue(),
//            nodeGroupPath);
//        if (response.nodes.size() < remoteHostIpAndPortPairs.size()) {
//          continue;
//        }
//
//        // check host status
//        checksToConvergence--;
//        for (NodeState nodeState : response.nodes.values()) {
//          if (nodeState.status != NodeState.NodeStatus.AVAILABLE) {
//            checksToConvergence = REQUIRED_STABLE_STATE_COUNT;
//            break;
//            // Note that we are not breaking from the above while loop where checksToConvergence is done
//            // This is because the nodes might switch between AVAILABLE and SYNCHRONIZING as the other nodes join
//          }
//        }
//      }
//
//      if (checkRetries == 0) {
//        throw new TimeoutException("nodes did not converge");
//      }
//    }
//  }

  public static void waitForNodeGroupConvergence(
      ServiceHost localHost,
      Collection<URI> nodeGroupUris,
      int healthyMemberCount,
      Integer totalMemberCount,
      boolean waitForTimeSync,
      int maxRetries) throws Throwable {
    checkArgument(localHost != null, "localHost cannot be null");
    checkArgument(nodeGroupUris != null, "nodeGroupUris cannot be null");
    checkArgument(!nodeGroupUris.isEmpty(), "nodeGroupUris cannot be null");
    checkArgument(maxRetries > 0, "maxRetries must be > 0");

    Map<URI, EnumSet<NodeState.NodeOption>> expectedOptionsPerNodeGroupUri = new HashMap<>();
    final int sleepTimeMillis = FAST_MAINT_INTERVAL_MILLIS * 2;
    Date now = null;
    Date expiration = new Date(new Date().getTime() + TimeUnit.SECONDS.toMillis(timeoutSeconds));
    Map<URI, NodeGroupService.NodeGroupState> nodesPerHost = new HashMap<>();
    Set<Long> updateTime = new HashSet<>();
    do {
      nodesPerHost.clear();
      updateTime.clear();
      TestContext ctx = TestContext.create(nodeGroupUris.size(), TimeUnit.SECONDS.toMicros(timeoutSeconds));
      for (URI nodeGroup : nodeGroupUris) {
        getNodeState(localHost, nodeGroup, nodesPerHost, ctx);
        EnumSet<NodeState.NodeOption> expectedOptions = expectedOptionsPerNodeGroupUri.get(nodeGroup);
        if (expectedOptions == null) {
          expectedOptionsPerNodeGroupUri.put(nodeGroup, NodeState.DEFAULT_OPTIONS);
        }
      }
      ctx.await();

      boolean isConverged = true;
      for (Map.Entry<URI, NodeGroupService.NodeGroupState> entry : nodesPerHost
          .entrySet()) {

        NodeGroupService.NodeGroupState nodeGroupState = entry.getValue();
        updateTime.add(nodeGroupState.membershipUpdateTimeMicros);
        int healthyNodeCount = calculateHealthyNodeCount(nodeGroupState);

        if (totalMemberCount != null
            && nodeGroupState.nodes.size() != totalMemberCount.intValue()) {
          logger.info(String.format("Host %s is reporting %d healthy members %d total, expected %d total",
              entry.getKey(), healthyNodeCount, nodesPerHost.size(),
              healthyMemberCount, totalMemberCount));
          isConverged = false;
          break;
        }

        if (healthyNodeCount != healthyMemberCount) {
          logger.info(String.format("Host %s is reporting %d healthy members, expected %d",
              entry.getKey(), healthyNodeCount, healthyMemberCount));
          isConverged = false;
          break;
        }

        validateNodes(entry.getValue(), healthyMemberCount, expectedOptionsPerNodeGroupUri);
      }

      now = new Date();

      if (waitForTimeSync && updateTime.size() != 1) {
        logger.info(String.format("Update times did not converge: %s", updateTime.toString()));
        isConverged = false;
      }

      if (isConverged) {
        break;
      }

      Thread.sleep(sleepTimeMillis);
    } while (now.before(expiration));

    boolean log = true;
    updateTime.clear();
    for (Map.Entry<URI, NodeGroupService.NodeGroupState> entry : nodesPerHost
        .entrySet()) {
      updateTime.add(entry.getValue().membershipUpdateTimeMicros);
      for (NodeState n : entry.getValue().nodes.values()) {
        if (log) {
          logger.info(String.format("%s:%s %s, (time) %d, (version) %d", n.groupReference, n.id, n.status,
              n.documentUpdateTimeMicros, n.documentVersion));
          log = false;
        }
//        if (n.status == NodeState.NodeStatus.AVAILABLE) {
//          this.peerHostIdToNodeState.put(n.id, n);
//        }
      }
    }

    try {
      if (waitForTimeSync && updateTime.size() != 1) {
        throw new IllegalStateException("Update time did not converge");
      }

      if (now.after(expiration)) {
        throw new TimeoutException();
      }
    } catch (Throwable e) {
      for (Map.Entry<URI, NodeGroupService.NodeGroupState> entry : nodesPerHost
          .entrySet()) {
        logger.info(String.format("%s reports %s", entry.getKey(), Utils.toJsonHtml(entry.getValue())));
      }
      throw e;
    }

    logger.info("Node group converged successfully.");

//    if (!waitForTimeSync) {
//      return;
//    }
//
//    // additional check using convergence utility
//    Date exp = new Date(new Date().getTime() + TimeUnit.SECONDS.toMillis(timeoutSeconds));
//    while (new Date().before(exp)) {
//      boolean[] isConverged = new boolean[1];
//      NodeGroupService.NodeGroupState ngs = nodesPerHost.values().iterator().next();
//
//      testStart(1);
//      Operation op = Operation.createPost(null)
//          .setReferer(getReferer())
//          .setExpiration(Utils.getNowMicrosUtc() + getOperationTimeoutMicros());
//      NodeGroupUtils.checkConvergenceFromAnyHost(this, ngs, op.setCompletion((o, e) -> {
//        if (e != null && waitForTimeSync) {
//          log(Level.INFO, "Convergence failure, will retry: %s", e.getMessage());
//          isConverged[0] = false;
//        } else {
//          isConverged[0] = true;
//        }
//        completeIteration();
//      }));
//      testWait();
//      if (!isConverged[0]) {
//        Thread.sleep(sleepTimeMillis);
//        continue;
//      }
//      break;
//    }
//
//    if (new Date().after(exp)) {
//      throw new TimeoutException();
//    }
  }

  public static void getNodeState(
      ServiceHost localHost,
      URI nodeGroup,
      Map<URI, NodeGroupService.NodeGroupState> nodesPerHost,
      TestContext ctx) {
    URI u = UriUtils.buildExpandLinksQueryUri(nodeGroup);
    Operation get = Operation.createGet(u).setReferer(UriUtils.buildUri(localHost, REFERRER_PATH))
        .setCompletion((o, e) -> {
      NodeGroupService.NodeGroupState ngs = null;
      if (e != null) {
        // failure is OK, since we might have just stopped a host
        logger.info(String.format("Host %s failed GET with %s", nodeGroup, e.getMessage()));
        ngs = new NodeGroupService.NodeGroupState();
      } else {
        ngs = o.getBody(NodeGroupService.NodeGroupState.class);
      }
      synchronized (nodesPerHost) {
        nodesPerHost.put(nodeGroup, ngs);
      }
//      if (ctx == null) {
//        completeIteration();
//      } else {
        ctx.completeIteration();
//      }
    });
    localHost.sendRequest(get);
//    send(localHost, get);
  }

  public static int calculateHealthyNodeCount(NodeGroupService.NodeGroupState r) {
    int healthyNodeCount = 0;
    for (NodeState ns : r.nodes.values()) {
      if (ns.status == NodeState.NodeStatus.AVAILABLE) {
        healthyNodeCount++;
      }
    }
    return healthyNodeCount;
  }

  public static void validateNodes(NodeGroupService.NodeGroupState r, int expectedNodesPerGroup,
                                   Map<URI, EnumSet<NodeState.NodeOption>> expectedOptionsPerNode) {

    int healthyNodes = 0;
    NodeState localNode = null;
    for (NodeState ns : r.nodes.values()) {
      if (ns.status == NodeState.NodeStatus.AVAILABLE) {
        healthyNodes++;
      }
      assert(ns.documentKind.equals(Utils.buildKind(NodeState.class)));
      if (ns.documentSelfLink.endsWith(r.documentOwner)) {
        localNode = ns;
      }

      assert(ns.options != null);
      EnumSet<NodeState.NodeOption> expectedOptions = expectedOptionsPerNode.get(ns.groupReference);
      if (expectedOptions == null) {
        expectedOptions = NodeState.DEFAULT_OPTIONS;
      }

      for (NodeState.NodeOption eo : expectedOptions) {
        assert(ns.options.contains(eo));
      }

      assert(ns.id != null);
      assert(ns.groupReference != null);
      assert(ns.documentSelfLink.startsWith(ns.groupReference.getPath()));
    }

    assert(healthyNodes >= expectedNodesPerGroup);
    assert(localNode != null);
  }

  public static void setQuorumSize(ServiceHost serviceHost, int quorumSize, String referrer) throws Throwable {
    NodeGroupService.UpdateQuorumRequest updateQuorumRequest = NodeGroupService.UpdateQuorumRequest.create(false);
    updateQuorumRequest.setMembershipQuorum(quorumSize);

    Operation updateQuorumOp = Operation
        .createPatch(UriUtils.buildUri(serviceHost, ServiceUriPaths.DEFAULT_NODE_GROUP))
        .setBody(updateQuorumRequest);

    sendRequestAndWait(serviceHost, updateQuorumOp, referrer);
  }

  /**
   * Retrieves the state for a particular node group on the host.
   *
   * @param host
   * @param nodeGroupPath
   * @return
   * @throws Throwable
   */
  public static NodeGroupService.NodeGroupState getNodeGroupState(
      ServiceHost host,
      String nodeGroupPath
  ) throws Throwable {
    checkArgument(host != null, "host cannot be null");
    return getNodeGroupState(host, host.getState().bindAddress, host.getPort(), nodeGroupPath);
  }

  public static NodeGroupService.NodeGroupState getNodeGroupState(
      ServiceHost localHost,
      String remoteHostIp,
      int remoteHostPort,
      String nodeGroupPath
  ) throws Throwable {
    checkArgument(localHost != null, "localHost cannot be null");
    checkArgument(!Strings.isNullOrEmpty(remoteHostIp), "remoteHostIp cannot be null or empty");
    checkArgument(!Strings.isNullOrEmpty(nodeGroupPath), "nodeGroupPath cannot be null or empty");

    Operation get = Operation
        .createGet(UriUtils.buildUri(remoteHostIp, remoteHostPort, nodeGroupPath, null))
        .setReferer(UriUtils.buildUri(localHost, REFERRER_PATH));

    OperationLatch opLatch = new OperationLatch(get);
    localHost.sendRequest(get);

    return opLatch.awaitOperationCompletion().getBody(NodeGroupService.NodeGroupState.class);
  }

  /**
   * Function used to wait for a service to be available.
   *
   * @param host
   * @param timeout
   * @param serviceLinks
   * @throws Throwable
   */
  public static void waitForServiceAvailability(
      ServiceHost host, long timeout, String... serviceLinks) throws Throwable {
    final CountDownLatch latch = new CountDownLatch(serviceLinks.length);
    final Throwable error = new Throwable("Error: registerForAvailability returned errors");

    Operation.CompletionHandler handler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (null != throwable) {
          error.addSuppressed(throwable);
        }

        latch.countDown();
      }
    };
    host.registerForServiceAvailability(handler, serviceLinks);

    if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
      throw new TimeoutException(
          String.format("One or several of service(s) %s not available", Utils.toJson(false, false, serviceLinks)));
    }

    if (error.getSuppressed().length > 0) {
      throw error;
    }
  }

  /**
   * Starts the factory services for the given list of services.
   *
   * @param host
   * @param factoryServicesMap
   */
  public static void startFactoryServices(ServiceHost host,
                                          Map<Class<? extends Service>, Supplier<FactoryService>> factoryServicesMap) {
    checkNotNull(host, "host cannot be null");
    checkNotNull(factoryServicesMap, "factoryServicesMap cannot be null");

    Iterator<Map.Entry<Class<? extends Service>, Supplier<FactoryService>>> it =
        factoryServicesMap.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Class<? extends Service>, Supplier<FactoryService>> entry = it.next();
      host.startFactory(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Starts the list of services on the host.
   *
   * @param host
   * @param services
   */
  public static void startServices(ServiceHost host, Class... services)
      throws InstantiationException, IllegalAccessException {
    checkArgument(services != null, "services cannot be null");

    for (Class service : services) {
      startService(host, service);
    }
  }

  /**
   * Starts a service specified by the class type on the host.
   *
   * @param host
   * @param service
   * @throws InstantiationException
   * @throws IllegalAccessException
   */
  public static void startService(ServiceHost host, Class service)
      throws InstantiationException, IllegalAccessException {
    startService(host, service, null);
  }

  /**
   * Starts a service specified by the class type on the host.
   *
   * @param host
   * @param service
   * @param path
   * @throws InstantiationException
   * @throws IllegalAccessException
   */
  public static void startService(ServiceHost host, Class service, String path)
      throws InstantiationException, IllegalAccessException {
    checkArgument(host != null, "host cannot be null");
    checkArgument(service != null, "service cannot be null");

    Service instance = (Service) service.newInstance();
    URI serviceUri = buildServiceUri(host, service, path);
    Operation.CompletionHandler completionHandler = new Operation.CompletionHandler() {
      @Override
      public void handle(Operation operation, Throwable throwable) {
        if (throwable != null) {
          logger.debug("Start service {[]} failed: {}", serviceUri, throwable);
        }
      }
    };

    Operation op = Operation
        .createPost(serviceUri)
        .setCompletion(completionHandler);
    host.startService(op, instance);
  }

  /**
   * Returns true is all services registered with the host are ready. False otherwise.
   *
   * @param host
   * @param serviceLinkFieldName
   * @param services
   * @return
   * @throws NoSuchFieldException
   * @throws IllegalAccessException
   */
  public static boolean areServicesReady(ServiceHost host, String serviceLinkFieldName, Class... services)
      throws NoSuchFieldException, IllegalAccessException {
    checkArgument(host != null, "host cannot be null");
    checkArgument(serviceLinkFieldName != null, "serviceLinkFieldName cannot be null");

    boolean areReady = true;
    for (Class service : services) {
      boolean isServiceReady = isServiceReady(host, serviceLinkFieldName, service);
      if (!isServiceReady) {
        logger.info("%s is not ready.", getServiceSelfLink(serviceLinkFieldName, service));
      }
      areReady &= isServiceReady(host, serviceLinkFieldName, service);
    }

    return areReady;
  }

  /**
   * Returns true if the service registered with the host is ready. False otherwise.
   *
   * @param host
   * @param serviceLinkFieldName
   * @param service
   * @return
   * @throws NoSuchFieldException
   * @throws IllegalAccessException
   */
  public static boolean isServiceReady(ServiceHost host, String serviceLinkFieldName, Class service)
      throws NoSuchFieldException, IllegalAccessException {
    return host.checkServiceAvailable(getServiceSelfLink(serviceLinkFieldName, service));
  }

  /**
   * Returns the self links of the services.
   *
   * @param serviceLinkFieldName
   * @param services
   * @return
   * @throws NoSuchFieldException
   * @throws IllegalAccessException
   */
  public static Collection<String> getServiceSelfLinks(String serviceLinkFieldName, Class... services)
      throws NoSuchFieldException, IllegalAccessException {
    List<String> serviceSelfLinks = new ArrayList<>();
    for (Class service : services) {
      serviceSelfLinks.add(getServiceSelfLink(serviceLinkFieldName, service));
    }
    return serviceSelfLinks;
  }

  /**
   * Returns the self link of the service.
   *
   * @param serviceLinkFieldName
   * @param service
   * @return
   * @throws NoSuchFieldException
   * @throws IllegalAccessException
   */
  public static String getServiceSelfLink(String serviceLinkFieldName, Class service)
      throws NoSuchFieldException, IllegalAccessException {
    Field serviceLinkField = service.getDeclaredField(serviceLinkFieldName);
    return (String) serviceLinkField.get(null);
  }

  /**
   * This method joins the current peer to the default node group.
   *
   * @param peerHost
   */
  public static void joinNodeGroup(ServiceHost host, String peerHost) {
    joinNodeGroup(host, peerHost, host.getPort());
  }

  /**
   * This method joins the current peer to the default node group.
   *
   * @param host
   * @param peerHost
   * @param peerPort
   */
  public static void joinNodeGroup(ServiceHost host, String peerHost, int peerPort) {
    if (!host.checkServiceAvailable(ServiceUriPaths.DEFAULT_NODE_GROUP)) {
      logger.warn("DEFAULT_NODE_GROUP service is unavailable!");
      return;
    }

    URI peerNodeGroup = UriUtils.buildUri(peerHost, peerPort, "", null);

    // send the request to the node group instance we have picked as the "initial" one
    host.joinPeers(ImmutableList.of(peerNodeGroup), ServiceUriPaths.DEFAULT_NODE_GROUP);
    logger.info("Joining group through {}", peerNodeGroup);
  }

  /**
   * Send given operation and wait for the response.
   *
   * @param host
   * @param requestedOperation
   * @param referrer
   * @return
   * @throws Throwable
   */
  public static Operation sendRequestAndWait(ServiceHost host, Operation requestedOperation, String referrer)
      throws TimeoutException, DocumentNotFoundException, BadRequestException, InterruptedException {
    OperationLatch syncOp = new OperationLatch(requestedOperation);
    if (requestedOperation.getReferer() == null) {
      requestedOperation.setReferer(UriUtils.buildUri(host, referrer));
    }

    host.sendRequest(requestedOperation);
    Operation completedOperation = syncOp.awaitOperationCompletion();
    return OperationUtils.handleCompletedOperation(requestedOperation, completedOperation);
  }

  public static NodeGroupBroadcastResponse sendBroadcastQueryAndWait(
      ServiceHost host, String referrer, QueryTask query) throws Throwable {

    Operation queryPostOperation = Operation
        .createPost(UriUtils.buildBroadcastRequestUri(
            UriUtils.buildUri(host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
            ServiceUriPaths.DEFAULT_NODE_SELECTOR))
        .setBody(query);

    return sendRequestAndWait(host, queryPostOperation, referrer).getBody(NodeGroupBroadcastResponse.class);
  }

  public static QueryTask waitForQuery(
      ServiceHost host, String referrer, QueryTask query, Predicate<QueryTask> predicate)
      throws Throwable {
    return waitForQuery(host, referrer, query, predicate, WAIT_ITERATION_COUNT, WAIT_ITERATION_SLEEP_MILLIS);
  }

  /**
   * Wait for a query to returns particular information.
   */
  public static QueryTask waitForQuery(
      ServiceHost host, String referrer, QueryTask query, Predicate<QueryTask> predicate,
      long waitIterationCount, long waitIterationSleep)
      throws Throwable {
    for (int i = 0; i < waitIterationCount; i++) {
      QueryTask result = sendQueryAndWait(host, referrer, query);
      if (predicate.test(result)) {
        return result;
      }
      Thread.sleep(waitIterationSleep);
    }
    throw new RuntimeException("timeout waiting for query result.");
  }

  public static QueryTask sendQueryAndWait(ServiceHost host, String referrer, QueryTask query) throws Throwable {

    Operation queryOp = Operation
        .createPost(UriUtils.buildUri(host, ServiceUriPaths.CORE_QUERY_TASKS))
        .setBody(query);

    return sendRequestAndWait(host, queryOp, referrer).getBody(QueryTask.class);
  }

  public static <T> T getServiceState(
      ServiceHost host, Class<T> type, String path, String referrer)
      throws Throwable {
    URI uri = UriUtils.buildUri(host, path);
    return getServiceState(host, type, uri, referrer);
  }

  private static <T> T getServiceState(ServiceHost host,
                                       Class<T> type, URI uri, String referrer) throws Throwable {
    Operation op = Operation.createGet(uri);
    Operation resultOp = sendRequestAndWait(host, op, referrer);
    return resultOp.getBody(type);
  }

  public static <T> T waitForServiceState(final Class<T> type, final String serviceUri,
                                          Predicate<T> predicate,
                                          final ServiceHost host,
                                          Runnable cleanup)
      throws Throwable {
    return waitForServiceState(type, serviceUri, predicate, host,
        WAIT_ITERATION_SLEEP_MILLIS, WAIT_ITERATION_COUNT, cleanup);
  }


  /**
   * Wait until service state satisfies the given predicate.
   */
  public static <T> T waitForServiceState(final Class<T> type, final String serviceUri,
                                          Predicate<T> predicate,
                                          final ServiceHost host,
                                          long waitIterationSleepMillis,
                                          long waitIterationCount,
                                          Runnable cleanup)
      throws Throwable {

    String timeoutMessage = String.format("Timeout waiting for state transition, serviceUri=[%s]", serviceUri);
    return waitForState(
        new Supplier<T>() {
          @Override
          public T get() {
            try {
              return getServiceState(host, type, serviceUri, "test-host");
            } catch (Throwable t) {
              throw new RuntimeException("Failed to get service state", t);
            }
          }
        },
        predicate,
        waitIterationSleepMillis,
        waitIterationCount,
        cleanup,
        timeoutMessage);
  }

  /**
   * Generic wait function.
   */
  public static <T> T waitForState(Supplier<T> supplier, Predicate<T> predicate, Runnable cleanup,
                                   String timeoutMessage)
      throws Throwable {
    return waitForState(supplier, predicate,
        WAIT_ITERATION_SLEEP_MILLIS, WAIT_ITERATION_COUNT, cleanup, timeoutMessage);
  }

  /**
   * Generic wait function.
   */
  public static <T> T waitForState(Supplier<T> supplier, Predicate<T> predicate,
                                   long waitIterationSleepMillis, long waitIterationCount,
                                   Runnable cleanup, String timeoutMessage)
      throws Throwable {
    for (int i = 0; i < waitIterationCount; i++) {
      T t = supplier.get();
      if (predicate.test(t)) {
        return t;
      }
      Thread.sleep(waitIterationSleepMillis);
    }

    if (cleanup != null) {
      cleanup.run();
    }

    logger.warn(timeoutMessage);
    throw new TimeoutException(timeoutMessage);
  }

  /**
   * Logs the contents of all factories on a host.
   *
   * @param host
   * @throws Throwable
   */
  public static <H extends ServiceHost & XenonHostInfoProvider> void dumpHost(
      H host, String referrer) throws Throwable {
    logger.info(String.format("host: %s - %s", host.getId(), host.getPort()));
    for (Class factory : host.getFactoryServices()) {
      try {
        Operation op = Operation.createGet(
            UriUtils.buildExpandLinksQueryUri(
                UriUtils.buildUri(host, factory)));
        Operation result = sendRequestAndWait(host, op, referrer);
        logger.info(String.format("%s: %s: %s", host.getPort(), factory.getSimpleName(),
            Utils.toJson(false, false, result.getBodyRaw())));
      } catch (Throwable ex) {
        logger.info(String.format("Could not get service: %s", factory.getCanonicalName()));
      }
    }
  }

  public static <H extends ServiceHost> void deleteAllDocuments(H host, String referrer) throws Throwable {
    ServiceHostUtils.deleteAllDocuments(host,
        referrer, DEFAULT_DELETE_ALL_DOCUMENTS_TIMEOUT_MILLIS,
        TimeUnit.MILLISECONDS);
  }

  public static <H extends ServiceHost> void deleteAllDocuments(H host, String referrer, long timeout, TimeUnit
      timeUnit)
      throws Throwable {
    QueryTask.Query selfLinkClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_SELF_LINK)
        .setTermMatchValue("/photon/*")
        .setTermMatchType(QueryTask.QueryTerm.MatchType.WILDCARD);

    QueryTask.QuerySpecification querySpecification = new QueryTask.QuerySpecification();
    querySpecification.query.addBooleanClause(selfLinkClause);
    QueryTask queryTask = QueryTask.create(querySpecification).setDirect(true);

    NodeGroupBroadcastResponse queryResponse =
        ServiceHostUtils.sendBroadcastQueryAndWait(host, referrer, queryTask);

    Set<String> documentLinks = QueryTaskUtils.getBroadcastQueryDocumentLinks(queryResponse);

    if (documentLinks == null || documentLinks.size() <= 0) {
      return;
    }

    CountDownLatch latch = new CountDownLatch(1);

    OperationJoin.JoinedCompletionHandler handler = new OperationJoin.JoinedCompletionHandler() {
      @Override
      public void handle(Map<Long, Operation> ops, Map<Long, Throwable> failures) {
        if (failures != null && !failures.isEmpty()) {
          for (Throwable e : failures.values()) {
            logger.error("deleteAllDocuments failed", e);
          }
        }
        latch.countDown();
      }
    };

    Collection<Operation> deletes = new LinkedList<>();
    for (String documentLink : documentLinks) {
      Operation deleteOperation =
          Operation.createDelete(UriUtils.buildUri(host, documentLink))
              .setBody("{}")
              .setReferer(UriUtils.buildUri(host, referrer));
      deletes.add(deleteOperation);
    }

    OperationJoin join = OperationJoin.create(deletes);
    join.setCompletion(handler);
    join.sendWith(host);
    if (!latch.await(timeout, timeUnit)) {
      throw new TimeoutException(String.format(
          "Deletion of all documents timed out. Timeout:{%s}, TimeUnit:{%s}",
          timeout,
          timeUnit));
    }
  }

  /**
   * Constructs a URI to start service under.
   *
   * @param host
   * @param service
   * @param path
   * @return
   */
  private static URI buildServiceUri(ServiceHost host, Class service, String path) {
    URI serviceUri;

    String error;
    if (path != null) {
      serviceUri = UriUtils.buildUri(host, path);
      error = String.format("Invalid path for starting service [%s]", path);
    } else {
      serviceUri = UriUtils.buildUri(host, service);
      error = String.format("No SELF_LINK field in class %s", service.getCanonicalName());
    }

    if (serviceUri == null) {
      throw new InvalidParameterException(error);
    }

    return serviceUri;
  }

  /**
   * Stop the host and cleanup its sandbox folder.
   *
   * @param host
   * @throws Throwable
   */
  public static void destroy(ServiceHost host) throws Throwable {
    host.stop();
    File sandbox = new File(host.getStorageSandbox());
    int maxAttempts = 10;
    for (int i = 0; i < maxAttempts; i++) {
      try {
        if (sandbox.exists()) {
          FileUtils.forceDelete(sandbox);
        }
        break;
      } catch (FileNotFoundException ex) {
        if (i == maxAttempts - 1) {
          // If all previous attempts fail then we may see leak of
          // some sandbox files in the sandbox folder. This could happen
          // because this exception may prematurely terminate
          // folder delete operation and leave some files unprocessed.
          throw ex;
        }

        logger.warn("Some file disappeared from the sandbox during deletion, will retry deleting sandbox",
            ex);
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }
    }
  }
}
