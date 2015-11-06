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
  module CommandDsl

    def usage(command_usage)
      @usage = command_usage
    end

    def desc(string)
      @desc = string
    end

    # @param [Symbol] method_name Method name
    def method_added(method_name)
      if @usage && @desc
        method = instance_method(method_name)
        unless method.arity == -1
          raise "Invalid command method #{self.name}##{method.name}, exactly 1 optional argument expected"
        end
        register_command(method, @usage, @desc)
      end
      @usage = nil
      @desc = nil
      @options = []
    end

    # @param [UnboundMethod] method Method implementing the command
    # @param [String] usage Command usage (used to parse command)
    # @param [String] desc Command description
    def register_command(method, usage, desc)
      EsxCloud::CliConfig.register_command(CommandHandler.new(self, method, usage, desc))
    end
  end
end
