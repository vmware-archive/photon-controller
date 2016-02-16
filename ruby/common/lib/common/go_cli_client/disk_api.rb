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
    module DiskApi

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Disk]
      def create_disk(project_id, payload)
        project = find_project_by_id(project_id)
        tenant = @project_to_tenant[project.id]

        cmd = "disk create -t '#{tenant.name}' -p '#{project.name}' -n '#{payload[:name]}' -f '#{payload[:flavor]}' -c '#{payload[:capacityGb]}'"

        if payload[:affinities] && !payload[:affinities].empty?
          affinities = payload[:affinities].map do |affinities|
            "#{affinities[:kind]}:#{affinities[:id]}"
          end.join(", ")
          cmd += " -a '#{affinities}'"
        end

        if payload[:tags] && !payload[:tags].empty?
          tags = payload[:tags].map do |tag|
            "#{tag}"
          end.join(", ")
          cmd += " -s '#{tags}'"
        end

        disk_id = run_cli(cmd)

        disk = find_disk_by_id(disk_id)
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
        cmd = "disk show #{id}"
        result = run_cli(cmd)
        get_disk_from_response(result)
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
      private

      # @param [String] result
      # @return [Host]
      def get_disk_from_response(result)
        values = result.split
        disk_hash = { "id" => values[0],
                      "name" => values[1],
                      "state" => values[2],
                      "kind" => values[3],
                      "flavor" => values[4],
                      "capacityGb" => values[5].to_i,
                      "datastore" => values[6],
                      "tags" => stringToArray(values[7]),
                      "vms" => stringToArray(values[8]) }
        Disk.create_from_hash(disk_hash)
      end

      # @param [String] result
      # @return [Array]
      def stringToArray(result)
        values = Array.new
        if result.to_s != ''
          values = result.split(',')
        end
        values
      end
    end
  end
end
