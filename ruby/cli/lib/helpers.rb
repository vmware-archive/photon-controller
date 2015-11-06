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
  module Helpers

    def logger
      Config.logger
    end

    def client
      cli_config.client
    end

    def cli_config
      CliConfig
    end

    def blank?
      return empty? if respond_to?(:empty?)
      to_s.strip.empty?
    end

    def red(message)
      "\e[0m\e[31m#{message}\e[0m"
    end

    def green(message)
      "\e[0m\e[32m#{message}\e[0m"
    end

    def yellow(message)
      "\e[0m\e[33m#{message}\e[0m"
    end

    def err(message)
      fail CliError, message
    end

  end
end
