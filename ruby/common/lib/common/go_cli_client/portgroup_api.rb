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
  class GoCliClient
    module PortGroupApi

      # @param [String] id
      # @return [PortGroup]
      def find_portgroup_by_id(id)
        @api_client.find_portgroup_by_id(id)
      end

      # @param [String] name
      # @param [String] usage_tag
      # @return [PortGroupList]
      def find_portgroups(name, usage_tag)
        @api_client.find_portgroups(name, usage_tag)
      end
    end
  end
end
