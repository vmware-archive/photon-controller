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

  let(:http_client) { double(EsxCloud::HttpClient) }
  let(:client) { EsxCloud::ApiClient.new("localhost:9000") }
  let(:image) { double(EsxCloud::Image) }
  let(:images) { double(EsxCloud::ImageList) }
  let(:tasks) { double(EsxCloud::TaskList) }

  before(:each) do
    EsxCloud::HttpClient.stub(:new).and_return(http_client)
  end

  it "uploads an image" do
    expect(http_client).to receive(:upload)
                            .with("/images", "/image/path", "image1", {imageReplication: "EAGER"})
                            .and_return(task_created("aaa"))

    expect(http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "image-id"))
    expect(http_client).to receive(:get).with("/images/image-id").and_return(ok_response("image"))
    expect(EsxCloud::Image).to receive(:create_from_json).with("image").and_return(image)

    expect(client.create_image("/image/path", "image1", "EAGER")).to eq image
  end

  it "creates image from vm" do
    payload = {name: "image1", replicationType: "EAGER"}
    expect(http_client).to receive(:post_json)
                           .with("/vms/vm1/create_image", payload)
                           .and_return(task_created("aaa"))

    expect(http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "image-id"))
    expect(http_client).to receive(:get).with("/images/image-id").and_return(ok_response("image"))
    expect(EsxCloud::Image).to receive(:create_from_json).with("image").and_return(image)

    expect(client.create_image_from_vm("vm1", payload)).to eq image
  end

  it "finds image by id" do
    expect(http_client).to receive(:get).with("/images/foo").and_return(ok_response("image"))
    expect(EsxCloud::Image).to receive(:create_from_json).with("image").and_return(image)

    client.find_image_by_id("foo").should == image
  end

  it "finds all images" do
    expect(http_client).to receive(:get).with("/images").and_return(ok_response("images"))
    expect(EsxCloud::ImageList).to receive(:create_from_json).with("images").and_return(images)

    client.find_all_images.should == images
  end

  it "finds images by name" do
    expect(http_client).to receive(:get).with("/images?name=image1").and_return(ok_response("images"))
    expect(EsxCloud::ImageList).to receive(:create_from_json).with("images").and_return(images)

    client.find_images_by_name("image1").should == images
  end

  it "deletes an image" do
    expect(http_client).to receive(:delete).with("/images/foo").and_return(task_created("aaa"))
    expect(http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

    client.delete_image("foo").should be_true
  end

  it "gets Image tasks" do
    expect(http_client).to receive(:get).with("/images/foo/tasks").and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

    expect(client.get_image_tasks("foo")).to eq(tasks)
  end

  it "gets flavor tasks" do
    expect(http_client).to receive(:get).with("/flavors/foo/tasks").and_return(ok_response("tasks"))
    expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

    expect(client.get_flavor_tasks("foo")).to eq(tasks)
  end
end
