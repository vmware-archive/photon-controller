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

  let(:key) {
    "key1"
  }

  let(:value) {
    "value1"
  }

  let(:pair) {
    {
        "key" => key,
        "value" => value
    }
  }

  it "shows a config key-value pair" do
    expect(@http_client).to receive(:get).with("/config/#{key}").and_return(ok_response(pair.to_json))
    client.find_config_by_key(key).should == pair
  end

  it "deletes a config key-value pair" do
    expect(@http_client).to receive(:delete).with("/config/#{key}").and_return(ok_response(nil))
    client.delete_config_by_key(key)
  end

  it "sets a config key-value pair" do
    expect(@http_client).to receive(:post).with("/config/#{key}/#{value}", nil).and_return(ok_response(pair.to_json))
    client.set_config_pair(key, value)
  end
end
