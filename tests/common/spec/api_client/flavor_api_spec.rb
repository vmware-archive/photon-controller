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

describe EsxCloud::ApiClient do

  before(:each) do
    @http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(@http_client)
  end

  let(:client) {
    EsxCloud::ApiClient.new("localhost:9000")
  }

  it "creates a flavor" do
    flavor = double(EsxCloud::Flavor)
    expect(@http_client).to receive(:post_json)
                            .with("/flavors", "payload")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "flavor-id"))
    expect(@http_client).to receive(:get).with("/flavors/flavor-id").and_return(ok_response("flavor"))
    expect(EsxCloud::Flavor).to receive(:create_from_json).with("flavor").and_return(flavor)
    client.create_flavor("payload").should == flavor
  end

  it "deletes a flavor" do
    expect(@http_client).to receive(:delete)
                            .with("/flavors/foo")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_flavor("foo").should be_true
  end

  it "deletes a flavor by name and kind" do
    flavors = double(EsxCloud::FlavorList, :items => [double(EsxCloud::Flavor, :id => "bar")])

    expect(@http_client).to receive(:get).with("/flavors?name=foo&kind=vm")
                            .and_return(ok_response("flavors"))
    expect(EsxCloud::FlavorList).to receive(:create_from_json).with("flavors").and_return(flavors)
    expect(@http_client).to receive(:delete)
                            .with("/flavors/bar")
                            .and_return(task_created("aaa"))

    expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa")
                            .and_return(task_done("aaa", :entity_id => "foo",
                                                  :entity_kind => "vm"))

    client.delete_flavor_by_name_kind("foo", "vm").should be_true
  end

  it "finds flavor by id" do
    flavor = double(EsxCloud::Flavor)

    expect(@http_client).to receive(:get).with("/flavors/foo").and_return(ok_response("flavor"))
    expect(EsxCloud::Flavor).to receive(:create_from_json).with("flavor").and_return(flavor)

    client.find_flavor_by_id("foo").should == flavor
  end

  it "finds all flavors" do
    flavors = double(EsxCloud::FlavorList)

    expect(@http_client).to receive(:get).with("/flavors").and_return(ok_response("flavors"))
    expect(EsxCloud::FlavorList).to receive(:create_from_json).with("flavors").and_return(flavors)

    client.find_all_flavors.should == flavors
  end

  it "finds flavors by name and kind" do
    flavors = double(EsxCloud::FlavorList)

    expect(@http_client).to receive(:get).with("/flavors?name=foo&kind=vm")
                            .and_return(ok_response("flavors"))
    expect(EsxCloud::FlavorList).to receive(:create_from_json).with("flavors").and_return(flavors)

    client.find_flavors_by_name_kind("foo", "vm").should == flavors
  end

  it "fails to delete a flavor by name and kind if it doesn't exist" do
    flavors = double(EsxCloud::FlavorList, :items => [])

    expect(@http_client).to receive(:get).with("/flavors?name=foo&kind=vm")
                            .and_return(ok_response("flavors"))
    expect(EsxCloud::FlavorList).to receive(:create_from_json).with("flavors").and_return(flavors)

    expect { client.delete_flavor_by_name_kind("foo", "vm") }.to raise_error(EsxCloud::NotFound)
  end

end
