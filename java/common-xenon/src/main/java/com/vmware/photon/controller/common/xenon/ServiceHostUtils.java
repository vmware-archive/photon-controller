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

  private static final long WAIT_ITERATION_SLEEP = 500;

  private static final long WAIT_ITERATION_COUNT = 30000 / WAIT_ITERATION_SLEEP; // 30 seconds.

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

    List<Pair<String, Integer>> remoteHostIpAndPortPairs = new ArrayList<>();
    for (ServiceHost host : hosts) {
      remoteHostIpAndPortPairs.add(Pair.of(host.getState().bindAddress, host.getPort()));
    }

    waitForNodeGroupConvergence(
        hosts[0],
        remoteHostIpAndPortPairs,
        nodeGroupPath,
        maxRetries,
        retryInterval);
  }

  public static void waitForNodeGroupConvergence(
      ServiceHost localHost,
      Collection<Pair<String, Integer>> remoteHostIpAndPortPairs,
      String nodeGroupPath,
      int maxRetries,
      int retryInterval) throws Throwable {
    checkArgument(localHost != null, "localHost cannot be null");
    checkArgument(remoteHostIpAndPortPairs != null, "remoteHostIpAndPortPairs cannot be null");
    checkArgument(!Strings.isNullOrEmpty(nodeGroupPath), "nodeGroupPath cannot be null or empty");
    checkArgument(maxRetries > 0, "maxRetries must be > 0");

    for (Pair<String, Integer> remoteHostIpAndPortPair : remoteHostIpAndPortPairs) {

      int checkRetries = maxRetries;
      int checksToConvergence = REQUIRED_STABLE_STATE_COUNT;
      while (checkRetries > 0 && checksToConvergence > 0) {
        // update retry count and sleep
        checkRetries--;
        Thread.sleep(retryInterval * checksToConvergence);

        // check the host response
        NodeGroupService.NodeGroupState response = getNodeGroupState(
            localHost,
            remoteHostIpAndPortPair.getKey(),
            remoteHostIpAndPortPair.getValue(),
            nodeGroupPath);
        if (response.nodes.size() < remoteHostIpAndPortPairs.size()) {
          continue;
        }

        // check host status
        checksToConvergence--;
        for (NodeState nodeState : response.nodes.values()) {
          if (nodeState.status != NodeState.NodeStatus.AVAILABLE) {
            checksToConvergence = REQUIRED_STABLE_STATE_COUNT;
            break;
            // Note that we are not breaking from the above while loop where checksToConvergence is done
            // This is because the nodes might switch between AVAILABLE and SYNCHRONIZING as the other nodes join
          }
        }
      }

      if (checkRetries == 0) {
        throw new TimeoutException("nodes did not converge");
      }
    }
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
          String.format("One or several of service(s) %s not available", Utils.toJson(serviceLinks)));
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
    return waitForQuery(host, referrer, query, predicate, WAIT_ITERATION_COUNT, WAIT_ITERATION_SLEEP);
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
    return waitForServiceState(type, serviceUri, predicate, host, WAIT_ITERATION_SLEEP, WAIT_ITERATION_COUNT, cleanup);
  }


  /**
   * Wait until service state satisfies the given predicate.
   */
  public static <T> T waitForServiceState(final Class<T> type, final String serviceUri,
                                          Predicate<T> predicate,
                                          final ServiceHost host,
                                          long waitIterationSleep,
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
        waitIterationSleep,
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
    return waitForState(supplier, predicate, WAIT_ITERATION_SLEEP, WAIT_ITERATION_COUNT, cleanup, timeoutMessage);
  }

  /**
   * Generic wait function.
   */
  public static <T> T waitForState(Supplier<T> supplier, Predicate<T> predicate,
                                   long waitIterationSleep, long waitIterationCount,
                                   Runnable cleanup, String timeoutMessage)
      throws Throwable {
    for (int i = 0; i < waitIterationCount; i++) {
      T t = supplier.get();
      if (predicate.test(t)) {
        return t;
      }
      Thread.sleep(waitIterationSleep);
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
            Utils.toJson(result.getBodyRaw())));
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
