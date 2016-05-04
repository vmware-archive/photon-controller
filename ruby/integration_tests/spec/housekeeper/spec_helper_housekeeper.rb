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

require "rspec"

gemfile = File.expand_path("../../Gemfile", __FILE__)

if File.exists?(gemfile)
  ENV["BUNDLE_GEMFILE"] = gemfile
  require "rubygems"
  require "bundler/setup"
end

require_relative "../../lib/integration"
require_relative "../../lib/dcp/houskeeper_client"
require_relative "../support/log_helper"
require_relative "../support/housekeeper_helper"

EsxCloud::Config.init

RSpec.configure do |config|
  config.color = true
  config.formatter = :documentation
  install_logging_hooks(config)
  HousekeeperHelper.clean_unreachable_datastores if ENV["UPTIME"]
end
