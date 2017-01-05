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
        project = find_project_by_id(project_id)
        tenant = @project_to_tenant[project.id]

        if tenant.nil?
          return @api_client.find_all_disks(project_id)
        end

        result = run_cli("disk list -t '#{tenant.name}' -p '#{project.name}'")
        get_disk_list_from_response(result)
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
        project = find_project_by_id(project_id)
        tenant = @project_to_tenant[project.id]
        result = run_cli("disk list -t '#{tenant.name}' -p '#{project.name}' -n '#{name}'")
        get_disk_list_from_response(result)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_disk_tasks(id, state = nil)
        cmd = "disk tasks '#{id}'"
        cmd += " -s '#{state}'" if state

        result = run_cli(cmd)
        get_task_list_from_response(result)
      end

      private

      # @param [String] result
      # @return [Disk]
      def get_disk_from_response(result)
        result.slice! "\n"
        values = result.split("\t", -1)
        disk_hash = Hash.new
        disk_hash["id"]         = values[0] unless values[0] == ""
        disk_hash["name"]       = values[1] unless values[1] == ""
        disk_hash["state"]      = values[2] unless values[2] == ""
        disk_hash["kind"]       = values[3] unless values[3] == ""
        disk_hash["flavor"]     = values[4] unless values[4] == ""
        disk_hash["capacityGb"] = values[5].to_i unless values[5] == ""
        disk_hash["datastore"]  = values[6] unless values[6] == ""
        disk_hash["tags"]       = stringToArray(values[7])
        disk_hash["vms"]        = stringToArray(values[8])

        Disk.create_from_hash(disk_hash)
      end

      def get_disk_list_from_response(result)
        disks = result.split("\n").map do |disk_info|
          get_disk_details disk_info.split("\t")[0]
        end
        DiskList.new(disks.compact)
      end

      def get_disk_details(disk_id)
        begin
          find_disk_by_id disk_id

          # When listing all disks, if a disk gets deleted
          # handle the Error to return nil for that disk to
          # create Disk list for the disks that exist.
        rescue EsxCloud::CliError => e
          raise() unless e.message.include? "DiskNotFound"
          nil
        end
      end
    end
  end
end
