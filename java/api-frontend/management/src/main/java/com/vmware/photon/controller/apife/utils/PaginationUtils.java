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

package com.vmware.photon.controller.apife.utils;

import com.vmware.photon.controller.api.common.exceptions.external.InvalidPageSizeException;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A collection of tools that can be used to convert data from one type to another.
 */
public class PaginationUtils {
  /**
   * Convert the data returned as ServiceDocumentQueryResult from Xenon to
   * ResourceList, which is being used by api-fe.
   * <p>
   * The order of the data will be honored.
   *
   * @param documentType
   * @param queryResult
   * @param <T>
   * @return
   */
  public static <T> ResourceList<T> xenonQueryResultToResourceList(Class<T> documentType,
                                                                   ServiceDocumentQueryResult queryResult) {

    // The documents links stored in documentLinks are sorted while documents are not, and
    // the following loop iterates on the documentLinks to preserve this order.
    List<T> documents = new ArrayList<>();
    if (queryResult.documentLinks != null) {
      for (String link : queryResult.documentLinks) {
        documents.add(Utils.fromJson(queryResult.documents.get(link), documentType));
      }
    }

    ResourceList<T> resourceList = new ResourceList<>();
    resourceList.setItems(documents);
    resourceList.setNextPageLink(queryResult.nextPageLink);
    resourceList.setPreviousPageLink(queryResult.prevPageLink);

    return resourceList;
  }

  /**
   * Convert the data returned as ServiceDocumentQueryResult from Xenon to
   * ResourceList, which is being used by api-fe.
   * <p>
   * The order of the data will be honored.
   *
   * @param documentType
   * @param queryResult
   * @param convert
   * @param <T>
   * @return
   */
  public static <T, S> ResourceList<T> xenonQueryResultToResourceList(Class<S> documentType,
                                                                      ServiceDocumentQueryResult queryResult,
                                                                      Function<S, T> convert) {
    // The documents links stored in documentLinks are sorted while documents are not, and
    // the following loop iterates on the documentLinks to preserve this order.
    List<T> documents = new ArrayList<>();
    if (queryResult.documentLinks != null) {
      for (String link : queryResult.documentLinks) {
        documents.add(convert.apply(Utils.fromJson(queryResult.documents.get(link), documentType)));
      }
    }

    ResourceList<T> resourceList = new ResourceList<>();
    resourceList.setItems(documents);
    resourceList.setNextPageLink(queryResult.nextPageLink);
    resourceList.setPreviousPageLink(queryResult.prevPageLink);

    return resourceList;
  }

  /**
   * The page links returned from xenon cannot be directly used. This util
   * function is to formalize it to a format like "/tasks?pageLink=xxxxxx"
   * @param resourceList
   * @param apiRoute
   * @param <T>
   * @return
   */
  public static <T> ResourceList<T> formalizePageLinks(ResourceList<T> resourceList, String apiRoute) {
    if (resourceList != null) {
      if (resourceList.getNextPageLink() != null) {
        resourceList.setNextPageLink(apiRoute + "?pageLink=" + resourceList.getNextPageLink());
      }

      if (resourceList.getPreviousPageLink() != null) {
        resourceList.setPreviousPageLink(apiRoute + "?pageLink=" + resourceList.getPreviousPageLink());
      }
    }

    return resourceList;
  }

  /**
   * Determine the page size to be used.
   *
   * 1. If origPageSize is empty, then the one defined in paginationConfig is to be used.
   * 2. If origPageSize is larger than the maxPageSize defined in paginationConfig, throw exception.
   *
   * @param paginationConfig
   * @param origPageSize
   * @return
   */
  public static Optional<Integer> determinePageSize(PaginationConfig paginationConfig,
                                                    Optional<Integer> origPageSize) throws InvalidPageSizeException {

    if (!origPageSize.isPresent()) {
      return Optional.of(paginationConfig.getDefaultPageSize());
    } else if (origPageSize.get() < 1 || origPageSize.get() > paginationConfig.getMaxPageSize()) {
      throw new InvalidPageSizeException(origPageSize.get(), 1, paginationConfig.getMaxPageSize());
    } else {
      return origPageSize;
    }
  }
}
