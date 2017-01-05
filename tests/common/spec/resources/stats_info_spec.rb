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

describe EsxCloud::StatsInfo do

  let(:stats_info) {
    $stats_info = EsxCloud::StatsInfo.new(true,
                                        "http://foo",
                                        "8080",
                                        "GRAPHITE");
  }

  let(:stats_info_in_hash) {
    $stats_info_in_hash = {'enabled' => true,
                          'storeEndpoint' => "http://foo",
                          'storePort' => "8080",
                          'storeType' => "GRAPHITE"}
  }

  let(:stats_info_in_json) {
    $stats_info_in_json = '{ "enabled": true, "storeEndpoint": "http://foo",'\
                          '"storePort": "8080", "storeType": "GRAPHITE"}'
  }

  it "can be retrieved from Config" do
    api_client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(api_client)

    stats_info = double(EsxCloud::StatsInfo)
    expect(api_client).to receive(:get_stats_info).and_return(stats_info)
    expect(EsxCloud::StatsInfo.get).to eq(stats_info)
  end

  it "can be created from valid JSON" do
    expect(stats_info).to eq(EsxCloud::StatsInfo.create_from_json(stats_info_in_json))
  end

  it "throws an unexpected format error when created from empty json" do
    expect { EsxCloud::StatsInfo.create_from_json("{}") }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "can be created from valid hash" do
    expect(stats_info).to eq(EsxCloud::StatsInfo.create_from_hash(stats_info_in_hash))
  end

  it "throws an unexpected format error when created from empty hash" do
    expect { EsxCloud::StatsInfo.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "can be hashed properly" do
    expect(stats_info.to_hash).to eq({:enabled=>true,
                                     :storeEndpoint=>"http://foo",
                                     :storePort=>"8080",
                                     :storeType=>"GRAPHITE"})
  end

  it "can be compared correctly" do
    stats_info_equal = EsxCloud::StatsInfo.new(true,
                                             "http://foo",
                                             "8080",
                                             "GRAPHITE")
    expect(stats_info).to eq(stats_info_equal)

    stats_info_not_equal = EsxCloud::StatsInfo.new(true,
                                                 "http://bar",
                                                 "8080",
                                                 "GRAPHITE")
    expect(stats_info).not_to eq(stats_info_not_equal)
  end

  it "can read disabled stats info from json" do
    json = '{ "enabled": false}'
    stats_info_from_json = EsxCloud::StatsInfo.create_from_json(json)

    expect(stats_info_from_json.enabled).to eq(false)
    expect(stats_info_from_json.storeEndpoint).to be_nil
    expect(stats_info_from_json.storePort).to be_nil
    expect(stats_info_from_json.storeType).to be_nil
  end

end
