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

module EsxCloud
  class HttpClient
    DEFAULT_CONTENT_TYPE = "application/json"

    DEFAULT_UPLOAD_CONTENT_TYPE = "application/octet-stream"

    MULTIPART_CONTENT_TYPE = "multipart/form-data"

    # @param [String] endpoint
    def initialize(endpoint, access_token = nil)
      if endpoint.nil?
        raise ArgumentError, "Endpoint must be specified"
      end

      fail "endpoint must specify the protocol, i.e., https or http" unless endpoint.start_with?("https") || endpoint.start_with?("http")

      @endpoint = endpoint.chomp("/")
      puts @endpoint
      proxy = ENV["http_proxy"] || ENV["HTTP_PROXY"]
      proxy = nil if host_matches_no_proxy?

      ssl_options = nil
      if @endpoint.start_with?("https")
        ssl_options = {:verify => false}
      end

      @conn = Faraday.new(:url => @endpoint, :proxy => proxy, :ssl => ssl_options) do |faraday|
        faraday.request(:url_encoded)
        faraday.adapter(Faraday.default_adapter)
      end

      @up_conn = Faraday.new(:url => @endpoint, :proxy => proxy, :ssl => ssl_options) do |faraday|
        faraday.request(:url_encoded)
        faraday.request(:multipart)
        faraday.adapter(Faraday.default_adapter)
      end

      @access_token = access_token
    end

    # @param [String] path
    # @param [Hash] payload
    # @param [Hash] headers
    # @return [HttpResponse] API HTTP response
    def post_json(path, payload, headers = {})
      Config.logger.debug("POST \"#{path}\" #{payload}")

      response = @conn.post do |req|
        set_path_and_headers(req, path, headers)
        if payload
          req.body = JSON.generate(payload)
        end
      end

      log_response(response)
      HttpResponse.new(response.status, response.body, response.headers)
    end

    # @param [String] path
    # @param [Hash] payload
    # @param [Hash] headers
    # @return [HttpResponse] API HTTP response
    def put_json(path, payload, headers = {})
      Config.logger.debug("PUT \"#{path}\" #{payload}")

      response = @conn.put do |req|
        set_path_and_headers(req, path, headers)
        if payload
          req.body = JSON.generate(payload)
        end
      end

      log_response(response)
      HttpResponse.new(response.status, response.body, response.headers)
    end

    # @param [String] path
    # @param [Hash] payload
    # @param [Hash] headers
    # @return [HttpResponse] API HTTP response
    def patch_json(path, payload, headers = {})
      Config.logger.debug("PATCH \"#{path}\" #{payload}")
      response = @conn.patch do |req|
        set_path_and_headers(req, path, headers)
        if payload
          req.body = JSON.generate(payload)
        end
      end

      log_response(response)
      HttpResponse.new(response.status, response.body, response.headers)
    end

    # @param [String] path
    # @param [String] payload
    # @param [Hash] headers
    # @return [HttpResponse] API HTTP response
    def post(path, payload = nil, headers = {})
      Config.logger.debug("POST \"#{path}\" #{payload}")

      response = @conn.post do |req|
        set_path_and_headers(req, path, headers)
        if payload
          req.body = payload
        end
      end

      log_response(response)
      HttpResponse.new(response.status, response.body, response.headers)
    end

    def upload(path, filepath, filename = nil, payload = {}, headers = {})
      Config.logger.debug("POST \"#{path}\" \"#{filepath}\" \"#{filename}\" #{payload}")

      payload = payload.merge({
          :file => Faraday::UploadIO.new(filepath, DEFAULT_UPLOAD_CONTENT_TYPE, filename),
      })

      response = @up_conn.post do |req|
        req.url(path)
        req.body = payload
        req.headers = {"Content-Type" => MULTIPART_CONTENT_TYPE}.merge(headers).merge(get_auth_header())
      end

      log_response(response)
      HttpResponse.new(response.status, response.body, response.headers)
    end

    # @param [String] path
    # @param [Hash] params
    # @param [Hash] headers
    # @return [HttpResponse] API HTTP response object
    def get(path, params = nil, headers = {})
      Config.logger.debug("GET \"#{path}\" #{params}")

      response = @conn.get do |req|
        set_path_and_headers(req, path, headers)
        if params
          req.params = params
        end
      end

      log_response(response)
      HttpResponse.new(response.status, response.body, response.headers)
    end


    # @param [String] path
    # @param [Hash] params
    # @param [Hash] headers

    def getAll(path, params = nil, headers = {})
      completeList = []

      response = get(path, params, headers)
      return HttpResponse.new(response.status, response.body, response.headers) unless response.code == 200

      body = JSON.parse(response.body)
      completeList.concat(body["items"])

      while body["nextPageLink"] != nil && body["nextPageLink"] != ""
        response = get(body["nextPageLink"], nil, headers)
        return HttpResponse.new(response.status, response.body, response.headers) unless response.code == 200

        body = JSON.parse(response.body)
        body["items"].each { |item| completeList << item unless completeList.include? item }
      end

      resourceList = {
          "items" => completeList,
          "nextPageLink" => nil,
          "previousPageLink" => nil,
      }
      HttpResponse.new(200, resourceList.to_json, response.headers)
    end

    # @param [String] path
    # @param [Hash] params
    # @param [Hash] headers
    # @param [Hash] payload
    # @return [HttpResponse] API HTTP response
    def delete(path, params = nil, headers = {}, payload = nil)
      Config.logger.debug("DELETE \"#{path}\" #{params}")

      response = @conn.delete do |req|
        set_path_and_headers(req, path, headers)
        if params
          req.params = params
        end
        if payload
          req.body = JSON.generate(payload)
        end
      end

      log_response(response)
      HttpResponse.new(response.status, response.body, response.headers)
    end

    private

    def log_response(response)
      Config.logger.debug("Response: #{response.status} #{response.headers["request-id"]} #{response.body}")
    end

    def host_matches_no_proxy?
      no_proxy = ENV["no_proxy"] || ENV["NO_PROXY"]
      return false unless no_proxy

      current_host = URI.parse(@endpoint).host

      no_proxy.split(/\s*,\s*/).any? do |domain_ext|
        domain_ext.gsub!(/\.$/, "")
        domain_ext.gsub!(/^\*/, "")
        current_host == domain_ext || (domain_ext.start_with?(".") && current_host.end_with?(domain_ext))
      end
    end

    def get_auth_header
      if @access_token.nil?
        auth_header = {}
      else
        auth_header = {"Authorization" => "Bearer " + @access_token}
      end

      auth_header
    end

    # @param [Faraday::Request] req
    # @param [String] path
    # @param [Hash] headers
    def set_path_and_headers(req, path, headers)
      req.url(path)
      req.headers = {"Content-Type" => DEFAULT_CONTENT_TYPE, "Accept" => DEFAULT_CONTENT_TYPE}
        .merge(headers)
        .merge(get_auth_header())
    end

  end
end
