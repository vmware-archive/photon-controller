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
  class MksTicket

    attr_accessor :host, :port, :cfg_file, :ticket, :ssl_thumbprint

    # @param [String] json
    # @return [MksTicket]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(host port cfg_file ticket ssl_thumbprint).to_set)
        fail UnexpectedFormat, "Invalid MksTicket hash: #{hash}"
      end

      new(hash["host"], hash["port"], hash["cfg_file"], hash["ticket"], hash["ssl_thumbprint"])
    end

    # @param [String] id
    # @param [Integer] port
    # @param [String] cfg_file
    # @param [String] ticket
    # @param [String] ssl_thumbprint
    def initialize(host, port, cfg_file, ticket, ssl_thumbprint)
      @host = host
      @port = port
      @cfg_file = cfg_file
      @ticket = ticket
      @ssl_thumbprint = ssl_thumbprint
    end

    def to_hash
      {
          :host => @host,
          :port => @port,
          :cfg_file => @cfg_file,
          :ticket => @ticket,
          :ssl_thumbprint => @ssl_thumbprint
      }
    end

    def ==(other)
      @host == other.host && @port == other.port && @cfg_file == other.cfg_file &&
          @ticket == other.ticket && @ssl_thumbprint == other.ssl_thumbprint
    end

  end
end
