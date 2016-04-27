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

import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.UtilsHelper;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility functions for DCP services.
 */
public class ServiceUtils {

  /**
   * Default expiration time for this service is 1 days.
   */
  public static final long DEFAULT_DOC_EXPIRATION_TIME_MICROS = TimeUnit.DAYS.toMicros(1);
  // Keep the expiration large enough to allow Xenon to replicate the DELETEs to all nodes first
  // and also to allow time for debugging deleted documents for live site incidents.
  public static final long DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS = TimeUnit.DAYS.toMicros(30);
  private static Random randomGenerator = new Random(System.currentTimeMillis());

  private static List<String> localHostIpAddresses = OperationUtils.getLocalHostIpAddresses();

  /**
   * Set the task info error fields.
   *
   * @param state
   * @param e
   */
  public static void setTaskInfoErrorStage(TaskState state, Throwable e) {
    state.stage = TaskState.TaskStage.FAILED;
    state.failure = Utils.toServiceErrorResponse(e);
  }

  public static void logInfo(Service service, String fmt, Object... args) {
    LoggerFactory.getLogger(service.getClass()).info(getFmtMsg(service, fmt, args));
  }

  public static void logSevere(Service service, String fmt, Object... args) {
    LoggerFactory.getLogger(service.getClass()).error(getFmtMsg(service, fmt, args));
  }

  public static void logSevere(Service service, Throwable e) {
    LoggerFactory.getLogger(service.getClass()).error(getFmtMsg(service, "%s", Utils.toString(e)));
  }

  public static void logSevere(Service service, Collection<Throwable> errors) {
    for (Throwable e : errors) {
      logSevere(service, e);
    }
  }

  public static void logTrace(Service service, String fmt, Object... args) {
    LoggerFactory.getLogger(service.getClass()).trace(getFmtMsg(service, fmt, args));
  }

  public static void logTrace(Service service, Throwable e) {
    LoggerFactory.getLogger(service.getClass()).trace(getFmtMsg(service, "%s", Utils.toString(e)));
  }

  public static void logWarning(Service service, String fmt, Object... args) {
    LoggerFactory.getLogger(service.getClass()).warn(getFmtMsg(service, fmt, args));
  }

  @VisibleForTesting
  protected static String getFmtMsg(Service service, String fmt, Object... args) {
    String requestId = UtilsHelper.getThreadContextId();

    StringBuilder fmtMsg = new StringBuilder();
    if (requestId != null && !requestId.isEmpty()) {
      fmtMsg.append(LoggingUtils.formatRequestIdLogSection(requestId) + " ");
    }

    fmtMsg.append(String.format("[%s] %s", service.getSelfLink(), String.format(fmt, args)));
    return fmtMsg.toString();
  }

  /**
   * Randomly select one of the items.
   *
   * @return item
   */
  public static <T> T selectRandomItem(Set<T> items) {
    checkNotNull(items, "Cannot selectRandomItem from null items");
    checkArgument(!items.isEmpty(), "Cannot selectRandomItem from empty items");

    randomGenerator.setSeed(System.currentTimeMillis());
    int selectedServerIndex = randomGenerator.nextInt(items.size());

    T item = null;
    Iterator<T> itemsIterator = items.iterator();
    //avoiding use of toArray to prevent unnecessary memory allocations, that method would always iterate through the
    //full list to produce the array, where as this version will short circuit on reaching the selectedServerIndex.
    for (int i = 0; i <= selectedServerIndex && itemsIterator.hasNext(); i++) {
      item = itemsIterator.next();
    }

    return item;
  }

  /**
   * From the current time, adding the given number of microseconds.
   *
   * @param durationMicros The number of microseconds to extend.
   * @return (current time + microseconds) in microseconds
   */
  public static long computeExpirationTime(long durationMicros) {
    return Utils.getNowMicrosUtc() + durationMicros;
  }

  /**
   * Extract document ID from document self link.
   *
   * @param documentSelfLink
   * @return
   */
  public static String getIDFromDocumentSelfLink(String documentSelfLink) {
    int index = documentSelfLink.lastIndexOf('/');
    return documentSelfLink.substring(index + 1);
  }

  /**
   * This method updates the specified document template with expanded indexing for one or more fields. This is
   * required for queries over non-PODO types such as collections and maps, and for complex embedded structures.
   *
   * @param sd         Supplies the base document template for the service in question.
   * @param fieldNames Supplies a list of field names for which expanded indexing should be enabled.
   */
  public static void setExpandedIndexing(ServiceDocument sd, String... fieldNames) {
    setIndexingOptions(sd, ServiceDocumentDescription.PropertyIndexingOption.EXPAND, fieldNames);
  }

  /**
   * This method updates the specified document template with sorted indexing for one or more fields. This is
   * required for sorting the results list of queries over numeric types.
   *
   * @param sd         Supplies the base document template for the service in question.
   * @param fieldNames Supplies a list of field names for which sorted indexing should be enabled.
   */
  public static void setSortedIndexing(ServiceDocument sd, String... fieldNames) {
    setIndexingOptions(sd, ServiceDocumentDescription.PropertyIndexingOption.SORT, fieldNames);
  }

  /**
   * This method updates the indexing options for the fields in the specified document template to add the specified
   * option.
   *
   * @param sd         Supplies the base document template for the service in question.
   * @param option     Supplies an indexing option.
   * @param fieldNames Supplies a list of field names for which the specified indexing option should be enabled.
   */
  public static void setIndexingOptions(ServiceDocument sd,
                                        ServiceDocumentDescription.PropertyIndexingOption option,
                                        String... fieldNames) {
    for (String fieldName : fieldNames) {
      ServiceDocumentDescription.PropertyDescription pd = sd.documentDescription.propertyDescriptions.get(fieldName);
      pd.indexingOptions.add(option);
    }
  }

  /**
   * Compare the hash of two documents.
   *
   * @param type
   * @param document1
   * @param document2
   * @return the value {@code 0} if hash of two documents are equal;
   * a value less than {@code 0} if hash of document1
   * is lexicographically less than hash of document2; and a
   * value greater than {@code 0} otherwise.
   */
  public static <T extends ServiceDocument> boolean documentEquals(
      Class<T> type, T document1, T document2) throws IllegalAccessException {

    ServiceDocumentDescription documentDescription = ServiceDocumentDescription.Builder.create()
        .buildDescription(type, EnumSet.noneOf(Service.ServiceOption.class));

    return ServiceDocument.equals(documentDescription, document1, document2);
  }

  /**
   * Returns the URI of a random server.
   *
   * @param serverSet
   * @param path
   * @return
   * @throws URISyntaxException
   */
  public static URI createUriFromServerSet(ServerSet serverSet, String path) throws
      URISyntaxException {
    return createUriFromServerSet(serverSet.getServers(), path);
  }

  /**
   * Returns the URI of a random server.
   *
   * @param serverInetSet
   * @param path
   * @return
   * @throws URISyntaxException
   */
  public static URI createUriFromServerSet(Set<InetSocketAddress> serverInetSet, String path) throws
      URISyntaxException {
    InetSocketAddress inetSocketAddress = ServiceUtils.selectRandomItem(serverInetSet);
    String address = inetSocketAddress.getHostString();
    int port = inetSocketAddress.getPort();

    URI uri = new URI("http", null, address, port, path, null, null);
    return uri;
  }

  /**
   * From a serverSet, return the URI of the localhost if available, otherwise a random host.
   * @param serverSet the set of servers
   * @param path the path of the desired URI
   * @return the URI
   * @throws URISyntaxException
   */
  public static URI selectLocalServer(ServerSet serverSet, String path) throws
      URISyntaxException {
    Set<InetSocketAddress> serverInetSet = serverSet.getServers();
    java.util.Optional<InetSocketAddress> localInetSocketAddress =
        serverInetSet.stream().filter(
            (InetSocketAddress i) -> localHostIpAddresses.contains(i.getAddress().getHostAddress()))
            .findFirst();

    InetSocketAddress selectedInetSocket;
    if (!localInetSocketAddress.isPresent()) {
      // The local server isn't available--choose a random server
      return createUriFromServerSet(serverInetSet, path);
    }

    // The local server is available, use it
    selectedInetSocket = localInetSocketAddress.get();
    String address = selectedInetSocket.getAddress().getHostAddress();
    int port = selectedInetSocket.getPort();

    return new URI("http", null, address, port, path, null, null);
  }

  public static void failOperationAsBadRequest(Service service, Operation operation, Throwable e) {
    failOperationAsBadRequest(service, operation, e, null);
  }

  public static void failOperationAsBadRequest(Service service,
                                               Operation operation,
                                               Throwable e,
                                               Object failureBody) {
    logSevere(service, e);
    operation.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
    operation.fail(e, failureBody);
  }

  public static Operation doServiceOperation(Service service, Operation requestedOperation)
      throws TimeoutException, DocumentNotFoundException, BadRequestException, InterruptedException {
    OperationLatch syncOp = new OperationLatch(requestedOperation);
    service.sendRequest(requestedOperation);
    Operation completedOperation = syncOp.awaitOperationCompletion();
    return OperationUtils.handleCompletedOperation(requestedOperation, completedOperation);
  }

  /**
   * This method will expire the document based on expiration duration provided.
   * expiration provided is given this precedence:
   * 1. Expiration provided in Delete operation if > 0
   * 2. Expiration provided in the Current state if > 0
   * 3. Expiration provided by ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS
   * The method will complete the operation as well before returning so this should be
   * the last operation in handleDelete implementation of a service.
   *
   * @param service
   * @param serviceDocumentType
   * @param deleteOperation
   * @param <T>
   */
  public static <T extends ServiceDocument> void expireDocumentOnDelete(
      Service service,
      Class<T> serviceDocumentType,
      Operation deleteOperation) {
    ServiceUtils.logInfo(service, "Deleting Service %s", service.getSelfLink());

    final ServiceDocument currentState = service.getState(deleteOperation);

    if (currentState.documentExpirationTimeMicros <= 0) {
      currentState.documentExpirationTimeMicros = ServiceUtils.computeExpirationTime(
          ServiceUtils.DEFAULT_ON_DELETE_DOC_EXPIRATION_TIME_MICROS);
    }

    if (deleteOperation.hasBody()) {
      T deleteState = deleteOperation.getBody(serviceDocumentType);
      if (deleteState.documentExpirationTimeMicros > 0) {
        currentState.documentExpirationTimeMicros = deleteState.documentExpirationTimeMicros;
      }
    }

    if (currentState.documentExpirationTimeMicros > 0) {
      ServiceUtils.logInfo(service,
          "Expiring service %s at %d micros",
          service.getSelfLink(),
          currentState.documentExpirationTimeMicros);
    }

    service.setState(deleteOperation, currentState);
    deleteOperation.complete();
  }
}
