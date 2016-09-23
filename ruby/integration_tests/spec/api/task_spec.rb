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

describe "task", management: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new(create_small_limits, [5.0])
    @cleaner = EsxCloud::SystemCleaner.new(client)
  end

  after(:all) do
    @cleaner.delete_tenant(@seeder.tenant)
  end

  let(:tenant) { @seeder.tenant! }

  describe "#find_task_by_id" do
    it "finds one by id" do
      tasks = client.get_tenant_tasks(tenant.id)
      expect(tasks).to_not be_nil
      expect(tasks.items.size).to eq 1

      resTask = EsxCloud::Task.find_task_by_id(tasks.items[0].id)
      # Do to some oddities in conversion of Dates from Java to Ruby
      # hashes, sometimes they get rounded. Instead of checking that
      # the task is equal, we check the attributes
      expect(resTask.state).to eq tasks.items[0].state
      expect(resTask.operation).to eq tasks.items[0].operation
      expect((resTask.start_time - tasks.items[0].start_time).abs()).to be <= 1000
      expect((resTask.end_time - tasks.items[0].end_time).abs()).to be <= 1000
    end
  end

  describe "#find_tasks" do
    ["tenant", "Tenant", "TENANT"].each do |kind|
      it "finds all tasks for the entity with kind #{kind}" do
        tasks = EsxCloud::Task.find_tasks tenant.id, kind

        expect(tasks.items.size).to eq 1
        expect(tasks.items[0]).to be_a EsxCloud::Task
        expect(tasks.items[0].operation).to eq "CREATE_TENANT"
      end
    end

    ["completed", "comPleted", "COMPLETED"].each do |state|
      it "finds all tasks for the entity with kind 'tenant' and state #{state}" do
        tasks = EsxCloud::Task.find_tasks tenant.id, "tenant", state

        expect(tasks.items.size).to eq 1
        expect(tasks.items[0]).to be_a EsxCloud::Task
        expect(tasks.items[0].operation).to eq "CREATE_TENANT"
      end
    end

    xit "finds all tasks in 'COMPLETED' state" do
      tenant
      tasks = EsxCloud::Task.find_tasks nil, nil, "COMPLETED"

      expect(tasks.items.size).to eq 1
      expect(tasks.items[0]).to be_a EsxCloud::Task
      expect(tasks.items[0].operation).to eq "CREATE_TENANT"
    end

    xit "finds all tasks in the system" do
      tenant
      tasks = EsxCloud::Task.find_tasks

      expect(tasks.items.size).to eq 1
      expect(tasks.items[0]).to be_a EsxCloud::Task
      expect(tasks.items[0].operation).to eq "CREATE_TENANT"
    end

    it "fails when only entity id is provided" do
      begin
        EsxCloud::Task.find_tasks tenant.id
        fail("should fail when only entity id is provided")
      rescue EsxCloud::ApiError => e
        e.response_code.should == 400
        e.errors.size.should == 1
        e.errors[0].code.should include("InvalidQueryParams")
      rescue EsxCloud::CliError => e
        e.output.should match("InvalidQueryParams")
      end
    end

    it "fails when only kind is provided" do
      begin
        tenant
        EsxCloud::Task.find_tasks nil, "tenant"
        fail("should fail when only entity id is provided")
      rescue EsxCloud::ApiError => e
        e.response_code.should == 400
        e.errors.size.should == 1
        e.errors[0].code.should include("InvalidQueryParams")
      rescue EsxCloud::CliError => e
        e.output.should match("InvalidQueryParams")
      end
    end
  end
end
