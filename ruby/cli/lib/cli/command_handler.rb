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
  class CommandHandler

    # @return [String]
    attr_reader :usage

    # @return [String]
    attr_reader :desc

    # @param [Class] klass
    # @param [UnboundMethod] method
    # @param [String] usage
    # @param [String] desc
    def initialize(klass, method, usage, desc)
      @klass = klass
      @method = method
      @usage = usage
      @desc = desc
    end

    # Run handler with provided args
    # @param [Array] args
    def run(args)
      cmd_instance = @klass.new(*[])
      cmd_instance.usage = @usage
      cmd_instance.send(@method.name, args)
    end

  end
end
