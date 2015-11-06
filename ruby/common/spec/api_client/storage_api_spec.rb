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
  let(:storage_id){
    "storage_id"
  }
  let(:storage_spec) {
      {
        "id" => "id0",
        "username" => "aaa"
      }
  }

  let(:metadata) {
    {
        "aa" => "bb",
        "cc" => "dd"
    }
  }

  it "creates a storage" do

    expect(@http_client).to receive(:post_json)
                            .with("/resources/storage", "payload").and_return(ok_response(storage_spec.to_json, 201))
    client.create_storage("payload").should == storage_spec
  end

  it "shows a storage" do

    expect(@http_client).to receive(:get).with("/resources/storage/#{storage_id}").and_return(ok_response(storage_spec.to_json))
    client.find_storage_by_id(storage_id).should == storage_spec
  end

  it "show storage metadata" do

    expect(@http_client).to receive(:get).with("/resources/storage/#{storage_id}/metadata").and_return(ok_response(metadata.to_json))
    client.find_storage_metadata_by_id(storage_id).should == metadata
  end

  it "update storage metadata" do
    property = "property0"
    value = "value0"
    expect(@http_client).to receive(:put_json).with("/resources/storage/#{storage_id}/metadata/#{property}/#{value}", {}).and_return(ok_response(storage_spec.to_json))
    client.update_storage_metadata_property(storage_id, property, value).should == storage_spec
  end

  it "update storage property" do
    property = "property0"
    value = "value0"
    expect(@http_client).to receive(:put_json).with("/resources/storage/#{storage_id}/#{property}/#{value}", {}).and_return(ok_response(storage_spec.to_json))
    client.update_storage_property(storage_id, property, value).should == storage_spec
  end

  it "list storage" do
    expect(@http_client).to receive(:get).with("/resources/storage").and_return(ok_response(storage_spec.to_json))
    client.find_all_storages.should == storage_spec
  end

end
