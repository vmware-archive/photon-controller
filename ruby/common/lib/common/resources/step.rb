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
  class Step

    attr_reader :state, :sequence, :operation, :errors, :warnings, :start_time, :end_time

    # @param [String] json Raw JSON
    # @return [Step] Step model
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Step]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) &&
        (hash.keys.to_set.superset?(%w(state sequence operation).to_set) ||
          hash.keys.to_set.superset?(%w(executionStatus sequence operation).to_set))
        raise UnexpectedFormat, "Invalid Step hash: #{hash}"
      end

      errors = Task.parse_task_errors(hash, hash["errors"])
      warnings = Task.parse_task_errors(hash, hash["warnings"])
      new(hash["state"] || hash["executionStatus"], hash["sequence"], hash["operation"],
          errors, warnings, hash["startedTime"], hash["endTime"])
    end

    # @param [String] state
    # @param [String] sequence
    # @param [TaskError[]] errors
    # @param [TaskError[]] warnings
    # @param [String] operation
    # @param [Integer] start_time
    # @param [Integer] end_time
    def initialize(state, sequence, operation, errors, warnings,  start_time, end_time)
      @state = state
      @sequence = sequence
      @operation = operation
      @errors = errors
      @warnings = warnings
      @start_time = start_time
      @end_time = end_time
    end

    def ==(other)
      @state == other.state &&
        @sequence == other.sequence &&
        @operation == other.operation &&
        @errors == other.errors &&
        @warnings == other.warnings &&
        @start_time == other.start_time &&
        @end_time == other.end_time
    end

    def duration
      return nil if @start_time.nil? || @end_time.nil?
      @end_time - @start_time
    end
  end
end
