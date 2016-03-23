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

 namespace java com.vmware.photon.controller.nsx.gen
 namespace py gen.nsx

include 'tracing.thrift'
include 'status.thrift'
include 'resource.thrift'

//////////////////////////////////////////////////////////////////////////////
// RegisterFabricNode
//////////////////////////////////////////////////////////////////////////////
struct HostNodeLoginCredential {
  1: required string username
  2: required string password
  3: required string thumbprint
}

struct RegisterFabricNodeRequest {
  1: required string resource_type
  2: optional string display_name
  3: required list<string> ip_addresses
  4: required string os_type
  5: optional HostNodeLoginCredential host_credential
}

struct RegisterFabricNodeResponse {
  1: required string id
  2: required string external_id
}

//////////////////////////////////////////////////////////////////////////////
// GetFabricNode
//////////////////////////////////////////////////////////////////////////////
struct GetFabricNodeResponse {
  1: required string id
  2: required string external_id
  3: required string resource_type
  4: required list<string> ip_addresses
  5: optional string managed_by_server
  6: optional string display_name
  7: optional string os_version
  8: required string os_type
}
