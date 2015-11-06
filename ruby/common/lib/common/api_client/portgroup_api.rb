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
  class ApiClient
    module PortGroupApi

      PORTGROUPS_ROOT = "/portgroups"

      # @param [String] id
      # @return [PortGroup]
      def find_portgroup_by_id(id)
        response = @http_client.get("#{PORTGROUPS_ROOT}/#{id}")
        check_response("Find portgroup by ID '#{id}'", response, 200)

        PortGroup.create_from_json(response.body)
      end

      # @param [String] name
      # @param [String] usage_tag
      # @return [PortGroupList]
      def find_portgroups(name, usage_tag)
        url = PORTGROUPS_ROOT
        name = "name=" + name if name
        usage_tag = "usageTag=" + usage_tag if usage_tag

        url += "?" if name || usage_tag
        if name && usage_tag
          url += [name, usage_tag].join("&")
        else
          url += name || usage_tag || ""
        end

        response = @http_client.get(url)
        check_response("List portgroups by '#{url}'", response, 200)

        PortGroupList.create_from_json(response.body)
      end
    end
  end
end
