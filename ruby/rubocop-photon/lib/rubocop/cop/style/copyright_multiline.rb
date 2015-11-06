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

module RuboCop
  module Cop
    module Style
      # Check that a copyright notice was given in each source file.
      #
      # Style/Copyright:
      #   Notice:
      #     - '^# Copyright (\(c\) )?2\d{3} Acme Inc'
      #     - '^#'
      #
      class CopyrightMultiline < Cop
        def message
          "Include a copyright notice matching /#{notice.join('\n')}/" \
          'before any code.'
        end

        def notice
          cop_config['Notice']
        end

        def investigate(processed_source)
          return if notice.empty?
          return if notice_found?(processed_source)
          range = source_range(processed_source.buffer, 1, 0)
          add_offense(range, range, message)
        end

        def notice_found?(processed_source)
          start_token_idx = notice_start processed_source
          log = "#{start_token_idx} "

          return false if start_token_idx.nil?
          return false if processed_source.tokens.size < (start_token_idx + notice.size)

          notice_found = true
          notice.each_with_index do |n, idx|
            token = processed_source.tokens[start_token_idx + idx]
            notice_regexp = Regexp.new(n)
            notice_found = notice_found && !((token.text =~ notice_regexp).nil?)
            log += "#{notice_found} "
          end

          puts log if ConfigLoader.debug?
          notice_found
        end

        def notice_start(processed_source)
          notice_regexp = Regexp.new(notice[0])
          processed_source.tokens.each_with_index do |token, index|
            break unless token.type == :tCOMMENT
            notice_found = !((token.text =~ notice_regexp).nil?)
            return index if notice_found
          end

          nil
        end
      end
    end
  end
end
