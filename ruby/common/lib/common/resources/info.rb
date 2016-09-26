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
  class Info
    attr_reader :base_version,
                :full_version,
                :git_commit_hash,
                :network_type

    # @param [String] base_version
    # @param [String] full_version
    # @param [String] git_commit_hash
    # @param [String] network_type
    def initialize(base_version, full_version, git_commit_hash, network_type)
      @base_version = base_version
      @full_version = full_version
      @git_commit_hash = git_commit_hash
      @network_type = network_type
    end

    def ==(other)
      @base_version == other.base_version &&
      @full_version == other.full_version &&
      @git_commit_hash == other.git_commit_hash &&
      @network_type == other.network_type
    end

    # @param [Hash] hash
    # @return [Info]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash)
        raise UnexpectedFormat, "Invalid Info hash: #{hash}"
      end

      new(hash["baseVersion"],
          hash["fullVersion"],
          hash["gitCommitHash"],
          hash["networkType"])
    end

    # @param [String] json
    # @return [Info]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

  end
end
