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
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.common.ServiceDocumentQueryResult;
import com.vmware.dcp.common.TaskState;
import com.vmware.dcp.common.UriUtils;
import com.vmware.dcp.common.Utils;
import com.vmware.dcp.common.http.netty.NettyHttpServiceClient;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.thrift.ServerSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * REST client to access DCP services.
 */
public class DcpRestClient implements DcpClient {

  private static final long POST_OPERATION_EXPIRATION_MICROS = TimeUnit.SECONDS.toMicros(60);
  private long postOperationExpirationMicros = POST_OPERATION_EXPIRATION_MICROS;
  private static final long GET_OPERATION_EXPIRATION_MICROS = TimeUnit.SECONDS.toMicros(120);
  private long getOperationExpirationMicros = GET_OPERATION_EXPIRATION_MICROS;
  private static final long QUERY_OPERATION_EXPIRATION_MICROS = TimeUnit.SECONDS.toMicros(60);
  private long queryOperationExpirationMicros = QUERY_OPERATION_EXPIRATION_MICROS;
  private static final long DELETE_OPERATION_EXPIRATION_MICROS = TimeUnit.SECONDS.toMicros(60);
  private long deleteOperationExpirationMicros = DELETE_OPERATION_EXPIRATION_MICROS;
  private static final long PATCH_OPERATION_EXPIRATION_MICROS = TimeUnit.SECONDS.toMicros(60);
  private long patchOperationExpirationMicros = PATCH_OPERATION_EXPIRATION_MICROS;
  private static final long DEFAULT_OPERATION_LATCH_TIMEOUT_MICROS = TimeUnit.SECONDS.toMicros(90);
  private static final long SERVICE_DOCUMENT_STATUS_CHECK_INTERVAL_MILLIS = TimeUnit.MILLISECONDS.toMillis(100);
  private long serviceDocumentStatusCheckIntervalMillis = SERVICE_DOCUMENT_STATUS_CHECK_INTERVAL_MILLIS;
  private static final Logger logger = LoggerFactory.getLogger(DcpRestClient.class);
  private NettyHttpServiceClient client;
  private ServerSet serverSet;
  private URI localHostUri;
  private InetAddress localHostInetAddress;


  @Inject
  public DcpRestClient(ServerSet serverSet, ExecutorService executor) {
    checkNotNull(serverSet, "Cannot construct DcpRestClient with null serverSet");
    checkNotNull(executor, "Cannot construct DcpRestClient with null executor");

    this.serverSet = serverSet;
    try {
      client = (NettyHttpServiceClient) NettyHttpServiceClient.create(
          DcpRestClient.class.getCanonicalName(),
          executor,
          Executors.newScheduledThreadPool(0));
    } catch (URISyntaxException uriSyntaxException) {
      logger.error("ctor: URISyntaxException={}", uriSyntaxException.toString());
      throw new RuntimeException(uriSyntaxException);
    }

    this.localHostUri = OperationUtils.getLocalHostUri();
    this.localHostInetAddress = OperationUtils.getLocalHostInetAddress();
  }

  public void start() {
    client.start();
    logger.info("client started");
  }

  public void stop() {
    client.stop();
    logger.info("client stopped");
  }

  @Override
  public Operation post(String serviceSelfLink, ServiceDocument body)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {
    URI serviceUri = getServiceUri(serviceSelfLink);

    Operation postOperation = Operation
        .createPost(serviceUri)
        .setUri(serviceUri)
        .setExpiration(Utils.getNowMicrosUtc() + getPostOperationExpirationMicros())
        .setBody(body)
        .setReferer(this.localHostUri);

    return send(postOperation);
  }

  @Override
  public Operation get(String documentSelfLink)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {
    URI serviceUri = getServiceUri(documentSelfLink);

    Operation getOperation = Operation
        .createGet(serviceUri)
        .setUri(serviceUri)
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setExpiration(Utils.getNowMicrosUtc() + getGetOperationExpirationMicros())
        .setReferer(this.localHostUri);

    return send(getOperation);
  }

  @Override
  public Operation get(URI documentServiceUri)
    throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {

    Operation getOperation = Operation
        .createGet(documentServiceUri)
        .setUri(documentServiceUri)
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setExpiration(Utils.getNowMicrosUtc() + getGetOperationExpirationMicros())
        .setReferer(this.localHostUri);

    return send(getOperation);
  }

  @Override
  public Operation delete(String documentSelfLink, ServiceDocument body)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {
    URI serviceUri = getServiceUri(documentSelfLink);

    Operation deleteOperation = Operation
        .createDelete(serviceUri)
        .setUri(serviceUri)
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setExpiration(Utils.getNowMicrosUtc() + getDeleteOperationExpirationMicros())
        .setReferer(this.localHostUri)
        .setBody(body);

    return send(deleteOperation);
  }

  @Override
  public Operation postToBroadcastQueryService(QueryTask.QuerySpecification spec)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {

    URI serviceUri = UriUtils.buildBroadcastRequestUri(
        getServiceUri(ServiceUriPaths.CORE_LOCAL_QUERY_TASKS),
        ServiceUriPaths.DEFAULT_NODE_SELECTOR);

    QueryTask query = QueryTask.create(spec)
        .setDirect(true);

    Operation queryOperation = Operation
        .createPost(serviceUri)
        .setUri(serviceUri)
        .setExpiration(Utils.getNowMicrosUtc() + getQueryOperationExpirationMicros())
        .setBody(query)
        .setReferer(this.localHostUri);

    return send(queryOperation);
  }

  @Override
  public Operation patch(String serviceSelfLink, ServiceDocument body)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {
    URI serviceUri = getServiceUri(serviceSelfLink);

    Operation patchOperation = Operation
        .createPatch(serviceUri)
        .setUri(serviceUri)
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_QUEUING)
        .setExpiration(Utils.getNowMicrosUtc() + getPatchOperationExpirationMicros())
        .setBody(body)
        .setReferer(this.localHostUri);

    return send(patchOperation);
  }

  @Override
  public Operation query(QueryTask.QuerySpecification spec, boolean isDirect)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {

    URI queryFactoryUri = getServiceUri(ServiceUriPaths.CORE_QUERY_TASKS);

    QueryTask query = QueryTask.create(spec);
    query.setDirect(isDirect);

    Operation queryOperation = Operation
        .createPost(queryFactoryUri)
        .setUri(queryFactoryUri)
        .setExpiration(Utils.getNowMicrosUtc() + getQueryOperationExpirationMicros())
        .setBody(query)
        .setReferer(this.localHostUri);

    URI queryServiceUri = UriUtils.extendUri(queryFactoryUri, query.documentSelfLink);

    return send(queryOperation);
  }

  /**
   * Executes a DCP query which will query for documents of type T.
   * Any other filter clauses are optional.
   * This allows for a query that returns all documents of type T.
   * This also expands the content of the resulting documents.
   *
   * @param documentType
   * @param terms
   * @param <T>
   * @return
   * @throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException
   */
  @Override
  public <T extends ServiceDocument> List<T> queryDocuments(Class<T> documentType,
                                                            ImmutableMap<String, String> terms)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {
    checkNotNull(documentType, "Cannot query documents with null documentType");

    QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(documentType, terms);
    spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    Operation result = postToBroadcastQueryService(spec);

    return QueryTaskUtils.getQueryResultDocuments(documentType, result);
  }

  /**
   * Executes a DCP query which queries for documents of type T.
   * The query terms are optional.
   * The pageSize is also optional. If it is not provided, the complete document will be retrieved.
   *
   * @param documentType
   * @param terms
   * @param pageSize
   * @param expandContent
   * @param <T>
   * @return
   * @throws BadRequestException
   * @throws DocumentNotFoundException
   * @throws TimeoutException
   * @throws InterruptedException
   */
  @Override
  public <T extends ServiceDocument> ServiceDocumentQueryResult queryDocuments(Class<T> documentType,
                                                                               ImmutableMap<String, String> terms,
                                                                               Optional<Integer> pageSize,
                                                                               boolean expandContent)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {

    checkNotNull(documentType, "Cannot query documents with null documentType");
    if (pageSize.isPresent()) {
      checkArgument(pageSize.get() >= 1, "Cannot query documents with a page size less than 1");
    }

    QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(documentType, terms);
    spec.options = EnumSet.of(QueryTask.QuerySpecification.QueryOption.BROADCAST);
    if (expandContent) {
      spec.options.add(QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT);
    }
    if (pageSize.isPresent()) {
      spec.resultLimit = pageSize.get();
    }

    // Indirect call. DCP will not return the results. Instead the service URI
    // established will be obtained here, and it will be used to get the results
    // after the query is in FINISHED stage.
    Operation result = query(spec, false);
    URI queryServiceUri = QueryTaskUtils.getServiceDocumentUri(result);

    // Wait for the query task to finish and then retrieve the documents
    result = waitForTaskToFinish(queryServiceUri);
    return result.getBody(QueryTask.class).results;
  }

  /**
   * Query a document page using the given page link.
   *
   * @param pageLink
   * @return
   * @throws BadRequestException
   * @throws DocumentNotFoundException
   * @throws TimeoutException
   * @throws InterruptedException
   */
  @Override
  public ServiceDocumentQueryResult queryDocumentPage(String pageLink)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {

    checkNotNull(pageLink, "Cannot query documents with null pageLink");
    checkArgument(!pageLink.isEmpty(), "Cannot query documents with empty pageLink");

    Operation result = get(pageLink);

    return result.getBody(QueryTask.class).results;
  }

  /**
   * Executes a DCP query which will query for documents of type T.
   * Any other filter clauses are optional.
   * This allows for a query that returns all documents of type T.
   * This returns the links to the resulting documents.
   *
   * @param documentType
   * @param terms
   * @param <T>
   * @return
   * @throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException
   */
  @Override
  public <T extends ServiceDocument> List<String> queryDocumentsForLinks(Class<T> documentType,
                                                                         ImmutableMap<String, String> terms)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {
    checkNotNull(documentType, "Cannot query documents with null documentType");

    QueryTask.QuerySpecification spec = QueryTaskUtils.buildQuerySpec(documentType, terms);
    Operation result = postToBroadcastQueryService(spec);
    Set<String> documentLinks = QueryTaskUtils.getQueryResultDocumentLinks(result);

    if (documentLinks.size() <= 0) {
      return ImmutableList.of();
    }

    return ImmutableList.copyOf(documentLinks);
  }

  /**
   * This method sifts through errors from DCP operations into checked and unchecked(RuntimeExceptions)
   * This is the default handling but it can be overridden by different clients based on their needs.
   *
   * @param operation
   * @param operationResult
   * @return
   * @throws DocumentNotFoundException
   * @throws TimeoutException
   */
  @VisibleForTesting
  protected Operation handleOperationResult(Operation operation, OperationLatch.OperationResult operationResult)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {

    switch (operationResult.completedOperation.getStatusCode()) {
      case Operation.STATUS_CODE_OK:
      case Operation.STATUS_CODE_ACCEPTED:
        return operationResult.completedOperation;
      case Operation.STATUS_CODE_NOT_FOUND:
        throw new DocumentNotFoundException(operation, operationResult);
      case Operation.STATUS_CODE_TIMEOUT:
        TimeoutException timeoutException;
        if (operationResult.operationFailure instanceof TimeoutException) {
          timeoutException = (TimeoutException) operationResult.operationFailure;
        } else {
          timeoutException = new TimeoutException(operationResult.operationFailure.getMessage());
          timeoutException.initCause(operationResult.operationFailure);
        }
        handleTimeoutException(operationResult.completedOperation, timeoutException);
        break;
      case Operation.STATUS_CODE_BAD_REQUEST:
        throw new BadRequestException(operation, operationResult);
      default:
        handleUnknownError(operation, operationResult);
    }

    return null;
  }

  @VisibleForTesting
  protected void handleTimeoutException(Operation operation, TimeoutException timeoutException)
      throws TimeoutException {
    logger.warn("send: TIMEOUT {}, Message={}",
        createLogMessageWithStatusAndBody(operation),
        timeoutException.getMessage());
    throw timeoutException;
  }

  @VisibleForTesting
  protected void handleInterruptedException(Operation operation, InterruptedException interruptedException)
      throws InterruptedException {
    logger.warn("send: INTERRUPTED {}, Exception={}",
        createLogMessageWithStatusAndBody(operation),
        interruptedException);

    throw interruptedException;
  }

  @VisibleForTesting
  protected OperationLatch createOperationLatch(Operation operation) {
    return new OperationLatch(operation);
  }

  @VisibleForTesting
  protected Operation send(Operation operation)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {
    // logger.info("send: STARTED {}", createLogMessageWithBody(operation));
    OperationLatch operationLatch = createOperationLatch(operation);

    client.send(operation);

    Operation completedOperation = null;
    try {
      OperationLatch.OperationResult operationResult =
          operationLatch.awaitForOperationResult(DEFAULT_OPERATION_LATCH_TIMEOUT_MICROS);
      logCompletedOperation(operationResult.completedOperation);
      completedOperation = handleOperationResult(operation, operationResult);
    } catch (TimeoutException timeoutException) {
      handleTimeoutException(operation, timeoutException);
    } catch (InterruptedException interruptedException) {
      handleInterruptedException(operation, interruptedException);
    }
    //this maybe null due to client side exceptions caught above.
    return completedOperation;
  }

  @VisibleForTesting
  protected long getPostOperationExpirationMicros() {
    return postOperationExpirationMicros;
  }

  @VisibleForTesting
  protected long getGetOperationExpirationMicros() {
    return getOperationExpirationMicros;
  }

  @VisibleForTesting
  protected long getQueryOperationExpirationMicros() {
    return queryOperationExpirationMicros;
  }

  @VisibleForTesting
  protected long getDeleteOperationExpirationMicros() {
    return deleteOperationExpirationMicros;
  }

  @VisibleForTesting
  protected long getPatchOperationExpirationMicros() {
    return patchOperationExpirationMicros;
  }

  protected int getPort(InetSocketAddress inetSocketAddress) {
    return inetSocketAddress.getPort();
  }

  private void handleUnknownError(Operation operation, OperationLatch.OperationResult operationResult) {
    throw new DcpRuntimeException(operation, operationResult);
  }

  private InetSocketAddress getRandomInetSocketAddress() {
    // we need to getServers every time to support dynamic addition and removal of servers.
    return ServiceUtils.selectRandomItem(serverSet.getServers());
  }

  @VisibleForTesting
  protected URI getServiceUri(String path) {

    //check if any of the hosts are available locally
    Optional<InetSocketAddress> localInetSocketAddress =
        this.serverSet.getServers().stream().filter(
            (InetSocketAddress i) -> i.getAddress().equals(this.localHostInetAddress))
            .findFirst();

    InetSocketAddress selectedInetSocketAddress;
    if (localInetSocketAddress.isPresent()) {
      // let the dcp host decide if a network hop across hosts is required for
      // the requested operation.
      selectedInetSocketAddress = localInetSocketAddress.get();
    } else {
      selectedInetSocketAddress = getRandomInetSocketAddress();
    }

    int port = getPort(selectedInetSocketAddress);
    String address = selectedInetSocketAddress.getAddress().getHostAddress();

    try {
      return new URI("http", null, address, port, path, null, null);
    } catch (URISyntaxException uriSyntaxException) {
      logger.error("createUriFromServerSet: URISyntaxException path={} exception={} port={}",
          path, uriSyntaxException, port);
      throw new RuntimeException(uriSyntaxException);
    }
  }

  private void logCompletedOperation(Operation completedOperation) {
    if (completedOperation.getStatusCode() == Operation.STATUS_CODE_OK) {
      switch (completedOperation.getAction()) {
        case DELETE:
          // fall through
        case PATCH:
          // fall through
        case PUT:
          // for successful DELETE, PATCH and PUT we do not need to log the status and body.
          /*logger.info("send: SUCCESS {}",
              createLogMessageWithoutStatusAndBody(completedOperation));*/
          break;
        case POST:
          // fall through
        case GET:
          // fall through
        default:
          // for successful POST and GET we do not need to log the status,
          // but we need need to log the body to see what was returned for the posted query or get.
          /*logger.info("send: SUCCESS {}",
              createLogMessageWithBody(completedOperation));*/
      }
    } else {
      if (completedOperation.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
        // logger.info("send: COMPLETED {}", createLogMessageWithStatus(completedOperation));
      } else {
        // logger.warn("send: WARN {}", createLogMessageWithStatusAndBody(completedOperation));
      }
    }
  }

  private String createLogMessageWithoutStatusAndBody(Operation operation) {
    return String.format(
        "Action={%s}, OperationId={%s}, Uri={%s}, Referer={%s}, jsonBody={NOT LOGGED}",
        operation.getAction(),
        operation.getId(),
        operation.getUri(),
        operation.getReferer());
  }

  private String createLogMessageWithBody(Operation operation) {
    return String.format(
        "Action={%s}, OperationId={%s}, Uri={%s}, Referer={%s}, jsonBody={%s}",
        operation.getAction(),
        operation.getId(),
        operation.getUri(),
        operation.getReferer(),
        Utils.toJson(operation.getBodyRaw()));
  }

  private String createLogMessageWithStatus(Operation operation) {
    return String.format(
        "Action={%s}, StatusCode={%s}, OperationId={%s}, Uri={%s}, Referer={%s}, jsonBody={NOT LOGGED}",
        operation.getAction(),
        operation.getStatusCode(),
        operation.getId(),
        operation.getUri(),
        operation.getReferer());
  }

  private String createLogMessageWithStatusAndBody(Operation operation) {
    return String.format(
        "Action={%s}, StatusCode={%s}, OperationId={%s}, Uri={%s}, Referer={%s}, jsonBody={%s}",
        operation.getAction(),
        operation.getStatusCode(),
        operation.getId(),
        operation.getUri(),
        operation.getReferer(),
        Utils.toJson(operation.getBodyRaw()));
  }

  private Operation waitForTaskToFinish(URI serviceUri)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException {

    Operation result = null;
    do {
      result = get(serviceUri);
      TaskState.TaskStage taskStage = QueryTaskUtils.getServiceState(result);
      if (taskStage == TaskState.TaskStage.FINISHED
          || taskStage == TaskState.TaskStage.FAILED
          || taskStage == TaskState.TaskStage.CANCELLED) {

        return result;
      }

      Thread.sleep(serviceDocumentStatusCheckIntervalMillis);
    } while (Utils.getNowMicrosUtc() <= result.getExpirationMicrosUtc());

    throw new TimeoutException(String.format("Timeout:{%s}, TimeUnit:{%s}", result.getExpirationMicrosUtc(),
        TimeUnit.MICROSECONDS));
  }
}
