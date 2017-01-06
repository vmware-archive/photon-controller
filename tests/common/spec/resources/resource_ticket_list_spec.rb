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

describe EsxCloud::ResourceTicketList do

  before(:each) do
  end

  it "can be created from JSON" do
    tickets = (1..3).map do |i|
      {
          "id" => "id-#{i}",
          "name" => "name-#{i}",
          "tenantId" => "foo",
          "limits" => [
              {"key" => "a", "value" => "b", "unit" => "c"},
              {"key" => "d", "value" => "e", "unit" => "f"}
          ],
          "usage" => [
              {"key" => "a", "value" => "b", "unit" => "c"},
              {"key" => "d", "value" => "e", "unit" => "f"}
          ]
      }
    end

    json = JSON.generate({"items" => tickets})
    list = EsxCloud::ResourceTicketList.create_from_json(json)

    list.items.each_with_index do |item, i|
      item.should == EsxCloud::ResourceTicket.create_from_hash(tickets[i])
    end
  end

  it "needs a proper payload format" do
    expect { EsxCloud::ResourceTicketList.create_from_json("[]") }.to raise_error(EsxCloud::UnexpectedFormat)
  end

end
