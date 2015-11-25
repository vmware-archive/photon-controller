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

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of tools that can be used to convert data from one type to another.
 */
public class DataTypeConversionUtils {
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

    List<T> documents = new ArrayList<>();
    for (String link : queryResult.documentLinks) {
      documents.add(Utils.fromJson(queryResult.documents.get(link), documentType));
    }

    ResourceList<T> resourceList = new ResourceList<>();
    resourceList.setItems(documents);
    resourceList.setNextPageLink(queryResult.nextPageLink);
    resourceList.setPreviousPageLink(queryResult.prevPageLink);

    return resourceList;
  }
}
