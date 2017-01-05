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
  class DiskCreateSpec

    attr_accessor :name, :kind, :flavor, :capacity_gb, :affinities, :tags

    # @param [String] name
    # @param [String] kind
    # @param [String] flavor
    # @param [int] capacity_gb
    # @param [Array<Locality>] affinities
    def initialize(name, kind, flavor, capacity_gb, affinities = nil, tags = nil)
      @name = name
      @kind = kind
      @flavor = flavor
      @capacity_gb = capacity_gb
      @affinities = affinities
      @tags = tags
    end

    def to_hash
      {
          :name => @name,
          :kind => @kind,
          :flavor => @flavor,
          :capacityGb => @capacity_gb,
          :affinities => @affinities ?
              @affinities.map { |a| a.to_hash } : [],
          :tags => @tags ? @tags : []
      }
    end

  end
end
