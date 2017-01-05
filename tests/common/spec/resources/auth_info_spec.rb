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

describe EsxCloud::AuthInfo do

  let(:auth_info) {
    $auth_info = EsxCloud::AuthInfo.new(true,
                                        "http://foo",
                                        "8080",
                                        "tenant",
                                        "username",
                                        "password",
                                        ["adminGroup1", "adminGroup2"]);
  }

  let(:auth_info_in_hash) {
    $auth_info_in_hash = {'enabled' => true,
                          'endpoint' => "http://foo",
                          'port' => "8080",
                          'tenant' => "tenant",
                          'username' => "username",
                          'password' => "password",
                          'securityGroups' => ["adminGroup1", "adminGroup2"]}
  }

  let(:auth_info_in_json) {
    $auth_info_in_json = '{ "enabled": true, "endpoint": "http://foo", "port": "8080", "tenant": "tenant", "username": "username",
                          "password": "password", "securityGroups": ["adminGroup1", "adminGroup2"]}'
  }

  it "can be retrieved from Config" do
    api_client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(api_client)

    auth_info = double(EsxCloud::AuthInfo)
    expect(api_client).to receive(:get_auth_info).and_return(auth_info)
    expect(EsxCloud::AuthInfo.get).to eq(auth_info)
  end

  it "can be created from valid JSON" do
    expect(auth_info).to eq(EsxCloud::AuthInfo.create_from_json(auth_info_in_json))
  end

  it "throws an unexpected format error when created from empty json" do
    expect { EsxCloud::AuthInfo.create_from_json("{}") }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "can be created from valid hash" do
    expect(auth_info).to eq(EsxCloud::AuthInfo.create_from_hash(auth_info_in_hash))
  end

  it "throws an unexpected format error when created from empty hash" do
    expect { EsxCloud::AuthInfo.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  it "can be hashed properly" do
    expect(auth_info.to_hash).to eq({:enabled=>true,
                                     :endpoint=>"http://foo",
                                     :port=>"8080",
                                     :tenant=>"tenant",
                                     :username=>"username",
                                     :password=>"password",
                                     :securityGroups=>["adminGroup1", "adminGroup2"]})
  end

  it "can be compared correctly" do
    auth_info_equal = EsxCloud::AuthInfo.new(true,
                                             "http://foo",
                                             "8080",
                                             "tenant",
                                             "username",
                                             "password",
                                             ["adminGroup1", "adminGroup2"])
    expect(auth_info).to eq(auth_info_equal)

    auth_info_not_equal = EsxCloud::AuthInfo.new(true,
                                                 "http://bar",
                                                 "8080",
                                                 "tenant",
                                                 "username",
                                                 "password",
                                                 ["adminGroup1", "adminGroup2"])
    expect(auth_info).not_to eq(auth_info_not_equal)
  end

  it "can read disabled auth info from json" do
    json = '{ "enabled": false}'
    auth_info_from_json = EsxCloud::AuthInfo.create_from_json(json)

    expect(auth_info_from_json.enabled).to eq(false)
    expect(auth_info_from_json.endpoint).to be_nil
    expect(auth_info_from_json.port).to be_nil
    expect(auth_info_from_json.tenant).to be_nil
    expect(auth_info_from_json.username).to be_nil
    expect(auth_info_from_json.password).to be_nil
    expect(auth_info_from_json.securityGroups).to be_nil
  end

end
