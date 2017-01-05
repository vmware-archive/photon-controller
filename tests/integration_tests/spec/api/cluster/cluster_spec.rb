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

require "net/ssh"
require "spec_helper"

describe "cluster-service", management: true do
  it 'should return 404 HTTP Status Code for invalid Cluster Id' do
    def verify_not_found_status_code
      begin
        yield
      rescue EsxCloud:: ApiError => e
        e.response_code.should == 404
      rescue EsxCloud::CliError => e
        e.output.should match("not found")
      end
    end
    cluster_id = "invalid-cluster-id"
    verify_not_found_status_code { client.find_cluster_by_id(cluster_id) }
    verify_not_found_status_code { client.delete_cluster(cluster_id) }
    verify_not_found_status_code { client.get_cluster_vms(cluster_id) }
    verify_not_found_status_code { client.resize_cluster(cluster_id, 100) }
  end
end
