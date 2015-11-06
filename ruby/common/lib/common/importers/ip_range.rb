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

require 'netaddr'

module EsxCloud
  class IPRange

    # @param [String] string
    # @return [Array<String>]
    def self.parse(range)
      addresses = []

      return addresses if range.nil? || range.empty?

      range.split(",").each do |item|
        range_split = item.strip.split("-").map {|r| r.strip}
        if range_split.size == 1
          addresses << NetAddr::CIDR.create(range_split.first).ip
        else
          start_range = NetAddr::CIDR.create(range_split[0])
          end_range = NetAddr::CIDR.create(range_split[1])
          (start_range..end_range).map do |ip_entry|
            addresses << ip_entry.ip
          end
        end
      end

      addresses
    end
  end
end
