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

require_relative "spec_helper"

require "tempfile"

describe EsxCloud::HttpClient do

  before(:each) do
    @conn = double(Faraday, :request => nil, :adapter => nil)
    @up_conn = double(Faraday, :request => nil, :adapter => nil)
    @faraday_request = double(Faraday::Request)
    @faraday_response = double(Faraday::Response, :status => 200, :body => "foo", :headers => {"k2" => "v2"})
    @multipart_response = double(Faraday::Response, :status => 201, :body => "foo", :headers => {"k2" => "v2"})
    allow(Faraday).to receive(:new).twice.with(:url => "http://foo", :proxy => anything, :ssl => anything).and_return(@conn, @up_conn)

    EsxCloud::Config.stub(:debug?)
    EsxCloud::Config.stub(:logger).and_return(double("logger", :info => nil, :debug => nil, :warn => nil))

    @tempfile = Tempfile.new("temp")
  end

  after(:each) do
    @tempfile.delete
  end

  let(:client) {
    EsxCloud::HttpClient.new("http://foo")
  }

  it "fails if no endpoint given" do
    expect { EsxCloud::HttpClient.new(nil) }.to raise_error(ArgumentError)
  end

  it "properly initializes Faraday" do
    expect(Faraday).to receive(:new).twice.with(:url => "http://foo", :proxy => anything, :ssl => nil).and_yield(@conn).and_return(@conn)

    expect(@conn).to receive(:request).with(:url_encoded)
    expect(@conn).to receive(:adapter).with(Faraday.default_adapter)

    client
  end

  it "honors http_proxy env variable" do
    expect(Faraday).to receive(:new).twice.with(:url => "http://test.vmware.com", :proxy => "foo.bar", :ssl => nil)

    with_env("http_proxy" => "foo.bar") do
      EsxCloud::HttpClient.new("http://test.vmware.com")
    end
  end

  it "honors no_proxy env variable" do
    expect(Faraday).to receive(:new).twice.with(:url => "http://test.vmware.com", :proxy => nil, :ssl => nil)

    with_env("http_proxy" => "foo.bar", "no_proxy" => ".vmware.com, 127.0.0.1 , localhost,foo.bar") do
      EsxCloud::HttpClient.new("http://test.vmware.com")
    end
  end

  it "performs POST with JSON payload" do
    expect(@conn).to receive(:post).and_yield(@faraday_request).and_return(@faraday_response)
    expect(@faraday_request).to receive(:url).with("/path")
    expect(@faraday_request).to receive(:headers=)
                                .with("k1" => "v1",
                                      "Content-Type" => "application/json",
                                      "Accept" => "application/json")
    expect(@faraday_request).to receive(:body=).with(JSON.generate("foo" => "bar"))

    response = client.post_json("/path", {"foo" => "bar"}, {"k1" => "v1"})
    response.should be_a(EsxCloud::HttpResponse)
    response.code.should == 200
    response.body.should == "foo"
    response.get_header("k2").should == "v2"
  end

  it "performs POST to upload file" do
    additional_payload = {"a1" => "b1", "a2" => "b2"}
    expect(@up_conn).to receive(:post).and_yield(@faraday_request).and_return(@multipart_response)
    expect(@faraday_request).to receive(:url).with("/path")
    expect(@faraday_request).to receive(:headers=)
                                .with("k1" => "v1", "Content-Type" => "multipart/form-data")

    expect(@faraday_request).to receive(:body=) do |hash|
      upload = hash[:file]

      upload.should_not be_nil
      upload.should be_a(Faraday::UploadIO)
      upload.content_type.should == "application/octet-stream"
      upload.local_path.should == @tempfile.path
      upload.original_filename.should == "a.vmdk"

      hash["a1"].should == "b1"
      hash["a2"].should == "b2"

    end

    response = client.upload("/path", @tempfile.path, "a.vmdk", additional_payload, {"k1" => "v1"})
    response.should be_a(EsxCloud::HttpResponse)
    response.code.should == 201
    response.body.should == "foo"
    response.get_header("k2").should == "v2"
  end

  it "performs POST to upload file with default name" do
    expect(@up_conn).to receive(:post).and_yield(@faraday_request).and_return(@multipart_response)
    expect(@faraday_request).to receive(:url).with("/path")
    expect(@faraday_request).to receive(:headers=).with("Content-Type" => "multipart/form-data")

    expect(@faraday_request).to receive(:body=) do |hash|
      upload = hash[:file]

      upload.should_not be_nil
      upload.should be_a(Faraday::UploadIO)
      upload.content_type.should == "application/octet-stream"
      upload.local_path.should == @tempfile.path
      upload.original_filename.should == File.basename(@tempfile.path)
    end

    response = client.upload("/path", @tempfile.path)
    response.should be_a(EsxCloud::HttpResponse)
    response.code.should == 201
    response.body.should == "foo"
    response.get_header("k2").should == "v2"
  end

  it "performs GET" do
    expect(@conn).to receive(:get).and_yield(@faraday_request).and_return(@faraday_response)
    expect(@faraday_request).to receive(:url).with("/path")
    expect(@faraday_request).to receive(:headers=).
                                    with("k1" => "v1",
                                         "Content-Type" => "application/json",
                                         "Accept" => "application/json")
    expect(@faraday_request).to receive(:params=).with("foo" => "bar")


    response = client.get("/path", {"foo" => "bar"}, {"k1" => "v1"})
    response.should be_a(EsxCloud::HttpResponse)
    response.code.should == 200
    response.body.should == "foo"
    response.get_header("k2").should == "v2"
  end

  it "get all through pagination" do
    first_page = {
        "items" => ["item1", "item2", JSON.parse("{\"id\": \"1234\"}")],
        "nextPageLink" => "/next-page",
        "previousPageLink" => nil,
    }
    first_page_response = double(Faraday::Response,
                                 :status => 200,
                                 :body => first_page.to_json,
                                 :headers => {"k2" => "v2"})

    first_page_request = double
    expect(@conn).to receive(:get).and_yield(first_page_request).and_return(first_page_response)
    expect(first_page_request).to receive(:url).with("/path")
    expect(first_page_request).to receive(:headers=)
                                      .with("k1" => "v1",
                                            "Content-Type" => "application/json",
                                            "Accept" => "application/json")
    expect(first_page_request).to receive(:params=).with("foo" => "bar")

    second_page = {
        "items" => ["item2", JSON.parse("{\"id\": \"1234\"}"), "item3", JSON.parse("{\"id\": \"2345\"}")],
        "nextPageLink" => nil,
        "previousPageLink" => nil,
    }
    second_page_response = double(Faraday::Response,
                                 :status => 200,
                                 :body => second_page.to_json,
                                 :headers => {"k2" => "v2"})

    second_page_request = double
    expect(@conn).to receive(:get).and_yield(second_page_request).and_return(second_page_response)
    expect(second_page_request).to receive(:url).with("/next-page")
    expect(second_page_request).to receive(:headers=)
                                     .with("k1" => "v1",
                                           "Content-Type" => "application/json",
                                           "Accept"=>"application/json")


    response = client.getAll("/path", {"foo" => "bar"}, {"k1" => "v1"})

    expect(response).to be_a(EsxCloud::HttpResponse)
    expect(response.code).to be(200)
    expect(JSON.parse(response.body)).to eq({
                                                "items" => [
                                                    "item1",
                                                    "item2",
                                                    { "id" => "1234" },
                                                    "item3",
                                                    { "id" => "2345" }
                                                ],
                                                "nextPageLink" => nil,
                                                "previousPageLink" => nil,
                                            })
    expect(response.get_header("k2")).to eq("v2")
  end

  it "performs DELETE" do
    expect(@conn).to receive(:delete).and_yield(@faraday_request).and_return(@faraday_response)
    expect(@faraday_request).to receive(:url).with("/path")
    expect(@faraday_request).to receive(:headers=)
                                .with("k1" => "v1",
                                      "Content-Type" => "application/json",
                                      "Accept" => "application/json")
    expect(@faraday_request).to receive(:params=).with("foo" => "bar")

    response = client.delete("/path", {"foo" => "bar"}, {"k1" => "v1"})
    response.should be_a(EsxCloud::HttpResponse)
    response.code.should == 200
    response.body.should == "foo"
    response.get_header("k2").should == "v2"
  end

  it "checks Authorization header presence" do
    access_token = "aaa"
    expect(@conn).to receive(:get).and_yield(@faraday_request).and_return(@faraday_response)
    expect(@faraday_request).to receive(:headers=).
                                    with("Authorization" => "Bearer " + access_token,
                                         "Content-Type" => "application/json",
                                         "Accept" => "application/json")
    expect(@faraday_request).to receive(:url).with("/path")
    httpClient = EsxCloud::HttpClient.new("http://foo", access_token)
    response = httpClient.get("/path")
    response.should be_a(EsxCloud::HttpResponse)
  end
end
