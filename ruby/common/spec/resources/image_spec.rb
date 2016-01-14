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

describe EsxCloud::Image do

  let(:client) { double(EsxCloud::ApiClient) }
  let(:image) { double(EsxCloud::Image) }

  before(:each) do
    allow(EsxCloud::Config).to receive(:client).and_return(client)
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "i_name",
        "state" => "CREATED",
        "size" => 1234,
        "replicationType" => "EAGER",
        "replicationProgress" => "50%",
        "seedingProgress" => "40%",
        "settings" => [
            {
                "name" => 'property-name1',
                "defaultValue" => 'property-value1'
            },
            {
                "name" => 'property-name2',
                "defaultValue" => 'property-value2'
            }
        ]
    }
    from_hash = EsxCloud::Image.create_from_hash(hash)
    from_json = EsxCloud::Image.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |image|
      image.id.should == "foo"
      image.name.should == "i_name"
      image.state.should == "CREATED"
      image.size.should == 1234
      image.replication.should == "EAGER"
      image.replication_progress.should == "50%"
      image.seeding_progress.should == "40%"
      image.settings.size.should == 2
      image.settings[0]['name'].should == 'property-name1'
      image.settings[0]['defaultValue'].should == 'property-value1'
      image.settings[1]['name'].should == 'property-name2'
      image.settings[1]['defaultValue'].should == 'property-value2'
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::Image.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "delegates create_from_vm to client" do
    spec = EsxCloud::ImageCreateSpec.new("name", "ON_DEMAND")
    expect(client).to receive(:create_image_from_vm).with("vm-id", spec.to_hash).and_return(image)
    expect(EsxCloud::Image.create_from_vm("vm-id", spec)).to eq image
  end
end
