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
    module ProjectApi

      PROJECTS_ROOT = "/projects"

      # @param [String] tenant_id
      # @param [Hash] payload
      # @return [Project]
      def create_project(tenant_id, payload)
        response = @http_client.post_json("/tenants/#{tenant_id}/projects", payload)
        check_response("Create project (#{tenant_id}, #{payload})", response, 201)

        task = poll_response(response)
        find_project_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Project]
      def find_project_by_id(id)
        response = @http_client.get("#{PROJECTS_ROOT}/#{id}")
        check_response("Get project by ID '#{id}'", response, 200)

        Project.create_from_json(response.body)
      end

      # @param [String] tenant_id
      # @return [ProjectList]
      def find_all_projects(tenant_id)
        response = @http_client.get("/tenants/#{tenant_id}/projects")
        check_response("Find all projects for tenant '#{tenant_id}'", response, 200)

        ProjectList.create_from_json(response.body)
      end

      # @param [String] name
      # @return [ProjectList]
      def find_projects_by_name(tenant_id, name)
        response = @http_client.get("/tenants/#{tenant_id}/projects?name=#{name}")
        check_response("Find projects by name '#{name}' for tenant #{tenant_id}", response, 200)

        ProjectList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_project(id)
        response = @http_client.delete("#{PROJECTS_ROOT}/#{id}")
        check_response("Delete project '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] id
      # @return [VmList]
      def get_project_vms(id)
        response = @http_client.get("#{PROJECTS_ROOT}/#{id}/vms")
        check_response("Get VMs for project '#{id}'", response, 200)

        VmList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [VmList]
      def get_project_disks(id)
        response = @http_client.get("#{PROJECTS_ROOT}/#{id}/disks")
        check_response("Get DISKs for project '#{id}'", response, 200)

        DiskList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [TaskList]
      def get_project_tasks(id, state = nil, kind = nil)
        url = "#{PROJECTS_ROOT}/#{id}/tasks"
        params = []
        params << "kind=#{kind}" if kind
        params << "state=#{state}" if state
        url += "?#{params.join("&")}" unless params.empty?

        response = @http_client.get(url)
        check_response("Get tasks for project '#{id}'", response, 200)

        TaskList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [ClusterList]
      def get_project_clusters(id)
        response = @http_client.get("#{PROJECTS_ROOT}/#{id}/clusters")
        check_response("Get clusters for project '#{id}'", response, 200)

        ClusterList.create_from_json(response.body)
      end

      # @param [String] id
      # @param [String] name
      # @return [VirtualNetworkList]
      def get_project_networks(id, name = nil)
        url = "#{PROJECTS_ROOT}/#{id}/subnets"
        url += "?name=#{name}" if name

        response = @http_client.get(url)
        check_response("Get networks for project '#{id}'", response, 200)

        VirtualNetworkList.create_from_json(response.body)
      end

      # @param [String] id
      # @param [Hash] payload
      def set_project_security_groups(id, payload)
        response = @http_client.post_json("#{PROJECTS_ROOT}/#{id}/set_security_groups", payload)
        check_response("Set the security groups for project '#{id}'", response, 200)

        poll_response(response)
      end
    end
  end
end
