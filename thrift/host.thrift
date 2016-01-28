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

namespace java com.vmware.photon.controller.host.gen
namespace py gen.host

include 'agent.thrift'
include 'resource.thrift'
include 'roles.thrift'
include 'scheduler.thrift'
include 'server_address.thrift'
include 'tracing.thrift'


struct SetResourceTagsRequest {
  1: optional map<string, set<string>> datastore_tags

  99: optional tracing.TracingInfo tracing_info
}

enum SetResourceTagsResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct SetResourceTagsResponse {
  1: required SetResourceTagsResultCode result
  2: optional string error
}

// The current status of the agent
enum AgentStatusCode {
   // The agent is up and running and can accept thrift calls.
   OK = 0

   // The agent is in the process of restarting
   RESTARTING = 1

   // There is no image datastore connected to host.
   IMAGE_DATASTORE_NOT_CONNECTED = 2
}

// Agent status response
struct AgentStatusResponse {
  1: required AgentStatusCode status
}

/**
 * Host configuration
 */
struct GetConfigRequest {
  1: optional string agent_id
}

enum GetConfigResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

// Current configuration of a host
struct HostConfig {
  1: required string agent_id
  2: required string availability_zone
  3: required list<resource.Datastore> datastores
  // host:port this agent listens to
  4: required server_address.ServerAddress address
  5: optional list<resource.Network> networks
  6: optional binary hypervisor
  7: optional roles.Roles roles
  // A uuid that corrosponds to only one datastore
  // in the datastores list
  8: optional string image_datastore_id

  // Whether the host is management only. Default as False.
  9: optional bool management_only

  // Image datastore IDs. Note that it's possible for the host to have multiple
  // image datastores.
  // TODO(mmutsuzaki) deprecate the image_datastore_id field.
  10: optional set<string> image_datastore_ids
  11: optional i32 memory_mb
  12: optional i32 cpu_count
  13: optional string esx_version
}

struct GetConfigResponse {
  1: required GetConfigResultCode result
  2: optional string error
  3: optional HostConfig hostConfig
}

enum HostMode {
  NORMAL = 0
  ENTERING_MAINTENANCE = 1
  MAINTENANCE = 2
  DEPROVISIONED = 3
}

struct GetHostModeRequest {
  99: optional tracing.TracingInfo tracing_info
}

enum GetHostModeResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct GetHostModeResponse {
  1: required GetHostModeResultCode result
  2: optional string error
  3: optional HostMode mode
}

struct SetHostModeRequest {
  1: required HostMode mode
  99: optional tracing.TracingInfo tracing_info
}

enum SetHostModeResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct SetHostModeResponse {
  1: required SetHostModeResultCode result
  2: optional string error
}

/**
 * Resource reservation
 */

// Reserve resources
struct ReserveRequest {
  1: required resource.Resource resource
  2: required i32 generation
  99: optional tracing.TracingInfo tracing_info
}

enum ReserveResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  STALE_GENERATION = 2
  OPERATION_NOT_ALLOWED = 3
}

struct ReserveResponse {
  1: required ReserveResultCode result
  2: optional string error

  // Reservation used for subsequent requests
  3: optional string reservation

  // List of disk ids that need to be migrated before a VM can be created
  4: optional list<string> disk_migrations
}

/**
 * VM lifecycle
 */

struct Ipv4Address {
  1: required string ip_address
  2: required string netmask
}

struct NicConnectionSpec {
  1: required string network_name
  2: optional Ipv4Address ip_address
}

struct NetworkConnectionSpec {
  1: required list<NicConnectionSpec> nic_spec
  2: optional string default_gateway
}

// Create VM
struct CreateVmRequest {
  // Reservation returned by ReserveResponse#reservation
  1: required string reservation

  // VM environment
  2: optional map<string,string> environment

  // VM Nic and IP adddress specification
  3: optional NetworkConnectionSpec network_connection_spec

  99: optional tracing.TracingInfo tracing_info
}

enum CreateVmResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  INVALID_RESERVATION = 2
  DISK_NOT_FOUND = 3
  NETWORK_NOT_FOUND = 4
  IMAGE_NOT_FOUND = 5
  PLACEMENT_NOT_FOUND = 6
  DATASTORE_UNAVAILABLE = 7
  // VM got created, but failed to increment refcount for the base image. Note
  // that in this case the agent tries to delete the VM that just got created,
  // but it does not guarantee the VM to be deleted.
  REF_UPDATE_FAILED = 8
  // The image exists, but it's tombstoned.
  IMAGE_TOMBSTONED = 9
  VM_ALREADY_EXIST = 10
  OPERATION_NOT_ALLOWED = 11
}

struct CreateVmResponse {
  1: required CreateVmResultCode result
  2: optional string error
  3: optional resource.Vm vm
}

struct RegisterVmRequest {
  1: required string vm_id
  2: required string datastore_id

  // Reservation returned by ReserveResponse#reservation
  3: optional string reservation
}

enum RegisterVmResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
  INVALID_RESERVATION = 3
}

struct RegisterVmResponse {
  1: required RegisterVmResultCode result
  2: optional string error
}

struct UnregisterVmRequest {
  1: required string vm_id
}

enum UnregisterVmResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
}

struct UnregisterVmResponse {
  1: required UnregisterVmResultCode result
  2: optional string error
}

// Delete VM
struct DeleteVmRequest {
  1: required string vm_id
  2: optional list<string> disk_ids  # TODO(agui): Not used anymore. Remove this.
  3: optional bool force
  99: optional tracing.TracingInfo tracing_info
}

enum DeleteVmResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
  VM_NOT_POWERED_OFF = 3
  OPERATION_NOT_ALLOWED = 4
  // Reference count for the base image got decremented, but VM deletion
  // failed.
  PARTIAL_DELETE = 5
  // Failed to decrement refcount. In this case, the agent will not attempt to
  // delete the VM.
  REF_UPDATE_FAILED = 6
  CONCURRENT_VM_OPERATION = 90
}

struct DeleteVmResponse {
  1: required DeleteVmResultCode result
  2: optional string error
}

enum ConnectedStatus {
  CONNECTED = 0
  DISCONNECTED = 1
  UNKNOWN = 2
}

struct VmNetworkInfo {
  1: optional string mac_address
  2: optional Ipv4Address ip_address
  3: optional ConnectedStatus is_connected
  // There could be nic cards that are not connected to networks.
  4: optional string network
}

// Get VM
struct GetVmNetworkRequest {
  1: required string vm_id
  99: optional tracing.TracingInfo tracing_info
}

// Should we consolidate result codes across all VM operations?
// Also system error doesn't really mean anything.
enum GetVmNetworkResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
}

struct GetVmNetworkResponse {
  1: optional list<VmNetworkInfo> network_info
  2: required GetVmNetworkResultCode result
  3: optional string error
}

// Attach an ISO to a VM
struct AttachISORequest {
  // The ID of the VM being updated.
  1: required string vm_id

  // Specifies the datastore path to the ISO (CD image) file that backs the
  // virtual CD drive
  2: required string iso_file_path

  99: optional tracing.TracingInfo tracing_info
}

enum AttachISOResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
  ISO_ATTACHED_ERROR = 3
  CONCURRENT_VM_OPERATION = 90
}

struct AttachISOResponse {
  1: required AttachISOResultCode result
  2: optional string error
}

// Detach an attached ISO from a VM, and possibly delete it.
struct DetachISORequest {
  // The ID of the VM being updated.
  1: required string vm_id

  // If true, will attempt to delete the iso after detaching it from the VM
  2: optional bool delete_file

  99: optional tracing.TracingInfo tracing_info
}

enum DetachISOResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
  ISO_NOT_ATTACHED = 3
  CANNOT_DELETE = 4
  CONCURRENT_VM_OPERATION = 90
}

struct DetachISOResponse {
  1: required DetachISOResultCode result
  2: optional string error
}

// Power operation on VM
enum PowerVmOp {
  ON = 1
  OFF = 2
  RESET = 3
  SUSPEND = 4
  RESUME = 5
}

struct PowerVmOpRequest {
  1: required string vm_id
  2: required PowerVmOp op
  99: optional tracing.TracingInfo tracing_info
}

enum PowerVmOpResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
  INVALID_VM_POWER_STATE = 3
  OPERATION_NOT_ALLOWED = 4
  CONCURRENT_VM_OPERATION = 90
}

struct PowerVmOpResponse {
  1: required PowerVmOpResultCode result
  2: optional string error
}

// Disk Attach/Detach
enum VmDiskOpResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
  DISK_NOT_FOUND = 3
  DISK_ATTACHED = 4
  DISK_DETACHED = 5
  INVALID_VM_POWER_STATE = 6
  CONCURRENT_VM_OPERATION = 90
}

struct VmDiskOpError {
  1: required VmDiskOpResultCode result
  2: optional string error
}

struct VmDisksOpResponse {
  1: required VmDiskOpResultCode result
  2: optional string error
  3: optional list<resource.Disk> disks
  4: optional map<string, VmDiskOpError> disk_errors
}

// Disk attach
struct VmDisksAttachRequest {
  1: required string vm_id
  2: required list<string> disk_ids
  99: optional tracing.TracingInfo tracing_info
}

// Disk detach
struct VmDisksDetachRequest {
  1: required string vm_id
  2: required list<string> disk_ids
  99: optional tracing.TracingInfo tracing_info
}

// Get Resources
struct GetResourcesRequest {
  1: optional list<resource.Locator> locators
  99: optional tracing.TracingInfo tracing_info
}

enum GetResourcesResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct GetResourcesResponse {
  1: required GetResourcesResultCode result
  2: optional string error
  3: optional list<resource.Resource> resources
}

/**
 * Disk lifecycle
 */

// Create Disks
struct CreateDisksRequest {
  1: required string reservation
  99: optional tracing.TracingInfo tracing_info
}

enum CreateDisksResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  INVALID_RESERVATION = 2
}

enum CreateDiskResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  PLACEMENT_NOT_FOUND=2
  DATASTORE_UNAVAILABLE=3
}

struct CreateDiskError {
  1: required CreateDiskResultCode result
  2: optional string error
}

struct CreateDisksResponse {
  1: required CreateDisksResultCode result
  2: optional string error
  3: optional list<resource.Disk> disks
  4: optional map<string, CreateDiskError> disk_errors
}

// Delete Disks
struct DeleteDisksRequest {
  1: required list<string> disk_ids
  99: optional tracing.TracingInfo tracing_info
}

enum DeleteDisksResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

enum DeleteDiskResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  DISK_NOT_FOUND = 2
  DISK_ATTACHED = 3
}

struct DeleteDiskError {
  1: required DeleteDiskResultCode result
  2: optional string error
}

struct DeleteDisksResponse {
  1: required DeleteDisksResultCode result
  2: optional string error
  3: optional map<string, DeleteDiskError> disk_errors
}

// Transfer Image
struct TransferImageRequest {
  // The id of the source image.
  1: required string source_image_id

  2: required server_address.ServerAddress destination_host

  // The datastore id.
  3: required string destination_datastore_id

  // The id of the datastore that image resides in.
  4: optional string source_datastore_id

  // If not specified, source_image_id will be the id of the image
  // used at the destination
  5: optional string destination_image_id

  99: optional tracing.TracingInfo tracing_info
}
enum TransferImageResultCode {
  // The image was created successfully.
  OK = 0
  // Catch all error.
  SYSTEM_ERROR = 1
  // Transfer is rejected because another is in progress.
  TRANSFER_IN_PROGRESS = 2
}
struct TransferImageResponse {
  1: required TransferImageResultCode result

  2: optional string error
}

// Receive Image
struct ReceiveImageRequest {
  // The ID of the Image.
  1: required string image_id

  // The datastore name or id.
  2: required string datastore_id

  // The id to lookup transferred image data at the receiving host
  3: required string transferred_image_id

  // Raw image metadata
  4: required string metadata
  5: required string manifest

  99: optional tracing.TracingInfo tracing_info
}
enum ReceiveImageResultCode {
  // The image was created successfully.
  OK = 0
  // Catch all error.
  SYSTEM_ERROR = 1
  // The src image directory was not found.
  IMAGE_NOT_FOUND = 2
  // The destination image already exists.
  DESTINATION_ALREADY_EXIST = 3
  // The datastore was not found.
  DATASTORE_NOT_FOUND = 4
}
struct ReceiveImageResponse {
  1: required ReceiveImageResultCode result
  2: optional string error
}

// Create Image
struct CreateImageRequest {
  // The ID of the Image.
  1: required string image_id

  // The datastore name or id.
  2: required string datastore

  // The path of temporary image directory relative to the datastore mount point.
  3: required string tmp_image_path

  99: optional tracing.TracingInfo tracing_info
}

enum CreateImageResultCode {
  /* The image was created successfully. */
  OK = 0
  /* Catch all error. */
  SYSTEM_ERROR = 1
  /* The src image directory was not found. */
  IMAGE_NOT_FOUND = 2
  /* The destination image already exists. */
  DESTINATION_ALREADY_EXIST = 3
  /* The datastore was not found. */
  DATASTORE_NOT_FOUND = 4
}

struct CreateImageResponse {
  1: required CreateImageResultCode result
  2: optional string error
}

// Create Image From Virtual Machine
struct CreateImageFromVmRequest {
  // The ID of the VM to create the image from
  1: required string vm_id

  // The ID of the new image being created.
  2: required string image_id

  // The datastore name or id to the image datastore.
  3: required string datastore

  // The path of temporary image directory relative to the datastore mount point.
  // This is the location where the image files to be moved to the image
  // datastore will be staged.
  4: required string tmp_image_path

  // The ID of the disk in the vm to used to create the image from.
  // If not specified, the VM's disk that was copied/COW-ed from
  // an image disk will be used.
  5: optional string disk_id

  99: optional tracing.TracingInfo tracing_info
}

enum CreateImageFromVmResultCode {
  /* The image was created successfully. */
  OK = 0
  /* Catch all error. */
  SYSTEM_ERROR = 1
  /* The destination image already exists. */
  IMAGE_ALREADY_EXIST = 2
  /* The src VM was not found. */
  VM_NOT_FOUND = 3
  /* The src VM is not in the correct (off) state to perform this operation. */
  INVALID_VM_POWER_STATE = 4
}

struct CreateImageFromVmResponse {
  1: required CreateImageFromVmResultCode result
  2: optional string error
}

// Copy Image
struct CopyImageRequest {
  // Source image
  1: required resource.Image source

  // Destination image
  2: required resource.Image destination

  99: optional tracing.TracingInfo tracing_info
}

enum CopyImageResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  IMAGE_NOT_FOUND = 2
  DESTINATION_ALREADY_EXIST = 3
}

struct CopyImageResponse {
  1: required CopyImageResultCode result
  2: optional string error
}

struct DeleteImageRequest {
  // Image to be deleted
  1: required resource.Image image
  // If True the image is marked as tombstoned. Defaults to True.
  // A tombstoned image cannot be used for creating new VMs.
  2: optional bool tombstone
  // Force the delete of the image regardless of ref. count. Defaults to False.
  // If there are powered on VMs referencing the image, esx(vmfs/nfs) will
  // prevent the deletion of the image.
  3: optional bool force

  99: optional tracing.TracingInfo tracing_info
}

enum DeleteImageResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  // The delete failed due to the image not being found on the datastore.
  IMAGE_NOT_FOUND = 2
  // The delete failed as the image is being referenced by virtual machines.
  IMAGE_IN_USE = 3
  // The delete failed as the ref count associated with the image is invalid.
  // This can happen due to many reasons, common examples include
  // 1 - no permissions to read the file.
  // 2 - File is not deserializable.
  // 3 - Ref count file version is not understood by this host.
  // 4 - Filesystem and OS issues.
  INVALID_REF_COUNT_FILE = 4
}

struct DeleteImageResponse {
  1: required DeleteImageResultCode result
  2: optional string error
}

struct DeleteDirectoryRequest {
  // The datastore name or id for the directory.
  1: required string datastore
  // The file path relative to the mount point of the datastore.
  2: required string directory_path

  99: optional tracing.TracingInfo tracing_info
}

enum DeleteDirectoryResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  DIRECTORY_NOT_FOUND = 2
  DATASTORE_NOT_FOUND = 3
}

struct DeleteDirectoryResponse {
  1: required DeleteDirectoryResultCode result
  2: optional string error
}

struct GetImagesRequest {
  // The datastore in which to get image list
  1: required string datastore_id

  99: optional tracing.TracingInfo tracing_info
}

enum GetImagesResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  DATASTORE_NOT_FOUND = 2
}

struct GetImagesResponse {
  1: required GetImagesResultCode result
  2: optional string error
  3: optional list<string> image_ids
}

struct ImageInfoRequest {
  1: required string image_id

  // If datastore name is specified, server will normalize it.
  2: required string datastore_id

  99: optional tracing.TracingInfo tracing_info
}

enum ImageInfoResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  IMAGE_NOT_FOUND = 2
  DATASTORE_NOT_FOUND = 3
  INVALID_REF_COUNT_FILE = 4
}

struct ImageInfoResponse {
  1: required ImageInfoResultCode result
  2: optional string error
  3: optional resource.ImageInfo image_info
}

// APIs to trigger scan/sweep of unused images

enum StartImageOperationResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  DATASTORE_NOT_FOUND = 2
  SCAN_IN_PROGRESS = 3
  SWEEP_IN_PROGRESS = 4
}

struct StartImageScanRequest {
  // The datastore in which to get the inactive image list
  1: required string datastore_id
  // Max number of vms/images that should be scanned per minute
  2: optional i64 scan_rate
  // Global time out, the scan operation is terminated if the timeout is exceeded, in seconds
  3: optional i64 timeout

  99: optional tracing.TracingInfo tracing_info
}

struct StartImageScanResponse {
  // Result code
  1: required StartImageOperationResultCode result
  2: optional string error
}

struct StartImageSweepRequest {
  // The datastore in which to get the inactive image list
  1: required string datastore_id
  // List of images to be deleted
  2: required list<resource.InactiveImageDescriptor> image_descs
  // Max number of images that should be sweeped per minute
  3: optional i64 sweep_rate
  // Global time out, the scan operation is terminated if the timeout is exceeded, in seconds
  4: optional i64 timeout
  // Grace period in seconds, used to adjust time comparison to compensate for clock drift among hosts
  5: optional i64 grace_period

  99: optional tracing.TracingInfo tracing_info
}

struct StartImageSweepResponse {
  // Result code
  1: required StartImageOperationResultCode result
  2: optional string error
}

enum StopImageOperationResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  DATASTORE_NOT_FOUND = 2
}

struct StopImageOperationResponse {
  // Result code
  1: required StopImageOperationResultCode result
  2: optional string error
}

struct StopImageOperationRequest {
  // The datastore in which to get the inactive image list
  1: required string datastore_id

  99: optional tracing.TracingInfo tracing_info
}

// APIs to collect the list of unused images
// and deleted images

enum GetMonitoredImagesResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  DATASTORE_NOT_FOUND = 2
  OPERATION_IN_PROGRESS = 3
}

struct GetInactiveImagesRequest {
  // The datastore in which to get the inactive image list
  1: required string datastore_id

  99: optional tracing.TracingInfo tracing_info
}

struct GetInactiveImagesResponse {
  1: required GetMonitoredImagesResultCode result
  2: optional string error
  3: optional i64 totalMB
  4: optional i64 usedMB
  5: optional list<resource.InactiveImageDescriptor> image_descs
}

struct GetDeletedImagesRequest {
  // The datastore in which to get the list of delete images since the last activation
  1: required string datastore_id
  2: optional string error

  99: optional tracing.TracingInfo tracing_info
}

struct GetDeletedImagesResponse {
  1: required GetMonitoredImagesResultCode result
  2: optional string error
  3: optional list<resource.InactiveImageDescriptor> image_descs
}

# Enumeration of service tickets supported by the agent.
enum ServiceType {
  # Ticket to authenticate against the NFC service on the host
  NFC = 0
  # Ticket to authenticate against the Http service on the host
  HTTP = 1
  # Ticket to authenticate against the VIM endpoint (hostd)
  VIM = 2
}

# Request structure for service tickets.
struct ServiceTicketRequest {
  1: required ServiceType service_type

  // Actually this field takes both datastore_name and datastore_id
  2: optional string datastore_name

  99: optional tracing.TracingInfo tracing_info
}

enum ServiceTicketResultCode {
  OK = 0
  BAD_REQUEST = 1
  NOT_FOUND = 2
  SYSTEM_ERROR = 99
}

struct ServiceTicketResponse {
  1: required ServiceTicketResultCode result
  2: optional string error

  // NFC ticket
  3: optional resource.HostServiceTicket ticket

  // VIM ticket
  4: optional string vim_ticket
}

struct MksTicketRequest {
  1: required string vm_id

  99: optional tracing.TracingInfo tracing_info
}

enum MksTicketResultCode {
  OK = 0
  SYSTEM_ERROR = 1
  VM_NOT_FOUND = 2
  INVALID_VM_POWER_STATE = 3
}

struct MksTicketResponse {
  1: required MksTicketResultCode result
  2: optional string error

  // Mks ticket
  3: optional resource.MksTicket ticket
}

enum HttpOp {
  GET = 0
  PUT = 1
  POST = 2
}

struct HttpTicketRequest {
  // URL to host resource to access via HTTP CGI
  1: required string url

  // The operation to perform on the URL
  2: required HttpOp op

  99: optional tracing.TracingInfo tracing_info
}

enum HttpTicketResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct HttpTicketResponse {
  1: required HttpTicketResultCode result
  2: optional string error
  3: optional string ticket
}

struct GetDatastoresRequest {
  99: optional tracing.TracingInfo tracing_info
}

enum GetDatastoresResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct GetDatastoresResponse {
  1: required GetDatastoresResultCode result
  2: required list<resource.Datastore> datastores
}

struct GetNetworksRequest {
  99: optional tracing.TracingInfo tracing_info
}

enum GetNetworksResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct GetNetworksResponse {
  1: required GetNetworksResultCode result
  2: optional list<resource.Network> networks
}

// Host service
service Host {
  // Get the status of the agent.
  AgentStatusResponse get_agent_status()
  scheduler.ConfigureResponse configure(1: scheduler.ConfigureRequest request)
  GetConfigResponse get_host_config(1: GetConfigRequest request)

  GetDatastoresResponse get_datastores(1: GetDatastoresRequest request)
  GetNetworksResponse get_networks(1: GetNetworksRequest request)

  // Set datastore tags
  SetResourceTagsResponse set_resource_tags(1: SetResourceTagsRequest request)

  // Get/set host mode
  GetHostModeResponse get_host_mode(1: GetHostModeRequest request)
  SetHostModeResponse set_host_mode(1: SetHostModeRequest request)

  ReserveResponse reserve(1: ReserveRequest request)

  CreateDisksResponse create_disks(1: CreateDisksRequest request)
  DeleteDisksResponse delete_disks(1: DeleteDisksRequest request)

  /**
   * Image
   */
  CreateImageResponse create_image(1: CreateImageRequest request)
  CopyImageResponse copy_image(1: CopyImageRequest request)
  DeleteImageResponse delete_image(1: DeleteImageRequest request)
  GetImagesResponse get_images(1: GetImagesRequest request)
  ImageInfoResponse get_image_info(1: ImageInfoRequest request)

  TransferImageResponse transfer_image(1: TransferImageRequest request)
  ReceiveImageResponse receive_image(1: ReceiveImageRequest request)

  /**
   * Image scan/sweep
   */
  StartImageScanResponse start_image_scan(1: StartImageScanRequest request)
  StartImageSweepResponse start_image_sweep(1: StartImageSweepRequest request)
  GetInactiveImagesResponse get_inactive_images(1: GetInactiveImagesRequest request)
  GetDeletedImagesResponse get_deleted_images(1: GetDeletedImagesRequest request)

  VmDisksOpResponse attach_disks(1: VmDisksAttachRequest request)
  VmDisksOpResponse detach_disks(1: VmDisksDetachRequest request)

  CreateVmResponse create_vm(1: CreateVmRequest request)
  DeleteVmResponse delete_vm(1: DeleteVmRequest request)
  AttachISOResponse attach_iso(1: AttachISORequest request)
  DetachISOResponse detach_iso(1: DetachISORequest request)
  GetResourcesResponse get_resources(1: GetResourcesRequest request)
  PowerVmOpResponse power_vm_op(1: PowerVmOpRequest request)
  GetVmNetworkResponse get_vm_networks(1: GetVmNetworkRequest request)
  CreateImageFromVmResponse create_image_from_vm(1: CreateImageFromVmRequest request)

  RegisterVmResponse register_vm(1: RegisterVmRequest request)
  UnregisterVmResponse unregister_vm(1: UnregisterVmRequest request)

  ServiceTicketResponse get_service_ticket(1: ServiceTicketRequest request)
  MksTicketResponse get_mks_ticket(1: MksTicketRequest request)
  HttpTicketResponse get_http_ticket(1: HttpTicketRequest request)

  scheduler.PlaceResponse place(1: scheduler.PlaceRequest request)
  scheduler.FindResponse find(1: scheduler.FindRequest request)

  /* API to delete a directory.
   * NFC delete directory implementation doesn't work correctly so the API FE
   * cannot clean up on partial uploads. This API is provided to workaround that
   * issue in the NFC implementation but can be used as a general purpose API to
   * delete tmp images.
   */
  DeleteDirectoryResponse delete_directory(1: DeleteDirectoryRequest request)

  // Method to provision an agent for esxcloud purposes.
  agent.ProvisionResponse provision(1: agent.ProvisionRequest request)

  // Method to set host's availability zone.
  agent.SetAvailabilityZoneResponse set_availability_zone(1: agent.SetAvailabilityZoneRequest request)
}
