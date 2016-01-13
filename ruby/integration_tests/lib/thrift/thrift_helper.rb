# Copyright 2016 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

require 'thrift'

require 'test_helpers'

$:.unshift "#{File.dirname(__FILE__)}/../../thrift"

module Photon
  module ThriftHelper
    def self.get_protocol(service_name)
      get_protocol EsxCloud::TestHelpers.get_esx_ip, 8835, service_name
    end

    def self.get_protocol(esx_ip, agent_port, service_name)
      transport = Thrift::FramedTransport.new(Thrift::Socket.new(esx_ip, agent_port))
      protocol = Thrift::MultiplexedProtocol.new(Thrift::CompactProtocol.new(transport), service_name)
      transport.open
      protocol
    end
  end
end
