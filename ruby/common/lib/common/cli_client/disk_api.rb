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
  class CliClient
    module DiskApi

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Disk]
      def create_disk(project_id, payload)
        project = find_project_by_id(project_id)
        tenant = @project_to_tenant[project.id]

        cmd = "disk create -t '#{tenant.name}' -p '#{project.name}' -n '#{payload[:name]}' -f '#{payload[:flavor]}' -g '#{payload[:capacityGb]}'"

        if payload[:affinities] && !payload[:affinities].empty?
          affinities = payload[:affinities].map do |affinities|
            "#{affinities[:kind]} #{affinities[:id]}"
          end.join(", ")
          cmd += " -a '#{affinities}'"
        end

        if payload[:tags] && !payload[:tags].empty?
          tags = payload[:tags].map do |tag|
            "#{tag}"
          end.join(", ")
          cmd += " -s '#{tags}'"
        end

        run_cli(cmd)
        disk = find_disks_by_name(project_id, payload[:name]).items[0]
        @disk_to_project[disk.id] = project

        disk
      end

      # @param [String] id
      # @return [Boolean]
      def delete_disk(id)

        run_cli("disk delete '#{id}'")

        true
      end

      # @param [String] project_id
      # @return [DiskList]
      def find_all_disks(project_id)
        @api_client.find_all_disks(project_id)
      end

      # @param [String] id
      # @return [Disk]
      def find_disk_by_id(id)
        @api_client.find_disk_by_id(id)
      end

      # @param [String] project_id
      # @param [String] name
      # @return [DiskList]
      def find_disks_by_name(project_id, name)
        @api_client.find_disks_by_name(project_id, name)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_disk_tasks(id, state = nil)
        @api_client.get_disk_tasks(id, state)
      end

    end
  end
end
