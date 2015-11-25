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

import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * REST client API to access DCP services.
 */
public interface DcpClient {
  void start();

  void stop();

  Operation post(String serviceSelfLink, ServiceDocument body)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  Operation get(String documentSelfLink)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  Operation get(URI documentServiceUri)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  Operation postToBroadcastQueryService(QueryTask.QuerySpecification spec)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  Operation delete(String documentSelfLink, ServiceDocument body)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  Operation patch(String serviceSelfLink, ServiceDocument body)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  Operation query(QueryTask.QuerySpecification spec, boolean isDirect)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  <T extends ServiceDocument> List<T> queryDocuments(Class<T> documentType,
                                                     ImmutableMap<String, String> terms)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  <T extends ServiceDocument> ServiceDocumentQueryResult queryDocuments(Class<T> documentType,
                                                                        ImmutableMap<String, String> terms,
                                                                        Optional<Integer> pageSize,
                                                                        boolean expandContent)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  ServiceDocumentQueryResult queryDocumentPage(String pageLink)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;

  <T extends ServiceDocument> List<String> queryDocumentsForLinks(Class<T> documentType,
                                                                  ImmutableMap<String, String> terms)
      throws BadRequestException, DocumentNotFoundException, TimeoutException, InterruptedException;
}
