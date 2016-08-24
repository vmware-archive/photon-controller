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
  class Cluster
    attr_reader :id, :name, :type, :state, :worker_count, :extended_properties

    # @param [String] id
    # @param [String] name
    # @param [String] type
    # @param [String] state
    # @param [int] worker_count
    # @param [Hash] extended_properties
    def initialize(id, name, type, state, worker_count, extended_properties)
      @id = id
      @name = name
      @type = type
      @state = state
      @worker_count = worker_count
      @extended_properties = extended_properties
    end

    def ==(other)
      @id == other.id && @name == other.name && @type == other.type &&
      @state == state && @worker_count == other.worker_count &&
      @extended_properties == other.extended_properties
    end

    # @param [Hash] hash
    # @return [Cluster]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name type).to_set)
        raise UnexpectedFormat, "Invalid Cluster hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["type"], hash["state"],
          hash["workerCount"], hash["extendedProperties"])
    end

    # @param [String] project_id
    # @param [CreateClusterSpec] create_cluster_spec
    # @return [Cluster]
    def self.create(project_id, create_cluster_spec)
      Config.client.create_cluster(project_id, create_cluster_spec.to_hash)
    end

    # @param [String] json
    # @return [Cluster]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

  end
end
