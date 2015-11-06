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

require_relative "../spec_helper"

describe EsxCloud::MksTicket do
  let(:client) {
    double(EsxCloud::ApiClient)
  }

  before(:each) do
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "can be created from hash or from JSON" do
    hash = {
        "host" => "192.168.1.100",
        "port" => "902",
        "cfg_file" => "/vmfs/volumes/21bd-4978-9b52-4fab839f7289.vmx",
        "ticket" => "5296c388-e530-4337-91ec-8599df715e84",
        "ssl_thumbprint" => "43:05:0F:F7:A6:46:E0:CA:51:0C:AC:27:73:D1:1E:09:49:8E:6B:E9"
    }
    from_hash = EsxCloud::MksTicket.create_from_hash(hash)
    from_json = EsxCloud::MksTicket.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |mks_ticket|
      mks_ticket.host.should == "192.168.1.100"
      mks_ticket.port.should == "902"
      mks_ticket.cfg_file.should == "/vmfs/volumes/21bd-4978-9b52-4fab839f7289.vmx"
      mks_ticket.ticket.should == "5296c388-e530-4337-91ec-8599df715e84"
      mks_ticket.ssl_thumbprint.should == "43:05:0F:F7:A6:46:E0:CA:51:0C:AC:27:73:D1:1E:09:49:8E:6B:E9"
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::MksTicket.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

end
