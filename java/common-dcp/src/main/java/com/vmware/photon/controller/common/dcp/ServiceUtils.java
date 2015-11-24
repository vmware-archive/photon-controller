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

package com.vmware.photon.controller.common.dcp;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.Service;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceDocumentDescription;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.common.UtilsHelper;
import com.vmware.photon.controller.common.logging.LoggingUtils;
import com.vmware.photon.controller.common.thrift.ServerSet;

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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Utility functions for DCP services.
 */
public class ServiceUtils {

  /**
   * Default expiration time for this service is 1 days.
   */
  public static final long DEFAULT_DOC_EXPIRATION_TIME = TimeUnit.DAYS.toMillis(1);
  private static Random randomGenerator = new Random(System.currentTimeMillis());

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
   * From the current time, adding the given number of milliseconds.
   *
   * @param duration The number of milliseconds to extend.
   * @return (current time + milliSecs) in microseconds
   */
  public static long computeExpirationTime(long duration) {
    return Utils.getNowMicrosUtc() + TimeUnit.MILLISECONDS.toMicros(duration);
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
   * This method generates a document template with expanded indexing for one or more fields. This is required for
   * queries over non-PODO types such as collections and maps, and for complex embedded structures.
   *
   * @param sd         Supplies the base document template for the service in question.
   * @param fieldNames Supplies a list of field names for which expanded indexing should be enabled.
   * @return On success, the return value is a document template with expanded indexing enabled.
   */
  public static ServiceDocument getDocumentTemplateWithIndexedFields(ServiceDocument sd, String... fieldNames) {
    for (String fieldName : fieldNames) {

      ServiceDocumentDescription.PropertyDescription propertyDescription =
          sd.documentDescription.propertyDescriptions.get(fieldName);

      propertyDescription.indexingOptions = EnumSet.of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND);
    }

    return sd;
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

  public static void failOperationAsBadRequest(Operation operation, Throwable e) {
    operation.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
    operation.fail(e);
  }
}
