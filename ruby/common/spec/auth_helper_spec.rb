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
      service_locator_url = "http://blah"
      auth_tool_path = "path_to_auth_tool"

      escaped_username = Shellwords.escape(Shellwords.escape(username))
      cmd = "-jar #{auth_tool_path} get-access-token -t esxcloud -a #{service_locator_url} -u #{escaped_username} -p #{password}"
      token = "@wesom3"

      expect(File).to receive(:exists?).with(auth_tool_path).and_return(true)
      expect(EsxCloud::CmdRunner).to receive(:run).with(/#{cmd}/, true, /.*Exception:.*/).and_return(token)

      returnedToken = EsxCloud::AuthHelper.get_access_token(username, password, service_locator_url, auth_tool_path)
      expect(returnedToken).to eq(token)
    end
  end

  it "fails if auth tool is not found" do
    username = "foo"
    password = "bar"
    service_locator_url = "http://blah"
    auth_tool_path = "path_to_auth_tool"

    expect {
      EsxCloud::AuthHelper.get_access_token(username, password, service_locator_url, auth_tool_path)
    }.to raise_error(EsxCloud::Error)
  end

  it "fails if tenant is not part of user name" do
    username = "foo"
    password = "bar"
    service_locator_url = "http://blah"
    auth_tool_path = "path_to_auth_tool"

    expect {
      EsxCloud::AuthHelper.get_access_token(username, password, service_locator_url, auth_tool_path)
    }.to raise_error(EsxCloud::Error)
  end
end
