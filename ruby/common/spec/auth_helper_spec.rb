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
  before(:each) do
    @http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(@http_client)
  end

  ["foo@esxcloud", "esxcloud\\foo"].each do |username|
    it "gets access token #{username}" do
      password = "bar"
      service_locator_url = "http://blah"

      escaped_username = Shellwords.escape(Shellwords.escape(username))
      token = "@wesom3"
      http_response = EsxCloud::HttpResponse.new(200, "{\"access_token\":\"#{token}\"}", {})

      expect(@http_client).to receive(:post).with("/openidconnect/token",
                                                  "grant_type=password&username=#{escaped_username}&password=#{password}&scope=openid offline_access rs_esxcloud at_groups",
                                                  {"Content-Type"=>"application/x-www-form-urlencoded"}).and_return(http_response)

      returnedToken = EsxCloud::AuthHelper.get_access_token(escaped_username, password, service_locator_url)
      expect(returnedToken).to eq(token)
    end
  end

end
