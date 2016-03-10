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
  class Host
    attr_accessor :id, :state, :username, :password, :availability_zone, :usage_tags, :address, :metadata, :esx_version

    # @param [HostCreateSpec] spec
    # @return [Host]
    def self.create(deployment_id, spec)
      Config.client.create_host(deployment_id, spec.to_hash)
    end

    # @param [String] json
    # @return [Host]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Host]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id username password usageTags address metadata state)
        .to_set)
        raise UnexpectedFormat, "Invalid host hash: #{hash}"
      end
      new(hash["id"], hash["state"], hash["username"], hash["password"], hash["availabilityZone"], hash["usageTags"],
          hash["address"], hash["metadata"], hash["esxVersion"])
    end

    # @param [String] host_id
    # @return [Host]
    def self.find_host_by_id(host_id)
      Config.client.mgmt_find_host_by_id(host_id)
    end

    # @param [String] host_id
    # @return [Boolean]
    def self.delete(host_id)
      Config.client.mgmt_delete_host(host_id)
    end

    # @param [String] host_id
    # @return [VmList]
    def self.get_host_vms(host_id)
      Config.client.mgmt_get_host_vms(host_id)
    end

    # @param [String] host_id
    # @return [TaskList]
    def self.find_tasks_by_host_id(host_id)
      Config.client.find_tasks_by_host_id(host_id)
    end

    # @param [String] host_id
    # @return [Host]
    def self.enter_maintenance_mode(host_id)
      Config.client.host_enter_maintenance_mode(host_id)
    end

    # @param [String] host_id
    # @return [Host]
    def self.enter_suspended_mode(host_id)
      Config.client.host_enter_suspended_mode(host_id)
    end

    # @param [String] host_id
    # @return [Host]
    def self.exit_maintenance_mode(host_id)
      Config.client.host_exit_maintenance_mode(host_id)
    end

    # @param [String] host_id
    # @return [Host]
    def self.resume(host_id)
      Config.client.host_resume(host_id)
    end

    # @param [String] host_id
    # @param [HostSetAvailabilityZoneSpec] spec
    # @return [Host]
    def self.host_set_availability_zone(host_id, spec)
      Config.client.host_set_availability_zone(host_id, spec.to_hash)
    end

    # @param [String] id
    # @param [String] username
    # @param [String] password
    # @param [String] availability_zone
    # @param [Array<String>] usage_tags
    # @param [String] address
    # @param [Hash] metadata
    def initialize(id, state, username, password, availability_zone, usage_tags, address, metadata, esx_version)
      @id = id
      @state = state
      @username = username
      @password = password
      @availability_zone = availability_zone
      @usage_tags = usage_tags
      @address = address
      @metadata = metadata
      @esx_version = esx_version
    end

    def ==(other)
      @id == other.id && @state == other.state && @address == other.address && @username == other.username &&
          @password == other.password && @availability_zone == other.availability_zone &&
          @usage_tags == other.usage_tags && @metadata == other.metadata && @esx_version == other.esx_version
    end

    def to_spec()
      EsxCloud::HostCreateSpec.new(
        @username,
        @password,
        @usage_tags,
        @address,
        @metadata,
        @availability_zone)
    end
  end
end
