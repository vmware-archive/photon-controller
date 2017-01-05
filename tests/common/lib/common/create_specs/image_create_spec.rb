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
  class ImageCreateSpec

    attr_accessor :name, :replication_type

    # @param [String] name
    # @param [String] replication_type
    def initialize(name, replication_type)
      @name = name
      @replication_type = replication_type
    end

    def to_hash
      {
          name: @name,
          replicationType: @replication_type
      }
    end

    def ==(other)
      @name == other.name && @replication_type == other.replication_type
    end
  end
end
