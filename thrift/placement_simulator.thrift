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

namespace java com.vmware.photon.controller.placement_simulator.gen
namespace py gen.placement_simulator

include 'scheduler.thrift'
include 'server_address.thrift'

struct InitializeRequest {
  1: required server_address.ServerAddress cloudstore_endpoint
}

enum InitializeResultCode {
  OK = 0
  SYSTEM_ERROR = 1
}

struct InitializeResponse {
  1: required InitializeResultCode result
  2: optional string error
}

// Placement simulator service
service PlacementSimulator {

  /*
   * Initializes the placement simulator.
   *
   * This method initializes the placement simulator from host/datastore/network
   * documents in cloudstore. This method needs to be called before calling any other
   * method in this service.
   */
  InitializeResponse initialize(1: InitializeRequest request)

  /*
   * Sends a place request to a given host.
   */
  scheduler.PlaceResponse place(1: string host_id, 2: scheduler.PlaceRequest request)

  /*
   * Reserves resources on a given host.
   */
  ReserveResponse reserve(1: string host_id, ReserveRequest request)

  /*
   * Creates a VM on a given host.
   */
  CreateVmResponse create_vm(1: string host_id, 2: CreateVmRequest request)

  /*
   * Deletes a VM on a given host.
   */
  DeleteVmResponse delete_vm(1: string host_id, 2: DeleteVmRequest request)

  /*
   * Creates a disk on a given host.
   */
  CreateDisksResponse create_disks(1: string host_id, 2: CreateDisksRequest request)

  /*
   * Deletes a disk on a given host.
   */
  DeleteDisksResponse delete_disks(1: string host_id, 2: DeleteDisksRequest request)
}
