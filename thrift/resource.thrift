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

namespace java com.vmware.photon.controller.resource.gen
namespace py gen.resource

include 'flavors.thrift'

// Builtin datastore tags definitions
const string SHARED_VMFS_TAG = "SHARED_VMFS"
const string LOCAL_VMFS_TAG = "LOCAL_VMFS"
const string NFS_TAG = "NFS"
const string VSAN_TAG = "VSAN"

/**
 * Resource specs.
 *
 * Used for placement and reservation.
 */

// Power state numeric values should match those used by Vim
enum VmPowerState {
  STOPPED = 0
  STARTED = 1
  SUSPENDED = 2
}

// Resource constraint could only be datastore, host, or network.
enum ResourceConstraintType {
  DATASTORE = 0
  HOST = 1
  NETWORK = 2
  AVAILABILITY_ZONE = 3
  DATASTORE_TAG = 4
  MANAGEMENT_ONLY = 5
  VIRTUAL_NETWORK = 6
}

enum ResourcePlacementType {
  VM = 1
  DISK = 2
  NETWORK = 3
  VIRTUAL_NETWORK = 4
}

// Disk Image Operation
enum CloneType {
  FULL_COPY = 1
  COPY_ON_WRITE = 2
}

// Disk Image
struct DiskImage {
  1: required string id
  2: required CloneType clone_type
}

// NetworkType
enum NetworkType {
  VM = 0
  MANAGEMENT = 1
  FT_LOGGING = 2
  VSPHERE_REPLICATION = 3
  VSPHERE_REPLICATION_NFC = 4
  VMOTION = 5
  VSAN = 6
  OTHER = 99
}

// Network
struct Network {
  1: required string id
  2: optional list<NetworkType> types
}

// Datastore Type
enum DatastoreType {
  LOCAL_VMFS = 0
  SHARED_VMFS = 1
  NFS_3 = 2
  NFS_41 = 3
  VSAN = 4
  // Used for testing
  EXT3 = 5
  OTHER = 99
}

// Datastore
struct Datastore {
  1: required string id
  2: optional string name
  3: optional DatastoreType type
  4: optional set<string> tags
}

// Image datastore
struct ImageDatastore {
  1: required string name

  // Whether the datastore is available for placing vms (and disks)
  2: required bool used_for_vms
}

// FaultDomain
struct FaultDomain {
  1: required string id
}

// ResourceConstraint
struct ResourceConstraint {
  1: required ResourceConstraintType type
  // The id needs to be the unique identifier of resource constraint of specific resource type.
  2: required list<string> values
  // Negative constraint, do not place where resource is available
  3: optional bool negative
}

// The ResourcePlacement are set by the scheduler
// and consumed at the time of resource creation
// by the host agent
// When the type is disk:
// resource_id maps to the disk id
// container_id maps to the id of the datastore where the disk is placed
// When the type is vm and disk placment is optimal
// resource id maps to the vm id
// container_id maps to the id of the datastore where the vmdk is located
// When the type is vm and disk placement is best effort
// resource id maps to the vm id
// container id maps to the id of the datastore containing the largest disk
struct ResourcePlacement {
  1: required ResourcePlacementType type
  2: required string resource_id
  3: required string container_id
}

struct ResourcePlacementList {
  1: required list<ResourcePlacement> placements
}

/**
 * Image
 */

enum ImageType {
  MANAGEMENT = 0
  CLOUD = 1
}

// There are two kinds of image replication
// EAGER will copy images to all image and local datastore
// ON_DEMAND will copy images to all image datastores, and the copies
// the local datastores will happen when VMs are created (that is, on demand)
enum ImageReplication {
  EAGER = 0
  ON_DEMAND = 1
}

// Image
struct Image {
  1: required string id
  2: required Datastore datastore
}

struct InactiveImageDescriptor {
  // Image Id
  1: required string image_id
  // Timestamp of the last vm creation operation using this image
  2: required i64 timestamp
}

// Disk
struct Disk {
  1: required string id
  2: required string flavor
  3: required bool persistent
  4: required bool new_disk
  5: required i32 capacity_gb
  6: optional DiskImage image
  7: optional Datastore datastore
  8: optional flavors.Flavor flavor_info
  9: optional list<ResourceConstraint> resource_constraints
}

// VM
struct Vm {
  1: required string id
  2: required string flavor
  3: required VmPowerState state
  4: optional Datastore datastore
  5: optional map<string, string> environment
  6: optional list<Disk> disks
  7: optional flavors.Flavor flavor_info
  8: optional list<ResourceConstraint> resource_constraints
  9: optional string tenant_id
  10: optional string project_id
  11: optional string location_id
}

// Resource
struct Resource {
  1: optional Vm vm
  2: optional list<Disk> disks
  3: optional ResourcePlacementList placement_list
}

/**
 * Resource locators.
 */

// Find VM
struct VmLocator {
  1: required string id
}

// Find Disk
struct DiskLocator {
  1: required string id
}

// Find Spec
// Only allowed to specify one resource
struct Locator {
  1: optional VmLocator vm
  2: optional DiskLocator disk
}

/**
 * Host service
 */

struct HostServiceTicket {
  1: optional string host
  2: optional i32 port
  3: required string service_type
  4: required string service_version
  5: required string session_id
  6: optional string ssl_thumbprint
}

// Mks Ticket
struct MksTicket {
  1: optional string host
  2: optional i32 port
  3: required string cfg_file
  4: required string ticket
  5: optional string ssl_thumbprint
}

/**
 * Host
 */
struct Host {
  1: required string id
  2: optional string address
  3: optional string username
  4: optional string password
  5: optional map<string, string> metadata
  6: optional set<string> usageTags
  7: optional string availabilityZone
}
