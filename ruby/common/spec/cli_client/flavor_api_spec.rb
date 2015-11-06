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

describe EsxCloud::CliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) do
    EsxCloud::CliClient.new("/path/to/cli", "localhost:9000")
  end

  it "creates a flavor" do
    flavor = double(EsxCloud::Flavor)
    flavors = double(EsxCloud::FlavorList, :items => [flavor])

    costs = []
    costs << EsxCloud::QuotaLineItem.new("vm.memory", "1", "GB").to_hash
    spec = { :name => "f1",
             :kind => 'kind',
             :cost => costs
             }

    expect(client).to receive(:run_cli).with("flavor create -n 'f1' -k 'kind' -c 'vm.memory 1 GB'")
    expect(client).to receive(:find_flavors_by_name_kind).and_return(flavors)

    client.create_flavor(spec).should == flavor
  end

  it "finds flavor by id" do
    flavor = double(EsxCloud::Flavor)
    expect(@api_client).to receive(:find_flavor_by_id).with("foo").and_return(flavor)
    client.find_flavor_by_id("foo").should == flavor
  end

  it "finds all flavors" do
    flavors = double(EsxCloud::FlavorList)
    expect(@api_client).to receive(:find_all_flavors).and_return(flavors)
    client.find_all_flavors.should == flavors
  end

  it "finds flavors by name and kind" do
    flavors = double(EsxCloud::FlavorList)
    expect(@api_client).to receive(:find_flavors_by_name_kind).with("foo", "vm").and_return(flavors)
    client.find_flavors_by_name_kind("foo", "vm").should == flavors
  end

  it "deletes flavor by id" do
    flavor = double(EsxCloud::Flavor, :name => "f1", :entity_kind => "vm")

    expect(@api_client).to receive(:delete_flavor).with("foo").and_return(true)
    client.delete_flavor("foo").should be_true
  end

  it "deletes flavor by name and kind" do
    name = "flavor"
    kind = "vm"
    expect(client).to receive(:run_cli).with("flavor delete -n '#{name}' -k '#{kind}'").and_return(true)

    client.delete_flavor_by_name_kind(name, kind).should be_true
  end

end
