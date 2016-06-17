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

describe EsxCloud::Status do

  let(:client) { double(EsxCloud::ApiClient) }
  let(:deployment) { double(EsxCloud::Deployment) }

  before(:each) do
    allow(EsxCloud::Config).to receive(:client).and_return(client)
  end

  let(:status_json_string) do
<<CONTENT
{
    "components": [
      {
         "component": "PHOTON_CONTROLLER",
         "status": "READY",
         "instances": [
            {
               "address": "/172.31.253.66:19000",
               "status": "READY"
            }
         ],
         "stats": {
            "READY": "1"
         }
      },
      {
         "component": "CHAIRMAN",
         "status": "READY",
         "instances": [
            {
               "address": "/172.31.253.66:13000",
               "status": "READY"
            }
         ],
         "stats": {
            "READY": "1"
         }
      }
   ],
   "status": "READY"
}
CONTENT
  end

  let(:status) { EsxCloud::Status.create_from_json(status_json_string) }

  describe "#create_from_json" do

    it "should initialize Status object properly" do
      expect(status.status).to eq "READY"
      expect(status.components.size).to eq 2
      status.components.each do |component|
        expect(component.status).to eq "READY"
        expect(component.stats).to eq({ "READY" => "1"})
        expect(component.instances.size).to eq 1
        expect(component.instances.first.status).to eq "READY"
        expect(component.instances.first.address).to match /^\/172\.31\.253\.66:/
      end
    end
  end

  describe "#to_s" do

    it "should return string properly" do
      expect(status.to_s).to eq "status: READY\ncomponents:\n" +
                                  "PHOTON_CONTROLLER: READY, message: , stats: {\"READY\"=>\"1\"}\ninstances:\n" +
                                  "/172.31.253.66:19000: READY, message: , stats: \n" +
                                  "CHAIRMAN: READY, message: , stats: {\"READY\"=>\"1\"}\ninstances:\n" +
                                  "/172.31.253.66:13000: READY, message: , stats: "
    end

  end

end
