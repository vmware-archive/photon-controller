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

  let(:http_client) { double(EsxCloud::HttpClient) }
  let(:client) { EsxCloud::CliClient.new("/path/to/cli", "localhost:9000") }
  let(:api_client) { double(EsxCloud::ApiClient) }
  let(:image) { double(EsxCloud::Image, name: "def.ova") }
  let(:images) { double(EsxCloud::ImageList) }
  let(:tasks) { double(EsxCloud::TaskList) }

  before(:each) do
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end
  
  it "creates an image" do
    expect(client).to receive(:run_cli).with("image upload '/tmp/abc.ova' -n 'def.ova' -i 'EAGER'")
    expect(client).to receive(:find_all_images).and_return(EsxCloud::ImageList.new([image]))

    expect(client.create_image("/tmp/abc.ova", "def.ova", "EAGER")).to eq image
  end

  it "creates image from vm" do
    payload = {name: "def.ova", replicationType: "EAGER"}
    expect(client).to receive(:run_cli).with("vm create_image vm1 -n 'def.ova' -r 'EAGER'")
    expect(client).to receive(:find_all_images).and_return(EsxCloud::ImageList.new([image]))

    expect(client.create_image_from_vm("vm1", payload)).to eq image
  end

  it "finds image by id" do
    expect(api_client).to receive(:find_image_by_id).with("foo").and_return(image)
    client.find_image_by_id("foo").should == image
  end

  it "finds all images" do
    expect(api_client).to receive(:find_all_images).and_return(images)
    client.find_all_images.should == images
  end

  it "deletes an image" do
    expect(client).to receive(:run_cli).with("image delete 'foo'")

    client.delete_image("foo").should be_true
  end

  it "gets Image tasks" do
    expect(api_client).to receive(:get_image_tasks).with("foo", "bar").and_return(tasks)
    expect(client.get_image_tasks("foo", "bar")).to eq(tasks)
  end

  it "gets Flavor tasks" do
    expect(api_client).to receive(:get_flavor_tasks).with("foo", "bar").and_return(tasks)
    expect(client.get_flavor_tasks("foo", "bar")).to eq(tasks)
  end
end
