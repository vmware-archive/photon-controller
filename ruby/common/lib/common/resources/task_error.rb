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
  class TaskError
    attr_reader :step, :code, :message, :data

    def initialize(step, code, message, data = {})
      @step = step
      @code = code
      @message = message
      @data = data
    end

    def ==(other)
      return false if @step.nil? ^ other.step.nil?

      (@step.nil? ||
        (@step["operation"] == other.step["operation"] &&
        @step["sequence"] == other.step["sequence"])) &&
        @code == other.code &&
        @message == other.message &&
        @data == other.data
    end

    def to_s
      error_info = "#{@code}: #{@message}, data: #{@data.inspect}"
      step.nil? ? error_info :
        "step: #{@step["operation"]} sequence #{@step["sequence"]}, #{error_info}"
    end

  end
end
