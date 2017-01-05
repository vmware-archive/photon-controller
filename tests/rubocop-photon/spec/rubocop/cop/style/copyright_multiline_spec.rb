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

describe RuboCop::Cop::Style::CopyrightMultiline, :config do
  subject(:cop) { described_class.new(config) }

  let(:config) { RuboCop::Config.new(cop_config) }
  let(:cop_config) do
    { described_class.cop_name =>
          { "Notice" => notice } }
  end
  let(:notice) { [ "^# Copyright 2[0-9]{3}.$", "^# All rigts reserved.$" ] }

  describe "#investigate" do
    context "when complete notice is present" do
      it "does not add an offense" do
        source = <<-SOURCE
          # Copyright 2015.
          # All rigts reserved.
        SOURCE

        cop.investigate process_source(source)
        expect(cop.offenses).to be_empty
      end
    end

    context "when complete notice is not the first comment" do
      it "does not adds an offense" do
        source = <<-SOURCE
          # Some other comment
          # Copyright 2015.
          # All rigts reserved.
        SOURCE

        cop.investigate process_source(source)
        expect(cop.offenses).to be_empty
      end
    end

    context "when complete notice is present after code" do
      it "adds an offense" do
        source = <<-SOURCE
          names = Array.new
          # Copyright 2015.
          # All rigts reserved.
        SOURCE

        cop.investigate process_source(source)
        expect(cop.offenses.size).to eq(1)
        expect(cop.message).to start_with("Include a copyright notice matching")
      end
    end

    context "when first line of notice does not match" do
      it "adds an offense" do
        source = <<-SOURCE
          # Copyright 2015. [BAD]
          # All rigts reserved.
        SOURCE

        cop.investigate process_source(source)
        expect(cop.offenses.size).to eq(1)
        expect(cop.message).to start_with("Include a copyright notice matching")
      end
    end

    context "when second line of notice does not match" do
      it "adds an offense" do
        source = <<-SOURCE
          # Copyright 2015.
          # All.
        SOURCE

        cop.investigate process_source(source)
        expect(cop.offenses.size).to eq(1)
        expect(cop.message).to start_with("Include a copyright notice matching")
      end
    end

    context "when file has less lines than notice" do
      it "adds an offense" do
        source = <<-SOURCE
          # Copyright 2015.
        SOURCE

        cop.investigate process_source(source)
        expect(cop.offenses.size).to eq(1)
        expect(cop.message).to start_with("Include a copyright notice matching")
      end
    end
  end

  def process_source(source)
    RuboCop::ProcessedSource.new(source)
  end
end
