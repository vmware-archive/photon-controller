# Copyright 2016 VMware, Inc. All Rights Reserved.
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

describe EsxCloud::GoCliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    cmd = "/path/to/cli target set --nocertcheck localhost:9000"
    expect(EsxCloud::CmdRunner).to receive(:run).with(cmd)
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  }

  it "creates an image" do
    image_id = double("foo")
    image = double(EsxCloud::Image, :id => image_id)
    expect(client).to receive(:run_cli)
                      .with("image create '/tmp/abc.ova' -n 'def.ova' -i 'EAGER'")
                      .and_return(image_id)
    expect(client).to receive(:find_image_by_id).with(image_id).and_return(image)

    expect(client.create_image("/tmp/abc.ova", "def.ova", "EAGER")).to eq image
  end

  it "finds image by id" do
    image_id = double("foo")
    image_hash ={ "id" => image_id,
                  "name" => "image1",
                  "state" => "READY",
                  "size" => 4194417,
                  "replicationType"=>"EAGER",
                  "replicationProgress"=>"100.0%",
                  "seedingProgress"=>"100.0%",
                  "settings"=>[{"name"=>"scsi0.virtualDev",
                                "defaultValue"=>"lsilogic"}]
                }
    image = EsxCloud::Image.create_from_hash(image_hash)
    result = "foo	image1	READY	4194417	EAGER	100.0%	100.0%  scsi0.virtualDev:lsilogic"
    expect(client).to receive(:run_cli).with("image show #{image_id}").and_return(result)
    expect(client).to receive(:get_image_from_response).with(result).and_return(image)

    client.find_image_by_id(image_id).should == image
  end

  it "finds all images" do
    images = double(EsxCloud::ImageList)
    result = "foo1  image1  READY 4194417 EAGER 100.0%  100.0%
              foo2  image2  READY 4194417 EAGER 100.0%  100.0%"
    expect(client).to receive(:run_cli).with("image list").and_return(result)
    expect(client).to receive(:get_image_list_from_response).with(result).and_return(images)

    client.find_all_images.should == images
  end

  it "finds images by name" do
    images = double(EsxCloud::ImageList)
    result = "foo1  image1  READY 4194417 EAGER 100.0%  100.0%
              foo2  image1  READY 4194417 EAGER 100.0%  100.0%"
    expect(client).to receive(:run_cli).with("image list -n 'image1'").and_return(ok_response(result))
    expect(EsxCloud::ImageList).to receive(:create_from_json).with("images").and_return(images)

    client.find_images_by_name("image1").should == images
  end

  it "deletes an image" do
    image_id = double("bar")
    expect(client).to receive(:run_cli).with("image delete '#{image_id}'")

    client.delete_image(image_id).should be_true
  end

  it "gets Image tasks" do
    image_id = double("bar")
    result = "task1 COMPLETED CREATE_IMAGE  1458853080000  1000
              task2 COMPLETED DELETE_IMAGE  1458853089000  1000"
    tasks = double(EsxCloud::TaskList)
    expect(client).to receive(:run_cli).with("image tasks '#{image_id}' -s 'COMPLETED'").and_return(result)
    expect(client).to receive(:get_task_list_from_response).with(result).and_return(tasks)
    client.get_image_tasks(image_id, "COMPLETED").should == tasks
  end
end
