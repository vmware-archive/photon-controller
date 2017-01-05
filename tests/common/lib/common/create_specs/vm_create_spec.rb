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
  class VmCreateSpec

    attr_accessor :name, :flavor, :image_id, :disks, :affinities, :networks

    # @param [String] name
    # @param [String] flavor
    # @param [String] image_id
    # @param [Array<VmDisk>] disks
    # @param [Hash] environment
    # @param [Array<Locality>] affinities
    # @param [Array<String>] networks
    def initialize(name, flavor, image_id, disks, environment = {}, affinities = nil, networks = nil)
      @name = name
      @flavor = flavor
      @image_id = image_id
      @disks = disks
      @environment = environment
      @affinities = affinities
      @networks = networks
    end

    def to_hash
      {
        name: @name,
        flavor: @flavor,
        sourceImageId: @image_id,
        attachedDisks: @disks.map { |disk| disk.to_hash },
        environment: @environment,
        affinities: @affinities ?
          @affinities.map { |a| a.to_hash } : [],
        subnets: @networks || []
      }
    end

  end
end
