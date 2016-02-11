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

describe EsxCloud::AuthHelper do

  ["foo@esxcloud", "esxcloud\\foo"].each do |username|
    it "gets access token #{username}" do
      password = "bar"
      service_locator_url = "127.0.0.1"
      token = "@wesom3"
      path = "/openidconnect/token"
      data = "username=#{username}&password=#{password}&grant_type=password&scope=openid offline_access id_groups at_groups rs_admin_server"

      https =  double()
      expect(https).to receive(:use_ssl=).with(true)
      expect(https).to receive(:verify_mode=).with(OpenSSL::SSL::VERIFY_NONE)
      expect(Net::HTTP).to receive(:new).with(service_locator_url, 443).and_return(https)
      request = double()
      expect(request).to receive(:body).and_return({"access_token" => token}.to_json)
      expect(https).to receive(:post).with(path, data).and_return(request)

      returnedToken = EsxCloud::AuthHelper.get_access_token(username, password, service_locator_url)
      expect(returnedToken).to eq(token)
    end
  end

  it "fails if tenant is not part of user name" do
    username = "foo"
    password = "bar"
    service_locator_url = "127.0,0.1"

    expect {
      EsxCloud::AuthHelper.get_access_token(username, password, service_locator_url)
    }.to raise_error(EsxCloud::Error)
  end
end
