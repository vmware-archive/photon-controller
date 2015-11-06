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

describe EsxCloud::ResourceTicket do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "delegates create to API client" do
    spec = EsxCloud::ResourceTicketCreateSpec.new(
        "dev",
        [
            EsxCloud::QuotaLineItem.new("a", "b", "c"),
            EsxCloud::QuotaLineItem.new("d", "e", "f")
        ]
    )

    spec_hash = {
        :name => "dev",
        :limits => [
            {:key => "a", :value => "b", :unit => "c"},
            {:key => "d", :value => "e", :unit => "f"}
        ]
    }

    expect(@client).to receive(:create_resource_ticket).with("foo", spec_hash)

    EsxCloud::ResourceTicket.create("foo", spec)
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "rt_name",
        "tenantId" => "bar",
        "limits" => [
            {"key" => "a", "value" => "b", "unit" => "c"},
            {"key" => "d", "value" => "e", "unit" => "f"}
        ],
        "usage" => [
            {"key" => "a", "value" => "b", "unit" => "c"},
            {"key" => "d", "value" => "e", "unit" => "f"}
        ]
    }

    from_hash = EsxCloud::ResourceTicket.create_from_hash(hash)
    from_json = EsxCloud::ResourceTicket.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |ticket|
      ticket.id.should == "foo"
      ticket.tenant_id.should == "bar"
      ticket.name.should == "rt_name"
      ticket.limits[0].should == EsxCloud::QuotaLineItem.new("a", "b", "c")
      ticket.limits[1].should == EsxCloud::QuotaLineItem.new("d", "e", "f")
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::ResourceTicket.create_from_hash({"name" => "aa"}) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

end
