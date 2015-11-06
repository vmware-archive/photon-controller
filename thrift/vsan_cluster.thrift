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

namespace java com.vmware.photon.controller.scheduler.gen
namespace py gen.storage

include 'tracing.thrift'

struct VsanClusterConfig {
  // Vsan cluster UUID.
  1: required string cluster_uuid

  // List of hosts managed by this cluster
  2: required list<string> hosts

  // Multicast address for upstream (or master) group
  3: required string upstream_ip

  // Multicast address for downstream (or agent) group
  4: required string downstream_ip
}
