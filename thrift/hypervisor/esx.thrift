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

namespace py gen.hypervisor.esx

struct MigrationDiskSpec {
  1: optional i32 unit_number
  2: optional i32 bus_number
  3: optional string controller_type
  4: optional string filename
}

struct MigrationSpec {
  1: optional string source_ip
  2: optional string source_uuid
  3: optional string source_vm_path_name
  4: optional string destination_ip
  5: optional string destination_uuid
  6: optional string destination_vm_dir_path
  7: optional string destination_vm_file_name
  8: optional list<MigrationDiskSpec> disk_locations
}

struct EsxMigrationSpec {
  1: optional MigrationSpec spec
  2: optional string vmx_file
  3: optional i32 migration_id
  4: optional i32 destination_id
}
