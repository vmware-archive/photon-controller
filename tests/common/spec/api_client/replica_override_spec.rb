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

  let(:client) do
    EsxCloud::ApiClient.new("localhost:9000")
  end

  it "creates replica override" do
    expect(@http_client).to receive(:post).with("/deployment/overrides/Zookeeper/replicas/5", nil).and_return(ok_response({}))
    client.set_replica_override("Zookeeper", 5)
  end

  it "raises exception when wrong job name is specified during create" do
    expect {
      client.set_replica_override("foo", 5)
    }.to raise_exception("Unknown job: foo")
  end

  it "lists current replica overrides" do
    overrides = {"Zookeeper" => "5"}
    expect(@http_client).to receive(:get).with("/deployment/overrides").and_return(ok_response(overrides.to_json))

    response = client.get_replica_overrides
    response.should == overrides
  end

  it "delete a replica override" do
    expect(@http_client).to receive(:delete).with("/deployment/overrides/Zookeeper/replicas").
                                and_return(EsxCloud::HttpResponse.new(204, {}, {}))
    client.delete_replica_override("Zookeeper")
  end

  it "raises exception when wrong job name is specified during delete" do
    expect {
      client.delete_replica_override("foo")
    }.to raise_exception("Unknown job: foo")
  end

end
