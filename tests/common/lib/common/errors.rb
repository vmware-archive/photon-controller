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
  class Error < StandardError; end

  # API errors not associated with any task are returned as a JSON array.
  # This class takes care of parsing and representing them as an exception.
  class ApiError < Error

    attr_reader :operation, :response_code, :message, :errors, :entity_id

    # @param [EsxCloud::Task] task
    def self.create_from_task(task)
      self.new(task.operation, 200, "#{task.operation} failed", task.errors, task.entity_id)
    end

    # @param [String] operation
    # @param [HttpResponse] response
    def self.create_from_http_error(operation, response)
      case response.code
        when 400
          message = "Bad request"
        when 401
          message = "Unauthorized"
        when 403
          message = "Forbidden"
        else
          message = "#{operation}: HTTP #{response.code}"
      end
      begin
        errors = JSON.parse(response.body)
      rescue JSON::ParserError
        errors = []
      end

      self.new(operation, response.code, message, errors)
    end

    # @param [String] operation
    # @param [Integer] response_code
    # @param [String] message
    # @param [Array] errors
    # @param [String] entity_id
    def initialize(operation, response_code, message, errors, entity_id = nil)
      @operation = operation
      @response_code = response_code
      @message = message
      @entity_id = entity_id
      @errors = errors.is_a?(Array) ? errors : [errors]

      @errors = convert_to_task_error(@errors)
    end

    def message
      "#{@message}, errors: #{@errors}, entity_id: #{@entity_id}, response_code: #{@response_code}, operation: #{@operation}"
    end

    private

    # Right now API produces 2 kinds of errors:
    # 1. validation errors (array of strings)
    # 2. API errors (hash with 'code', 'message' and 'data' or array of these hashes)
    # Unless these 2 are consolidated we need a special treatment below:
    def convert_to_task_error(error_array)
      error_array.map do |error|
        if error.is_a?(Array)
          convert_to_task_error(error)
        elsif error.is_a?(TaskError)
          error
        elsif error.is_a?(Hash)
          TaskError.new(nil, error["code"], error["message"], error["data"])
        else
          TaskError.new(nil, nil, error.to_s)
        end
      end
    end

  end

  class CliError < Error
    attr_reader :output, :message

    def initialize(output)
      @output = output
      @message = output
    end
  end

  class UnexpectedFormat < Error; end
  class NotImplemented < Error; end
  class NotFound < Error; end

end
