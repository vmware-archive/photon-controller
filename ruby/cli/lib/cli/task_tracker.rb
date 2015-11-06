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
  module Cli
    class TaskTracker

      def initialize
        @stream = STDOUT
        @formatador = Formatador.new
        @iteration = 0
      end

      def task_progress(task)
        # backoff is done in the task polling loop which ensures that we won't
        # print too often.
        if stream.tty?
          @task = task
          animate_progress
        else
          stream.print "."
        end
      end

      def task_done(_)
        if stream.tty?
          @task = nil
          @thread.join unless @thread.nil?
          @thread = nil
        else
          stream.puts
        end
      end

      private

      attr_reader :stream, :formatador, :task

      def animate_progress
        @thread ||= Thread.new do
          formatador.display_line
          loop do
            break if task.nil?
            formatador.redisplay "[green]#{spinner} [/]: #{task_duration} : #{task.operation} : #{step_status || task_status}"
            @iteration += 1
            sleep 0.5
          end
          formatador.redisplay "\r"
        end
      end

      def step_status
        active_step = task.steps.select { |s| s.state == "STARTED" }
        if active_step.size == 1
          "#{active_step[0].operation} |#{active_step[0].sequence + 1} of #{task.steps.size + 1}|"
        else
          nil
        end
      end

      def task_duration
        start_time = task.start_time || 0
        ts = Time.now - Time.at(start_time / 1000)
        sprintf("%02d:%02d:%02d", ts / 3600, (ts / 60) % 60, ts % 60)
      end

      def task_status
        "#{task.state}"
      end

      def spinner
        cursor = @iteration % 3
        "|#{' ' * (cursor)}=#{' ' * (2 - cursor)}|"
      end
    end
  end
end
