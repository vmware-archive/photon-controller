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

require_relative "cloud_store_client"

module EsxCloud
  module Dcp
    module CloudStore
      class TaskFactory
        FACTORY_SERVICE_LINK = "/photon/cloudstore/tasks"

        def self.create_random_tasks(number, factory_link=FACTORY_SERVICE_LINK)
          factory = TaskFactory.new
          todo = Queue.new
          ts = (1..TaskFactory.parallel_thread_count).map{
            Thread.new{
              while todo.deq
                factory.create_random_task factory_link
              end
            }
          }
          number.times { todo << [] }
          # adding n nils to the queue to stop the thread
          TaskFactory.parallel_thread_count.times{ todo << nil }
          ts.each do |t|
            t.join
          end
        end

        def initialize()

        end

        def create_random_task(factory_link)
          payload = {
            state: "COMPLETED",
            entity_id: random_name("/does/not/exist/"),
            entity_kind: "fake"
          }
          CloudStoreClient.instance.post factory_link, payload
        end

        def self.parallel_thread_count
          ENV["CREATE_TASK_THREADS"] || 10
        end

      end
    end
  end
end
