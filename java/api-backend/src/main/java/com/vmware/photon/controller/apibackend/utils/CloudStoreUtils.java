/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apibackend.utils;

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;

import java.util.function.Consumer;

/**
 * This class implements utility functions for cloud-store.
 */
public class CloudStoreUtils {

  public static <E extends ServiceDocument> void getCloudStoreEntityAndProcess(
      Service service,
      String entityLink,
      Class<E> entityType,
      Consumer<E> successConsumer,
      Consumer<Throwable> failureConsumer) {
    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createGet(entityLink)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failureConsumer.accept(ex);
            return;
          }

          successConsumer.accept(op.getBody(entityType));
        })
        .sendWith(service);
  }

  public static <E extends ServiceDocument> void patchCloudStoreEntityAndProcess(
      Service service,
      String entityLink,
      E patch,
      Class<E> entityType,
      Consumer<E> successConsumer,
      Consumer<Throwable> failureConsumer) {
    ServiceHostUtils.getCloudStoreHelper(service.getHost())
        .createPatch(entityLink)
        .setBody(patch)
        .setCompletion((op, ex) -> {
          if (ex != null) {
            failureConsumer.accept(ex);
            return;
          }

          successConsumer.accept(op.getBody(entityType));
        })
        .sendWith(service);
  }
}
