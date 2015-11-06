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

require "open3"

module EsxCloud
  class CmdRunner

    # @param [String] command
    # @return [String]
    def self.run(command, separate_std_out_and_err = nil, error_filter = nil)
      with_clean_bundler_env do
        Config.logger.debug("Running command \"#{command}\"")

        if separate_std_out_and_err.nil?
          Open3.popen2e(command) do |stdin, std_out_err, wait_thread|
            exit_status = wait_thread.value
            Config.logger.debug("Exit status is #{exit_status}")

            output = read_io(std_out_err)
            Config.logger.debug("Output: #{output}")

            unless exit_status.success?
              raise EsxCloud::CliError, output
            end

            output
          end
        else
          Open3.popen3(command) do |stdin, stdout, stderr, wait_thread|
            exit_status = wait_thread.value
            Config.logger.debug("Exit status is #{exit_status}")

            unless exit_status.success?
              errors = read_io(stderr, error_filter)
              Config.logger.debug("Errors: #{errors}")

              raise EsxCloud::CliError, errors
            end

            output = read_io(stdout)
            Config.logger.debug("Outputs: #{output}")

            output
          end
        end
      end
    end

    private

    def self.with_clean_bundler_env
      defined?(Bundler) ? Bundler.with_clean_env { yield } : yield
    end

    def self.read_io(io, filter = nil)
      if filter.nil?
        io.read
      else
        output = ""
        while line = io.gets
          output.concat(line) if filter.match(line)
        end

        output
      end
    end
  end
end
