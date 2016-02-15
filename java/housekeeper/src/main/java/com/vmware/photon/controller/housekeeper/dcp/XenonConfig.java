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

package com.vmware.photon.controller.housekeeper.dcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.BindingAnnotation;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Xenon configuration.
 */
public class XenonConfig {
  private static final int IMAGE_BATCHCOPY_SIZE = 5;

  @NotNull
  @Range(min = 0, max = 100)
  @JsonProperty("image_copy_batch_size")
  private int imageCopyBatchSize;

  @NotNull
  @NotEmpty
  @JsonProperty("storage_path")
  private String storagePath;

  public XenonConfig() {
    imageCopyBatchSize = IMAGE_BATCHCOPY_SIZE;
  }

  public int getImageCopyBatchSize() {
    return imageCopyBatchSize;
  }

  public String getStoragePath() {
    return storagePath;
  }

  /**
   * DCP storage path.
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface StoragePath {
  }

}
