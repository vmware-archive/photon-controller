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
        tenant = find_tenant_by_id(tenant_id)

        cmd = "project create -t '#{tenant.name}' -n '#{payload[:name]}' -r '#{payload[:resourceTicket][:name]}'"
        limits = payload[:resourceTicket][:limits].map { |limit|
          "#{limit[:key]} #{limit[:value]} #{limit[:unit]}"
        }.join(", ")
        cmd += " -l '#{limits}'"
        security_groups = payload[:securityGroups]
        cmd += " -g '#{security_groups.join(",")}'" if security_groups

        project_id = run_cli(cmd)
        project = find_project_by_id(project_id)

        @project_to_tenant[project.id] = tenant

        project
      end

      # @param [String] id
      # @return [Project]
      def find_project_by_id(id)
        result = run_cli("project show #{id}")
        get_project_from_response result
      end

      # @param [String] tenant_id
      # @return [ProjectList]
      def find_all_projects(tenant_id)
        tenant = find_tenant_by_id tenant_id
        result = run_cli("project list -t '#{tenant.name}'")

        get_project_list_from_response result
      end

      # @param [String] name
      # @return [ProjectList]
      def find_projects_by_name(tenant_id, name)
        @api_client.find_projects_by_name(tenant_id, name)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_project(id)
        cmd = "project delete #{id}"
        run_cli(cmd)
        true
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
        cmd = "project tasks '#{id}'"
        cmd += " -s '#{state}'" if state
        cmd += " -k '#{kind}'" if kind

        result = run_cli(cmd)
        get_task_list_from_response(result)
      end

      # @param [String] id
      # @return [ClusterList]
      def get_project_clusters(id)
        @api_client.get_project_clusters(id)
      end

      # @param [String] id
      # @return [VirtualNetworkList]
      def get_project_networks(id)
        @api_client.get_project_networks(id)
      end

      # @param [String] id
      # @param [Hash] payload
      def set_project_security_groups(id, payload)
        cmd = "project set_security_groups '#{id}' '#{payload[:items].join(",")}'"
        run_cli(cmd)
      end

      private

      def get_project_from_response(result)
        result.slice! "\n"
        values = result.split("\t", -1)
        project_hash = Hash.new
        project_hash["id"]    = values[0] unless values[0] == ""
        project_hash["name"]  = values[1] unless values[1] == ""
        project_hash["resourceTicket"]  = getResourceTicket(values[2], values[3], values[4], values[5])
        project_hash["securityGroups"]  = getSecurityGroups(values[6])

        Project.create_from_hash(project_hash)
      end

      def get_project_list_from_response(result)
        projects = result.split("\n").map do |project_info|
          get_project_details project_info.split("\t")[0]
        end
        ProjectList.new(projects.compact)
      end

      def get_project_details(project_id)
        begin
          find_project_by_id project_id

          # When listing all projects, if a project gets deleted
          # handle the Error to return nil for that project to
          # create project list for the projects that exist.
        rescue EsxCloud::CliError => e
          raise() unless e.message.include? "NotFound"
          nil
        end
      end

      def getResourceTicket(id, name, limits, usage)
        rt_hash = Hash.new
        rt_hash["tenantTicketId"]   = id
        rt_hash["tenantTicketName"] = name
        rt_hash["limits"] = getLimitsOrUsage(limits)
        rt_hash["usage"] = getLimitsOrUsage(usage)

        rt_hash
      end
    end
  end
end
