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

require_relative "api_client/auth_api"
require_relative "api_client/project_api"
require_relative "api_client/resource_ticket_api"
require_relative "api_client/tenant_api"
require_relative "api_client/vm_api"
require_relative "api_client/disk_api"
require_relative "api_client/host_api"
require_relative "api_client/network_api"
require_relative "api_client/storage_api"
require_relative "api_client/import_api"
require_relative "api_client/flavor_api"
require_relative "api_client/image_api"
require_relative "api_client/deployment_api"
require_relative "api_client/replica_override_api"
require_relative "api_client/config_api"
require_relative "api_client/status_api"
require_relative "api_client/task_api"
require_relative "api_client/cluster_api"
require_relative "api_client/availability_zone_api"
require_relative "api_client/available_api"
require_relative "api_client/virtual_network_api"

module EsxCloud
  class ApiClient
    include EsxCloud::ApiClient::AuthApi
    include EsxCloud::ApiClient::ProjectApi
    include EsxCloud::ApiClient::ResourceTicketApi
    include EsxCloud::ApiClient::TenantApi
    include EsxCloud::ApiClient::VmApi
    include EsxCloud::ApiClient::DiskApi
    include EsxCloud::ApiClient::NetworkApi
    include EsxCloud::ApiClient::VirtualNetworkApi
    include EsxCloud::ApiClient::StorageApi
    include EsxCloud::ApiClient::HostApi
    include EsxCloud::ApiClient::ImportApi
    include EsxCloud::ApiClient::FlavorApi
    include EsxCloud::ApiClient::ImageApi
    include EsxCloud::ApiClient::DeploymentApi
    include EsxCloud::ApiClient::ReplicaOverrideApi
    include EsxCloud::ApiClient::ConfigApi
    include EsxCloud::ApiClient::StatusApi
    include EsxCloud::ApiClient::TaskApi
    include EsxCloud::ApiClient::ClusterApi
    include EsxCloud::ApiClient::AvailabilityZoneApi
    include EsxCloud::ApiClient::AvailableApi

    attr_accessor :task_tracker, :http_client

    TASKS_URL_BASE = "/tasks"
    MAX_POLL_ERROR_COUNT = 10

    # @param [String] endpoint API endpoint (not including version)
    # @param [#task_progress, #task_done] task_tracker Task tracker
    def initialize(endpoint, task_tracker = nil, access_token = nil)
      @endpoint = endpoint.chomp("/")
      @http_client = HttpClient.new(endpoint, access_token)

      if task_tracker && !(task_tracker.respond_to?(:task_progress) && task_tracker.respond_to?(:task_done))
        raise ArgumentError, "Invalid task tracker, should respond to :task_progress and :task_done"
      end

      @task_tracker = task_tracker
    end

    def poll_response(response, polling_states = %w(queued started), timeout_s = 7200, poll_interval_s = 0.5)
      task = JSON.parse(response.body)

      task_url = task["selfLink"]
      task_url = "#{TASKS_URL_BASE}/#{task["id"]}" unless task_url

      poll_task(
          task_url,
          polling_states,
          timeout_s,
          poll_interval_s)
    end

    # @param [String] task_url
    # @param [#include?] polling_states States that mean that task needs to be polled further
    # @param [Numeric] timeout_s Task waiting timeout (in seconds)
    # @param [Numeric] poll_interval_s Interval between polls
    # @return [Task] Task model for a finished task
    def poll_task(task_url, polling_states = %w(queued started), timeout_s = 7200, poll_interval_s = 0.5)
      unless task_url
        err("Can't follow API response, no task URL present")
      end

      deadline = Time.now + timeout_s
      error_count = 0
      while true
        if Time.now > deadline
          err("Timed out while waiting for task #{task_url} to complete")
        end

        task_response = @http_client.get(task_url)
        if task_response.code != 200
          err("Non-OK response while trying to get task #{task_url}: #{task_response.code}") if error_count > MAX_POLL_ERROR_COUNT
          error_count +=1
          sleep([poll_interval_s, [deadline - Time.now, 0].max].min)
          next
        end

        error_count = 0

        task = Task.create_from_json(task_response.body)
        task.url = task_url

        if @task_tracker
          @task_tracker.task_progress(task)
        end

        task_state = task.state.to_s.downcase
        unless polling_states.include?(task_state)
          @task_tracker.task_done(task) if @task_tracker
          fail ApiError.create_from_task(task) if task_state == "error"
          return task
        end

        sleep([poll_interval_s, [deadline - Time.now, 0].max].min)
        # adjust poll_interval to avoid hammering the API server with too many
        # poll requests
        poll_interval_s = [poll_interval_s * 2, 2].min
      end
    end

    # @param [String] operation
    # @param [HttpResponse] response
    # @param [Array<Fixnum>] good_codes
    def check_response(operation, response, good_codes = [])
      return if Array(good_codes).include?(response.code)

      raise ApiError.create_from_http_error(operation, response)
    end

    # @param [String] id
    # @return [Hash]
    def find_task_by_id_as_json(id)
      response = @http_client.get("#{TASKS_URL_BASE}/#{id}")
      check_response("Get task by ID '#{id}'", response, 200)

      JSON.parse(response.body)
    end

    # @return [String]
    def find_task_context_by_id(id)
      response = @http_client.get("#{TASKS_URL_BASE}/#{id}/context")
      check_response("Get task context by ID '#{id}'", response, 200)
      response.body
    end

    # @return [String]
    def retry_task(id)
      response = @http_client.post("#{TASKS_URL_BASE}/#{id}/retry", nil)
      check_response("Retry Task '#{id}'", response, 201)
      response.body
    end

    # @return [String]
    def abort_task(id)
      response = @http_client.post("#{TASKS_URL_BASE}/#{id}/abort", nil)
      check_response("Abort Task '#{id}'", response, 200)
      response.body
    end

    private

    def err(message)
      raise EsxCloud::Error, message
    end

  end
end
