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
  class NetworkList

    attr_reader :items

    # @param [String] json
    # @return [NetworkList]
    def self.create_from_json(json)
      puts "Create from json"
      payload = JSON.parse(json)

      unless payload.is_a?(Hash) && payload["items"].is_a?(Enumerable)
        fail UnexpectedFormat, "Invalid network list: #{payload}"
      end

      puts "creating one by one"
      items = payload["items"].map do |item|
        puts "The item is"
        puts item.inspect
        tmp = Network.create_from_hash(item)
        puts "network is"
        puts tmp.inspect
        tmp
      end

      new(items)
    end

    # @param [Array<Network>] items
    def initialize(items)
      @items = items
    end

  end
end
