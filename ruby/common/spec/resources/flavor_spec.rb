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

require "json"

require_relative "../spec_helper"

describe EsxCloud::Flavor do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "delegates create to API client" do
    spec = EsxCloud::FlavorCreateSpec.new("f1", "kind", [EsxCloud::QuotaLineItem.new("vm.memory", "1", "GB")])
    expect(@client).to receive(:create_flavor).with(spec.to_hash)

    EsxCloud::Flavor.create(spec)
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::Flavor.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "can be created from JSON" do
    costs = []
    costs << EsxCloud::QuotaLineItem.new("vm.memory", "1", "GB").to_hash
    hash = { :id => "foo",
             :name => "f1",
             :kind => 'kind',
             :cost => costs,
             :state => 'READY'
             }

    puts hash.to_json
    from_json = EsxCloud::Flavor.create_from_json(hash.to_json)

    from_json.id.should == "foo"
    from_json.name.should == "f1"
    from_json.kind.should == "kind"
    from_json.state.should == "READY"
  end

  describe "upload flavor file" do
    before(:all) do
      @file = File.expand_path("../../test_files/flavors/ephemeral-disk.yml", File.dirname(__FILE__))
      @hash = YAML.load_file(@file)
      @call_create_flavor_count = @hash["flavors"].length

      costs = []
      costs << EsxCloud::QuotaLineItem.new("vm.memory", "1", "GB").to_hash
      hash = { :id => "foo",
               :name => "f1",
               :kind => 'kind',
               :cost => costs
      }
      from_json = EsxCloud::Flavor.create_from_json(hash.to_json)
      @find_flavor_response = EsxCloud::FlavorList.new([from_json])

      http_response = EsxCloud::HttpResponse.new(404, "foo bar", {})
      @error = EsxCloud::ApiError.create_from_http_error("some operation", http_response)
    end

    it "successfully uploads flavor files" do
        expect(@client).to receive(:create_flavor).exactly(@call_create_flavor_count).times.with(an_instance_of(Hash))
        expect(@client).to receive(:find_flavors_by_name_kind).with(an_instance_of(String), an_instance_of(String))
                           .exactly(@call_create_flavor_count).times.and_return(EsxCloud::FlavorList.new([]))
        EsxCloud::Flavor.upload_flavor(@file, false)
    end

    it "first deletes then creates flavor when force uploading flavor files" do
      expect(@client).to receive(:create_flavor).exactly(@call_create_flavor_count).times.with(an_instance_of(Hash))
      expect(@client).to receive(:find_flavors_by_name_kind).with(an_instance_of(String), an_instance_of(String))
                         .exactly(@call_create_flavor_count).times.and_return(@find_flavor_response)
      expect(@client).to receive(:delete_flavor).with(an_instance_of(String))
                         .exactly(@call_create_flavor_count).times
      EsxCloud::Flavor.upload_flavor(@file, true)
    end

    it "can't delete flavor in use when force uploads flavor files" do
      expect(@client).to receive(:find_flavors_by_name_kind).with(an_instance_of(String), an_instance_of(String))
                         .exactly(@call_create_flavor_count).times.and_return(@find_flavor_response)
      expect(@client).to receive(:delete_flavor).with(an_instance_of(String))
                         .exactly(@call_create_flavor_count).times.and_raise(@error)
      expect(@client).to receive(:create_flavor).exactly(0).times
      expect(@hash["flavors"]).to eq EsxCloud::Flavor.upload_flavor(@file, true)
    end

    it "skips uploading exsiting flavor when not force uploading flavor files" do
      expect(@client).to receive(:find_flavors_by_name_kind).with(an_instance_of(String), an_instance_of(String))
                         .exactly(@call_create_flavor_count).times.and_return(@find_flavor_response)
      expect(@client).to receive(:create_flavor).exactly(0).times
      expect(@hash["flavors"]).to eq EsxCloud::Flavor.upload_flavor(@file, false)
    end
  end

  it "delegates find_by_id to API client" do
    expect(@client).to receive(:find_flavor_by_id).with("foo")
    EsxCloud::Flavor.find_by_id("foo")
  end

  it "delegates find_by_name_kind to API client" do
    expect(@client).to receive(:find_flavors_by_name_kind).with("foo", "kind")
    EsxCloud::Flavor.find_by_name_kind("foo", "kind")
  end

  it "delegates find_all to API client" do
    expect(@client).to receive(:find_all_flavors)
    EsxCloud::Flavor.find_all
  end

  it "can be deleted" do
    costs = []
    costs << EsxCloud::QuotaLineItem.new("vm.memory", "1", "GB")
    expect(@client).to receive(:delete_flavor).with("foo")

    EsxCloud::Flavor.new("foo", "name", "kind", costs, "READY").delete
  end

end
