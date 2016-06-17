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

  let(:status_ready_hash) do
    {
      "components" => [
        {
          "component" => "PHOTON_CONTROLLER",
          "status" => "READY",
          "instances" => [
            {
              "address" => "/172.31.253.66=>19000",
              "status" => "READY"
            }
          ],
          "stats" => {
            "READY" => "1"
          }
        },
        {
          "component" => "CHAIRMAN",
          "status" => "READY",
          "instances" => [
            {
              "address" => "/172.31.253.66=>13000",
              "status" => "READY"
            }
          ],
          "stats" => {
            "READY" => "1"
          }
        }
      ],
      "status" => "READY"
    }
  end

  let(:status_ready_response) do
    EsxCloud::HttpResponse.new(200, JSON.generate(status_ready_hash), {})
  end

  it "shows system status" do
    expect(@http_client).to receive(:get).with("/status").and_return(status_ready_response)
    system_status = client.get_status
    system_status.status.should == "READY"
    system_status.components.size.should == 2

    system_status.components.each do |component|
      component.name.should_not be_nil
      component.status.should == "READY"
    end
  end
end
