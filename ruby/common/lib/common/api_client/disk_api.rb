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
    module DiskApi

      DISKS_ROOT = "/disks"

      # @param [String] project_id
      # @param [Hash] payload
      # @return [Disk]
      def create_disk(project_id, payload)
        response = @http_client.post_json("/projects/#{project_id}/disks", payload)
        check_response("Create Disk (#{project_id}) #{payload}", response, 201)

        task = poll_response(response)
        find_disk_by_id(task.entity_id)
      end

      # @param [String] id
      # @return [Boolean]
      def delete_disk(id)
        response = @http_client.delete("#{DISKS_ROOT}/#{id}")
        check_response("Delete Disk '#{id}'", response, 201)

        poll_response(response)
        true
      end

      # @param [String] project_id
      # @return [DiskList]
      def find_all_disks(project_id)
        response = @http_client.get("/projects/#{project_id}/disks")
        check_response("Find all Disks for project '#{project_id}'", response, 200)

        DiskList.create_from_json(response.body)
      end

      # @param [String] project_id
      # @return [DiskList]
      def find_all_disks_pagination(project_id)
        response = @http_client.getAll("/projects/#{project_id}/disks?pageSize=1")
        check_response("Find all Disks for project '#{project_id}'", response, 200)

        DiskList.create_from_json(response.body)
      end

      # @param [String] id
      # @return [Disk]
      def find_disk_by_id(id)
        response = @http_client.get("#{DISKS_ROOT}/#{id}")
        check_response("Find Disk by ID '#{id}'", response, 200)

        Disk.create_from_json(response.body)
      end

      # @param [String] project_id
      # @param [String] name
      # @return [DiskList]
      def find_disks_by_name(project_id, name)
        response = @http_client.get("/projects/#{project_id}/disks?name=#{name}")
        check_response("Find Disks by name '#{name}' in project '#{project_id}'", response, 200)

        DiskList.create_from_json(response.body)
      end

      # @param [String] id
      # @param [String] state
      # @return [TaskList]
      def get_disk_tasks(id, state = nil)
        url = "#{DISKS_ROOT}/#{id}/tasks"
        url += "?state=#{state}" if state
        response = @http_client.get(url)
        check_response("Get tasks for Disk '#{id}'", response, 200)

        TaskList.create_from_json(response.body)
      end
    end
  end
end
