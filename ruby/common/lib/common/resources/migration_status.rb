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

  # Contains migration status Info
  class MigrationStatus

    attr_reader :completed_cycles, :data_migration_cycle_progress, :data_migration_cycle_size, :vibs_uploaded, :vibs_uploading

    # @param [String] json
    # @return [MigrationStatus]
    def self.create_from_json(json)
      create_from_hash(JSON.parse(json))
    end

    # @param [Hash] hash
    # @return [MigrationStatus]
    def self.create_from_hash(hash)
      if hash.nil? # for backwards compat, the old management plane does not have a migration status
        new
      else
        unless hash.is_a?(Hash)
          fail UnexpectedFormat, "Invalid MigrationStatus hash: #{hash}"
        end

        new(hash["completedDataMigrationCycles"], hash["dataMigrationCycleProgress"], hash["dataMigrationCycleSize"], hash["vibsUploaded"], hash["vibsUploading"])
      end
    end

    # @param [Int] completed_cycles
    # @param [Int] data_migration_cycle_progress
    # @param [Int] data_migration_cycle_size
    def initialize(completed_cycles = 0, data_migration_cycle_progress = 0, data_migration_cycle_size = 0, vibs_uploaded = 0, vibs_uploading = 0)
      @completed_cycles = completed_cycles
      @data_migration_cycle_progress = data_migration_cycle_progress
      @data_migration_cycle_size = data_migration_cycle_size
      @vibs_uploaded = vibs_uploaded
      @vibs_uploading = vibs_uploading
    end

    # @param [MigrationStatus] other
    def ==(other)
      @completed_cycles == other.completed_cycles &&
      @data_migration_cycle_progress == other.data_migration_cycle_progress &&
      @data_migration_cycle_size == other.data_migration_cycle_size &&
      @vibs_uploaded == other.vibs_uploaded &&
      @vibs_uploading == other. vibs_uploading
    end

    def to_hash
      {
          completedCycles: @completed_cycles,
          dataMigrationCycleProgress: @data_migration_cycle_progress,
          dataMigrationCycleSize: @data_migration_cycle_size,
          vibsUploaded: @vibs_uploaded,
          vibsUploading: @vibs_uploading,
      }
    end

  end
end