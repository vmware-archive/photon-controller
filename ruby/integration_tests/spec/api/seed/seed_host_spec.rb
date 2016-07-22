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

require "spec_helper"

describe "Seed host", seed_host: true do

  it "Creates a host from environement variables" do
    EsxCloud::Config.init
    EsxCloud::Config.client = ApiClientHelper.management

    allowed_datastores = EsxCloud::TestHelpers.get_datastore_name
    fail "No datastore defined for VMs in ESX_DATASTORE" if allowed_datastores.nil? || allowed_datastores.empty?

    deployments = EsxCloud::Deployment.find_all.items
    fail "Unexpected deployment list #{deployments.inspect}" unless deployments.size == 1

    metadata = {
        "ALLOWED_DATASTORES" => allowed_datastores
    }

    spec = EsxCloud::HostCreateSpec.new(
        EsxCloud::TestHelpers.get_esx_username,
        EsxCloud::TestHelpers.get_esx_password,
        ["CLOUD"],
        EsxCloud::TestHelpers.get_esx_ip,
        metadata
    )
    host = EsxCloud::Host.create deployments.first.id, spec
    puts host
  end
end
