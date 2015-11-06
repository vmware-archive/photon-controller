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

module EsxCloud::Cli
  module Commands
    class Tasks < Base

      usage "task show <task_id>"
      desc "Show task info"
      def show(args = [])
        task_id = shift_keyword_arg(args)
        if task_id.blank?
          usage_error("Please provide task ID")
        end

        parse_options(args)

        task = client.find_task_by_id(task_id)
        display_task task
      end

      usage "task monitor <task_id>"
      desc "Monitor task progress"
      def monitor(args = [])
        task_id = shift_keyword_arg(args)
        if task_id.blank?
          usage_error("Please provide task ID")
        end

        parse_options(args)

        task = client.poll_task("#{EsxCloud::ApiClient::TASKS_URL_BASE}/#{task_id}")
        display_task task
      end

      usage "task list [<options>]"
      desc "List tasks associated with the specified entity"
      def find_by_entity(args = [])
        entityId, entityKind, state = nil, nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-e", "--entityId ID", "Id of the entity") { |e| entityId = e }
          opts.on("-k", "--entityKind KIND", "Kind of the entity: tenant, project, vm etc") { |k| entityKind = k }
          opts.on("-s", "--state STATE", "State of the tasks") { |s| state = s }
        end

        parse_options(args, opts_parser)

        tasks = client.find_tasks(entityId, entityKind, state)
        render_task_list(tasks.items)
      end

      private

      def display_task(task)
        puts
        puts "Task\t#{task.id}"
        puts "Entity\t#{task.entity_kind} #{task.entity_id}"
        puts "State\t#{task.state}"
        puts

        display_task_errors_if_exists(task.errors.flatten(1), "error")
        display_task_errors_if_exists(task.warnings.flatten(1), "warning")
      end

      def display_task_errors_if_exists(task_errors, level)
        unless task_errors.empty?
          puts
          if task_errors.size == 1
            puts "The following #{level} was encountered while running the task:"
            puts
            puts red "  #{task_errors[0].code}: #{task_errors[0].message}"
          else
            puts red "  #{task_errors.size} #{level}s were encountered while running this task:"
            puts
            task_errors.each_with_index do |task_error, i|
              puts "#{i+1}. #{task_error.code}: #{task_error.message}"
            end
          end

          puts
        end
      end
    end
  end
end
