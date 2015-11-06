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
    module TaskApi

      # @param [String] id
      # @return [Task]
      def find_task_by_id(id)
        @api_client.find_task_by_id id
      end

      # @param [String] entity_id
      # @param [String] entity_kind
      # @param [String] state
      # @return [TaskList]
      def find_tasks(entity_id = nil, entity_kind = nil, state = nil)
        @api_client.find_tasks entity_id, entity_kind, state
      end
    end
  end
end
