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
  class Locality

    attr_reader :id, :kind

    # @param [String] id
    # @param [String] kind
    def initialize(id, kind)
      @id = id
      @kind = kind
    end

    def to_hash
      {
          :id => @id,
          :kind => @kind,
      }
    end

    def ==(other)
      @id==other.id && @kind==other.kind
    end
  end
end
