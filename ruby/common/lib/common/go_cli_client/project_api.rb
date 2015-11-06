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
    module ProjectApi
      # @param [String] tenant_id
      # @param [Hash] payload
      # @return [Project]
      def create_project(tenant_id, payload)
        @api_client.create_project(tenant_id, payload)
      end

      # @param [String] id
      # @return [Project]
      def find_project_by_id(id)
        @api_client.find_project_by_id(id)
      end

      # @param [String] tenant_id
      # @return [ProjectList]
      def find_all_projects(tenant_id)
        @api_client.find_all_projects(tenant_id)
      end

      # @param [String] name
      # @return [ProjectList]
      def find_projects_by_name(tenant_id, name)
        @api_client.find_projects_by_name(tenant_id, name)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_project(id)
        @api_client.delete_project(id)
      end

      # @param [String] id
      # @return [VmList]
      def get_project_vms(id)
        @api_client.get_project_vms(id)
      end

      # @param [String] id
      # @return [DiskList]
      def get_project_disks(id)
        @api_client.get_project_disks(id)
      end

      # @param [String] id
      # @return [TaskList]
      def get_project_tasks(id, state = nil, kind = nil)
        @api_client.get_project_tasks(id, state, kind)
      end

      # @param [String] id
      # @return [ClusterList]
      def get_project_clusters(id)
        @api_client.get_project_clusters(id)
      end

      # @param [String] id
      # @param [Hash] payload
      def set_project_security_groups(id, payload)
        @api_client.set_project_security_groups(id, payload)
      end
    end
  end
end
