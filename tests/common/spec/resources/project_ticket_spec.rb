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

describe EsxCloud::ProjectTicket do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "can be created from hash or from JSON" do
    hash = {
        "tenantTicketId" => "some-tenant-ticket-id",
        "tenantTicketName" => "some-tenant-ticket-name",
        "limits" => [
            {"key" => "a", "value" => "b", "unit" => "c"},
            {"key" => "d", "value" => "e", "unit" => "f"}
        ],
        "usage" => [
            {"key" => "a", "value" => "b2", "unit" => "c"},
            {"key" => "d", "value" => "e2", "unit" => "f"}
        ]
    }

    from_hash = EsxCloud::ProjectTicket.create_from_hash(hash)
    from_json = EsxCloud::ProjectTicket.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |ticket|
      ticket.limits[0].should == EsxCloud::QuotaLineItem.new("a", "b", "c")
      ticket.limits[1].should == EsxCloud::QuotaLineItem.new("d", "e", "f")
      ticket.usage[0].should == EsxCloud::QuotaLineItem.new("a", "b2", "c")
      ticket.usage[1].should == EsxCloud::QuotaLineItem.new("d", "e2", "f")
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::ProjectTicket.create_from_hash({}) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

end
