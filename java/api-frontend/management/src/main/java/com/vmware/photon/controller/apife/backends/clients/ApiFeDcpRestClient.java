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

package com.vmware.photon.controller.apife.backends.clients;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.dcp.services.common.QueryTask;
import com.vmware.photon.controller.apife.BackendTaskExecutor;
import com.vmware.photon.controller.common.CloudStoreServerSet;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DcpRuntimeException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.thrift.ServerSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * Http rest client to talk to DCP.
 * This class allows for injection of CloudStoreServerSet and executor specific to API-FE
 */
@Singleton
public class ApiFeDcpRestClient extends DcpRestClient {

  private static final Logger logger = LoggerFactory.getLogger(ApiFeDcpRestClient.class);

  @Inject
  public ApiFeDcpRestClient(@CloudStoreServerSet ServerSet serverSet,
                            @BackendTaskExecutor ExecutorService executor) {
    super(serverSet, executor);
  }

  @Override
  public Operation post(String serviceSelfLink, ServiceDocument body) {

    try {
      return super.post(serviceSelfLink, body);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new DcpRuntimeException(documentNotFoundException);
    } catch (BadRequestException badRequestException) {
      throw new DcpRuntimeException(badRequestException);
    } catch (TimeoutException timeoutException) {
      throw new RuntimeException(timeoutException);
    } catch (InterruptedException interruptedException) {
      throw new RuntimeException(interruptedException);
    }
  }

  @Override
  public Operation get(String documentSelfLink) throws DocumentNotFoundException {
    try {
      return super.get(documentSelfLink);
    } catch (BadRequestException badRequestException) {
      throw new DcpRuntimeException(badRequestException);
    } catch (TimeoutException timeoutException) {
      throw new RuntimeException(timeoutException);
    } catch (InterruptedException interruptedException) {
      throw new RuntimeException(interruptedException);
    }
  }

  @Override
  public Operation delete(String documentSelfLink, ServiceDocument body) {
    try {
      return super.delete(documentSelfLink, body);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new DcpRuntimeException(documentNotFoundException);
    } catch (BadRequestException badRequestException) {
      throw new DcpRuntimeException(badRequestException);
    } catch (TimeoutException timeoutException) {
      throw new RuntimeException(timeoutException);
    } catch (InterruptedException interruptedException) {
      throw new RuntimeException(interruptedException);
    }
  }

  @Override
  public Operation postToBroadcastQueryService(QueryTask.QuerySpecification spec) {
    try {
      return super.postToBroadcastQueryService(spec);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new DcpRuntimeException(documentNotFoundException);
    } catch (BadRequestException badRequestException) {
      throw new DcpRuntimeException(badRequestException);
    } catch (TimeoutException timeoutException) {
      throw new RuntimeException(timeoutException);
    } catch (InterruptedException interruptedException) {
      throw new RuntimeException(interruptedException);
    }
  }

  @Override
  public Operation patch(String serviceSelfLink, ServiceDocument body)
      throws DocumentNotFoundException {
    try {
      return super.patch(serviceSelfLink, body);
    } catch (BadRequestException badRequestException) {
      throw new DcpRuntimeException(badRequestException);
    } catch (TimeoutException timeoutException) {
      throw new RuntimeException(timeoutException);
    } catch (InterruptedException interruptedException) {
      throw new RuntimeException(interruptedException);
    }
  }

  @Override
  public <T extends ServiceDocument> List<T> queryDocuments(Class<T> documentType,
                                                            ImmutableMap<String, String> terms) {
    try {
      return super.queryDocuments(documentType, terms);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new DcpRuntimeException(documentNotFoundException);
    } catch (BadRequestException badRequestException) {
      throw new DcpRuntimeException(badRequestException);
    } catch (TimeoutException | InterruptedException exception) {
      throw new RuntimeException(exception);
    }
  }

  @Override
  public <T extends ServiceDocument> List<String> queryDocumentsForLinks(Class<T> documentType,
                                                                         ImmutableMap<String, String> terms) {
    try {
      return super.queryDocumentsForLinks(documentType, terms);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new DcpRuntimeException(documentNotFoundException);
    } catch (BadRequestException badRequestException) {
      throw new DcpRuntimeException(badRequestException);
    } catch (TimeoutException | InterruptedException exception) {
      throw new RuntimeException(exception);
    }
  }

  @VisibleForTesting
  @Override
  protected void handleTimeoutException(Operation operation, TimeoutException timeoutException) {
    //API-FE does not handle timeout exception currently hence converting it to RuntimeException

    logger.warn("ApiFeDcpRestClient.send: TIMEOUT Operation={}, Message={}",
        operation,
        timeoutException.getMessage());
    throw new RuntimeException(timeoutException);
  }

  @VisibleForTesting
  @Override
  protected void handleInterruptedException(Operation operation, InterruptedException interruptedException) {
    logger.warn("ApiFeDcpRestClient.send: INTERRUPTED Operation={}, Exception={}",
        operation,
        interruptedException);

    //API-FE does not support task cancellation at this time
    //set cancellation flag again and defer its handling to higher up the stack
    //stack above may opt-in to look at the interrupted state of the thread if it chooses to
    //see http://www.ibm.com/developerworks/java/library/j-jtp05236/index.html
    Thread.currentThread().interrupt();
  }

}
