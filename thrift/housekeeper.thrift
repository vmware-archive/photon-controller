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

namespace java com.vmware.photon.controller.housekeeper.gen
namespace py gen.housekeeper

include 'tracing.thrift'
include 'status.thrift'

/**
 * Image replication
 */

enum ReplicateImageResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
}

struct ReplicateImageResult {
  1: required ReplicateImageResultCode code
  2: optional string error
}

struct ReplicateImageRequest {
  1: required string datastore
  2: required string image
  99: optional tracing.TracingInfo tracing_info
}

struct ReplicateImageResponse {
  1: required ReplicateImageResult result
  2: optional string operation_id
}

enum ReplicateImageStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct ReplicateImageStatus {
  1: required ReplicateImageStatusCode code
  2: optional string error
}

struct ReplicateImageStatusRequest {
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct ReplicateImageStatusResponse {
  1: required ReplicateImageResult result
  2: optional ReplicateImageStatus status
}

/**
 * Image removal
 */

enum RemoveImageResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  SERVICE_NOT_FOUND = 2
}

struct RemoveImageResult {
  1: required RemoveImageResultCode code
  2: optional string error
}

// Remove image
struct RemoveImageRequest {
  // The image to remove (this is the name of the image)
  // location is assumed to be a well known location for the
  // system
  1: required string image
  99: optional tracing.TracingInfo tracing_info
}

struct RemoveImageResponse {
  1: required RemoveImageResult result
  // ID of the removal operation
  2: optional string operation_id
}

enum RemoveImageStatusCode {
  IN_PROGRESS = 0
  FINISHED = 1
  FAILED = 2
  CANCELLED = 3
}

struct RemoveImageStatus {
  1: required RemoveImageStatusCode code
  2: optional string error
}

struct RemoveImageStatusRequest {
  // ID of of the removal operation
  1: required string operation_id
  99: optional tracing.TracingInfo tracing_info
}

struct RemoveImageStatusResponse {
  1: required RemoveImageResult result
  2: optional RemoveImageStatus status
}

// Housekeeper service
service Housekeeper {
  ReplicateImageResponse replicate_image(1: ReplicateImageRequest request)
  ReplicateImageStatusResponse replicate_image_status(1: ReplicateImageStatusRequest request)

  RemoveImageResponse remove_image(1: RemoveImageRequest request)
  RemoveImageStatusResponse remove_image_status(1: RemoveImageStatusRequest request)

  status.Status get_status();
}
