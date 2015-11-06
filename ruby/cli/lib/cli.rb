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

$LOAD_PATH.unshift(File.expand_path(File.dirname(__FILE__) + "../../../common/lib"))

module EsxCloud
  module Cli
    VERSION = "0.1"
  end
end

require "highline/import"
require "optparse"
require "terminal-table"
require "yaml"
require "formatador"


require "common"
require "helpers"
require "cli_config"

require "cli/command_dsl"
require "cli/command_handler"
require "cli/runner"
require "cli/task_tracker"

EsxCloud::CliConfig.init
EsxCloud::Config.init

class Object
  include EsxCloud::Helpers
end

require "cli/commands/input"
require "cli/commands/ui"
require "cli/commands/base"


