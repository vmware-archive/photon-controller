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

require "thread"
require "rspec"
require "yaml"
require "socket"

class TestCounter
  def initialize
    @counter = 0
  end

  def next
    @counter += 1
  end
end

def install_logging_hooks(rspec_config)
  counter = TestCounter.new
  log_dir = File.expand_path(File.join(File.dirname(__FILE__), "..", "reports", "log", ENV["DRIVER"]))

  FileUtils.mkdir_p(log_dir)

  rspec_config.before :all do |example_group|
    reset_logger(log_dir, counter, "before_#{example_group.class.description}")
  end

  # N.B. For now 'after all' hook for each example group will get appended to the last test in the group
  rspec_config.before :each do |example_group|
    full_example_name = "%s %s" % [example_group.class.description, example_group.example.description]
    reset_logger(log_dir, counter, full_example_name)
  end
end

def reset_logger(log_dir, counter, name)
  path = "%03d_%s.log" % [counter.next, name.gsub(/[^a-z0-9\s_-]/i, "").gsub(/\s+/, "_") ]
  log_file_name = File.join(log_dir, path)

  logger = Logger.new(log_file_name)
  logger.level = Logger::DEBUG

  EsxCloud::Config.logger = logger
  EsxCloud::Config.log_file_name = log_file_name
end
