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
include 'resource.thrift'

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
  3: required resource.ImageReplication replicationType
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

// Housekeeper service
service Housekeeper {
  ReplicateImageResponse replicate_image(1: ReplicateImageRequest request)
  ReplicateImageStatusResponse replicate_image_status(1: ReplicateImageStatusRequest request)

  status.Status get_status();
}
