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
// Common structures and enums
//////////////////////////////////////////////////////////////////////////////
struct HostNodeLoginCredential {
  1: required string username
  2: required string password
  3: required string thumbprint
}

struct PhysicalNic {
  1: required string uplink_name
  2: required string device_name
}

struct HostSwitch {
  1: required string host_switch_name
  2: optional string static_ip_pool_id
  3: optional list<PhysicalNic> pnics
}

struct TransportZoneEndPoint {
  1: required string transport_zone_id
}

enum TransportType {
  OVERLAY,
  VLAN
}

enum RouterType {
  TIER0 = 1
  TIER1 = 2
}

struct IPv4CIDRBlock {
  1: required string IPv4CIDRBlock
}

struct LogicalRouterConfig {
  1: optional list<IPv4CIDRBlock> external_transit_networks
  2: optional IPv4CIDRBlock internal_transit_network
}

//////////////////////////////////////////////////////////////////////////////
// Fabric node
//////////////////////////////////////////////////////////////////////////////
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

//////////////////////////////////////////////////////////////////////////////
// Transport node
//////////////////////////////////////////////////////////////////////////////
struct CreateTransportNodeRequest {
  1: optional string description
  2: optional string display_name
  3: required string node_id
  4: required list<HostSwitch> host_switches
  5: optional list<TransportZoneEndPoint> transport_zone_endpoints
}

struct CreateTransportNodeResponse {
  1: required string id
}

struct GetTransportNodeResponse {
  1: required string id
  2: required string resource_type
  3: required string node_id
  4: required list<HostSwitch> host_switches
  5: optional list<TransportZoneEndPoint> transport_zone_endpoints
}

//////////////////////////////////////////////////////////////////////////////
// Transport zone
//////////////////////////////////////////////////////////////////////////////
struct CreateTransportZoneRequest {
  1: optional string display_name
  2: required string host_switch_name
  3: optional string description
  4: required TransportType transport_type
}

struct CreateTransportZoneResponse {
  1: required string id
}

struct GetTransportZoneResponse {
  1: required string id
  2: required string resource_type
  3: required TransportType transport_type
  4: required string host_switch_name
}

//////////////////////////////////////////////////////////////////////////////
// Logical Router
//////////////////////////////////////////////////////////////////////////////
struct CreateLogicalRouterRequest {
  1: required RouterType router_type
  2: optional string display_name
  3: optional string description
  4: optional LogicalRouterConfig config
}

struct CreateLogicalRouterResponse {
  1: required string id
  2: required RouterType router_type
}

struct GetLogicalRouterResponse {
  1: required string id
  2: optional string resource_type
  3: required RouterType router_type
  4: optional string display_name
  5: optional string description
  6: optional LogicalRouterConfig config
}
