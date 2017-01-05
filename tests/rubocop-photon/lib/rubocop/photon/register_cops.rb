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

require 'yaml'

module RuboCop
  module Photon
    module RegisterCops
      def self.register!
        hash = YAML.load_file(default_file)
        puts "configuration from #{default_file}" if ConfigLoader.debug?
        config = ConfigLoader.merge_with_default(hash, default_file)

        ConfigLoader.instance_variable_set(:@default_configuration, config)
      end

      private

      def self.default_file
        @default_file ||= File.expand_path(
            '../../../../config/default.yml', __FILE__
        )
      end
    end
  end
end
