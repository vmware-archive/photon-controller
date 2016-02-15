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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.common.exceptions.external.PageExpiredException;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ImageNotFoundException;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageService;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageServiceFactory;
import com.vmware.photon.controller.cloudstore.xenon.entity.ImageToImageDatastoreMappingService;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods to support the backend classes.
 */
public class BackendHelpers {

  /**
   * Check if seeding is done for the given image.
   *
   * @param dcpRestClient
   * @param imageId
   * @return
   * @throws ExternalException
   */
  public static boolean isImageSeedingDone(ApiFeDcpRestClient dcpRestClient,
                                           String imageId) throws ExternalException {

    try {
      ImageService.State state = dcpRestClient.get(ImageServiceFactory.SELF_LINK + "/" + imageId)
          .getBody(ImageService.State.class);
      return state.totalImageDatastore == state.replicatedImageDatastore;
    } catch (DocumentNotFoundException e) {
      throw new ImageNotFoundException(ImageNotFoundException.Type.ID, imageId);
    }
  }

  /**
   * Get the list of image datastores that have been seeded with the given image.
   *
   * @param dcpRestClient
   * @param imageId
   * @return
   */
  public static List<String> getSeededImageDatastores(ApiFeDcpRestClient dcpRestClient,
                                                      String imageId) throws ExternalException {

    final ImmutableMap.Builder<String, String> termsBuilder = new ImmutableMap.Builder<>();
    termsBuilder.put("imageId", imageId);

    ServiceDocumentQueryResult queryResult = dcpRestClient
        .queryDocuments(ImageToImageDatastoreMappingService.State.class,
            termsBuilder.build(), Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE), true);

    List<String> seededImageDatastores = new ArrayList<>();
    queryResult.documents.values().forEach(item -> {
      seededImageDatastores.add(Utils.fromJson(item, ImageToImageDatastoreMappingService.State.class).imageDatastoreId);
    });

    try {
      while (StringUtils.isNotBlank(queryResult.nextPageLink)) {
        queryResult = dcpRestClient.queryDocumentPage(queryResult.nextPageLink);
        queryResult.documents.values().forEach(item -> {
          seededImageDatastores
              .add(Utils.fromJson(item, ImageToImageDatastoreMappingService.State.class).imageDatastoreId);
        });
      }
    } catch (DocumentNotFoundException e) {
      throw new PageExpiredException(queryResult.nextPageLink);
    }

    return seededImageDatastores;
  }
}
