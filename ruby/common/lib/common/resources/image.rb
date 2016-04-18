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
  class Image

    attr_accessor :id, :name, :state, :size, :replication, :replication_progress, :seeding_progress, :settings

    # @param [String] path
    # @param [String] name image name
    # @return [Image]
    def self.create(path, name = nil, image_replication = nil)
      Config.client.create_image(path, name, image_replication)
    end

    # @param [String] id
    # @param [EsxCloud::ImageCreateSpec] spec
    # @return [Image]
    def self.create_from_vm(id, spec)
      Config.client.create_image_from_vm(id, spec.to_hash)
    end

    # @param [String] image_id
    # @return [Boolean]
    def self.delete(image_id)
      Config.client.delete_image(image_id)
    end

    # @param [String] path
    # @return [Task]
    def self.validate(path)
      Config.client.validate_image(path)
    end

    # @param [String] id
    # @return [Image]
    def self.find_by_id(id)
      Config.client.find_image_by_id(id)
    end

    # @return [ImageList]
    def self.find_all
      Config.client.find_all_images
    end

    # @return [ImageList]
    def self.find_by_name(name)
      Config.client.find_images_by_name(name)
    end

    # @param [String] json
    # @return [Image]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [Image]
    def self.create_from_hash(hash)
      unless hash.is_a?(Hash) && hash.keys.to_set.superset?(%w(id name state replicationType).to_set)
        fail UnexpectedFormat, "Invalid image hash: #{hash}"
      end

      new(hash["id"], hash["name"], hash["state"], hash["size"], hash["replicationType"], hash["replicationProgress"],
          hash["seedingProgress"], hash["settings"])
    end

    # @return [Boolean]
    def delete
      self.class.delete(@id)
    end

    # @param [String] id
    # @param [String] name
    # @param [String] state
    # @param [Integer] size
    def initialize(id, name, state, size, replication, replication_progress, seeding_progress, settings)
      @id = id
      @name = name
      @state = state
      @size = size
      @replication = replication
      @replication_progress = replication_progress
      @seeding_progress = seeding_progress
      @settings = settings
    end

    def ==(other)
      @id == other.id &&
      @name == other.name &&
      @state == other.state &&
      @size == other.size &&
      @replication == other.replication &&
      @replication_progress == other.replication_progress &&
      @seeding_progress == other.seeding_progress &&
      @settings == other.settings
    end

  end
end
