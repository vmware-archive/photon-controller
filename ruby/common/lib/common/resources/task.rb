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
  class Task

    attr_reader :id, :state, :entity_id, :entity_kind
    attr_accessor :operation, :steps, :start_time, :end_time, :resource_properties, :url

    # @param [String] id
    # @return [Task]
    def self.find_task_by_id(id)
      Config.client.find_task_by_id id
    end

    # @param [String] entity_id
    # @param [String] entity_kind
    # @param [String] state
    # @return [TaskList]
    def self.find_tasks(entity_id = nil, entity_kind = nil, state = nil)
      Config.client.find_tasks entity_id, entity_kind, state
    end

    # @param [String] json Raw JSON
    # @return [Task] Task model
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Task]
    def self.create_from_hash(hash)
      # check it is a hash and has id, state
      unless hash.is_a?(Hash) && (hash.keys.to_set.superset?(Set.new(%w(id state))))
        raise UnexpectedFormat, "Invalid task hash: #{hash}"
      end

      entity = {}
      unless hash["entity"].nil?
        entity = hash["entity"]
        unless entity.is_a?(Hash) && entity.keys.to_set.superset?(Set.new(%w(id kind)))
          raise UnexpectedFormat, "Invalid task entity hash: #{entity}"
        end
      end

      task = new(hash["id"], hash["state"], entity["id"], entity["kind"])
      if hash["operation"]
        task.operation = hash["operation"]
      end

      if hash["resourceProperties"]
        task.resource_properties = hash["resourceProperties"]
      end

      unless hash["steps"].nil?
        hash["steps"].each do |step|
          task.steps << Step.create_from_hash(step)
        end
      end

      task.start_time = hash["startedTime"]
      task.end_time = hash["endTime"]

      task
    end

    def self.parse_task_errors(step, task_errors)
      result = []
      unless task_errors.nil?
        unless task_errors.is_a?(Array)
          raise UnexpectedFormat, "Invalid step task error format: #{task_errors}"
        end

        unless task_errors.empty?
          result << task_errors.map do |task_error|
            TaskError.new(step, task_error["code"], task_error["message"], task_error["data"])
          end
        end
      end
      result
    end

    # @param [String] id
    # @param [String] state
    # @param [String] entity_id
    # @param [String] entity_kind
    def initialize(id, state, entity_id, entity_kind)
      @id = id
      @state = state
      @entity_id = entity_id
      @entity_kind = entity_kind
      @operation = nil
      @steps = []
      @start_time = nil
      @end_time = nil
      @resource_properties = nil
    end

    def ==(other)
      @id == other.id && @state == other.state && @entity_id == other.entity_id &&
          @entity_kind == other.entity_kind && @operation == other.operation &&
          @steps == other.steps && @start_time == other.start_time && @end_time == other.end_time &&
          @resource_properties == other.resource_properties
    end

    def duration
      return nil if @start_time.nil? || @end_time.nil?
      @end_time - @start_time
    end

    def errors
      steps
        .map { |step| step.errors }
        .flatten 1
    end

    def warnings
      steps
        .map { |step| step.warnings }
        .flatten 1
    end

  end
end
