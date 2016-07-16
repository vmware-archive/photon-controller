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

require_relative "spec_helper_housekeeper"

describe "Image Cleaner", housekeeper: true, auth_disabled: true do
  let(:service_link) { "/photon/housekeeper/image-cleaners" }
  let(:client) { EsxCloud::Dcp::HouskeeperClient.instance }
  let(:task_completion_stages) { ["FINISHED", "FAILED", "CANCELLED"] }
  let(:task_start_stages) { ["CREATED", "STARTED"] }

  it "runs succesfully" do
    payload = {
      imageWatermarkTime: Time.now.to_i,
      imageDeleteWatermarkTime: Time.now.to_i
    }

    wait_for_existing_task
    task = run_task payload
    expect(task["taskInfo"]["stage"]).to eq "FINISHED"
    expect(task["dataStoreCount"]).to be >= 1
    expect(task["finishedDeletes"]).to eq task["dataStoreCount"]
    expect(task["failedOrCanceledDeletes"]).to eq 0
  end

  def wait_for_existing_task
    tasks = client.get service_link + "?expand"
    running_task_count = 0
    running_task = nil
    tasks["documents"].each do |document|
      task = document[1]
      if task_start_stages.include? task["taskInfo"]["stage"]
        running_task_count += 1
        running_task = task
      end
    end

    if !running_task.nil?
      expect(running_task_count).to eq(1)
      poll_task running_task
    else
      nil
    end
  end

  def run_task(payload)
    task = client.post service_link, payload
    poll_task task
  end

  def poll_task(task)
    task = client.poll_task(task["documentSelfLink"], 10, 200) do |b|
      task_completion_stages.include? b["taskInfo"]["stage"]
    end
    EsxCloud::Config.logger.debug "Image Cleaner Task: #{task.inspect}"
    task
  end
end
