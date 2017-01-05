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
        result = run_cli("task show #{id}")
        get_task_from_response(result)
      end

      # @param [String] entity_id
      # @param [String] entity_kind
      # @param [String] state
      # @return [TaskList]
      def find_tasks(entity_id = nil, entity_kind = nil, state = nil)
        cmd = "task list"
        cmd += " -e '#{entity_id}'" if entity_id
        cmd += " -k '#{entity_kind}'" if entity_kind
        cmd += " -s '#{state}'" if state

        result = run_cli(cmd)
        get_task_list_from_response(result)
      end

      private

      def get_task_from_response(result)
        values = result.split("\n", -1)
        # Splitting on (\n, -1) includes an empty extra entry to the values
        # hence remove the last entry
        values.pop
        task_attributes = values[0].split("\t", -1)
        task_hash = Hash.new
        task_hash["id"]          = task_attributes[0] unless task_attributes[0] == ""
        task_hash["state"]       = task_attributes[1] unless task_attributes[1] == ""
        task_hash["entity"]      = get_entity_hash(task_attributes[2], task_attributes[3]) unless task_attributes[2] == "" || task_attributes[3] == ""
        task_hash["operation"]   = task_attributes[4] unless task_attributes[4] == ""
        task_hash["startedTime"] = task_attributes[5].to_i unless task_attributes[5] == ""
        task_hash["endTime"]     = task_attributes[6].to_i unless task_attributes[6] == ""
        task_hash["resourceProperties"] = JSON.parse(task_attributes[7]) unless task_attributes[7] == ""
        if values.size > 1
          task_hash["steps"] = getSteps(values)
        end
        Task.create_from_hash(task_hash)
      end

      def get_entity_hash(entity_id, entity_kind)
        entity_hash = Hash.new
        entity_hash["id"]    = entity_id unless entity_id == ""
        entity_hash["kind"]  = entity_kind unless entity_kind == ""

        entity_hash
      end

      def getSteps(values)
        steps = Array.new
        i = 1
        while i < values.size
          steps << get_step_hash(values[i])
          i += 1
        end
        steps
      end

      def get_step_hash(result)
        values = result.split("\t", -1)
        step_hash = Hash.new
        step_hash["sequence"]    = values[0].to_i unless values[0] == ""
        step_hash["operation"]   = values[1] unless values[1] == ""
        step_hash["state"]       = values[2] unless values[2] == ""
        step_hash["startedTime"] = values[3].to_i unless values[3] == ""
        step_hash["endTime"]     = values[4].to_i unless values[4] == ""
        step_hash["errors"]      = getAPIErrors(values[5]) unless values[5] == ""
        step_hash["warnings"]    = getAPIErrors(values[6]) unless values[6] == ""
        step_hash
      end

      def getAPIErrors(apiErrors)
        stepErrors = Array.new
        errorCodes = stringToArray(apiErrors)
        errorCodes.each do |errorCode|
          stepErrors << {"code" => errorCode}
        end
        stepErrors
      end

      def get_task_list_from_response(result)
        tasks = result.split("\n").map do |task_info|
          get_task_details task_info.split("\t")[0]
        end

        TaskList.new(tasks.compact)
      end

      def get_task_details(task_id)
        begin
          find_task_by_id task_id

          # When listing all tasks, if a task gets deleted
          # handle the Error to return nil for that task to
          # create task list for the tasks that exist.
        rescue EsxCloud::CliError => e
          raise() unless e.message.include? "NotFound"
          nil
        end
      end

    end
  end
end
