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

describe "vm power ops", management: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new(create_small_limits)
    @cleaner = EsxCloud::SystemCleaner.new(client)
    @default_network = @seeder.network!
    @vm = @seeder.vm!
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
    @cleaner.delete_network(@default_network)
  end

  it "should start, stop" do
    @vm.start!
    @vm.state.should == "STARTED"
    @vm.stop!
    @vm.state.should == "STOPPED"
  end

  it "should start, restart" do
    @vm.start!
    @vm.state.should == "STARTED"
    @vm.restart!
    @vm.state.should == "STARTED"
    @vm.stop!
    @vm.state.should == "STOPPED"
  end

  it "should suspend, resume" do
    @vm.start!
    @vm.state.should == "STARTED"
    @vm.suspend!
    @vm.state.should == "SUSPENDED"
    @vm.resume!
    @vm.state.should == "STARTED"
    @vm.stop!
    @vm.state.should == "STOPPED"
  end
end
