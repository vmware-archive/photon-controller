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
  class Config

    class << self
      # @return [ApiClient]
      attr_accessor :client

      # @return [Logger]
      attr_accessor :logger

      # @return [string]
      attr_accessor :log_file_name

      def client
        return @client if @client
        raise EsxCloud::Error, "API client not initialized"
      end

      def init
        @client = nil
        @logger = Logger.new(STDOUT)
        @logger.level = debug? ? Logger::DEBUG : Logger::WARN
        @log_file_name = ""
      end

      def debug?
        ENV["DEBUG"] && ENV["DEBUG"] != ""
      end
    end
  end
end
