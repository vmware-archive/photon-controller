# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

module EsxCloud
  class ClusterCreateSpec

    attr_accessor :name, :type, :vm_flavor, :disk_flavor, :worker_count, :batch_size,
                  :extended_properties

    # @param [String] name
    # @param [String] type
    # @param [String] vm_flavor
    # @param [String] disk_flavor
    # @param [String] network_id
    # @param [int] worker_count
    # @param [int] batch_size
    # @param [Hash] extended_properties
    def initialize(name, type, vm_flavor, disk_flavor, network_id, worker_count, batch_size,
                   extended_properties)
      @name = name
      @type = type
      @vm_flavor = vm_flavor
      @disk_flavor = disk_flavor
      @network_id = network_id
      @worker_count = worker_count
      @batch_size = batch_size
      @extended_properties = extended_properties
    end

    def to_hash
      {
        name: @name,
        type: @type,
        vmFlavor: @vm_flavor,
        diskFlavor: @disk_flavor,
        vmNetworkId: @network_id,
        workerCount: @worker_count,
        workerBatchExpansionSize: @batch_size,
        extendedProperties: @extended_properties
      }
    end
  end
end
