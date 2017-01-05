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
    module TaskApi

      TASKS_ROOT = "/tasks"

      # @param [String] id
      # @return [Task]
      def find_task_by_id(id)
        response = @http_client.get "#{TASKS_ROOT}/#{id}"
        check_response "Find Task by ID '#{id}'", response, 200

        Task.create_from_json response.body
      end

      # @param [String] entity_id
      # @param [String] entity_kind
      # @param [String] state
      # @return [TaskList]
      def find_tasks(entity_id = nil, entity_kind = nil, state= nil)
        response = @http_client.get find_tasks_request_path(entity_id, entity_kind, state)
        check_response "Find Task by Entity and State ('#{entity_id}', '#{entity_kind}', '#{state}')", response, 200

        TaskList.create_from_json response.body
      end

      private

      def find_tasks_request_path(entity_id, entity_kind, state)
        qry = find_tasks_qry_string entity_id, entity_kind, state
        request_path = TASKS_ROOT
        qry.empty? ? request_path : "#{request_path}?#{qry}"
      end

      def find_tasks_qry_string(entity_id, entity_kind, state)
        query_string = []
        query_string << "entityId=#{entity_id}" unless entity_id.nil?
        query_string << "entityKind=#{entity_kind}" unless entity_kind.nil?
        query_string << "state=#{state}" unless state.nil?

        query_string.join("&")
      end
    end
  end
end
