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
  class HostCreateSpec

    attr_accessor :username, :password, :usage_tags, :address, :metadata, :availability_zone

    # @param [String] username
    # @param [String] password
    # @param [Array<String>] usage_tags
    # @param [String] address
    # @param [Hash] metadata
    # @param [String] availability_zone
    def initialize(username, password, usage_tags, address, metadata = {}, availability_zone = nil)
      @username = username
      @password = password
      @usage_tags = usage_tags
      @address = address
      @metadata = metadata
      @availability_zone = availability_zone
    end

    def to_hash
      {
          username: @username,
          password: @password,
          availabilityZone: @availability_zone,
          usageTags: @usage_tags,
          address: @address,
          metadata: @metadata.nil? ? {} : @metadata
      }
    end

    def ==(other)
      @address == other.address && @username == other.username && @password == other.password &&
        @availability_zone == other.availability_zone && @usage_tags == other.usage_tags && @metadata == other.metadata
    end
  end
end
