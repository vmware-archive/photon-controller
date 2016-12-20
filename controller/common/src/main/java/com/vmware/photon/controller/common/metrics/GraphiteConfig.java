/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.common.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotNull;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Graphite metric reporter config.
 * <p/>
 * Based on com.codahale.dropwizard.metrics.graphite.GraphiteReporterFactory.
 */
public class GraphiteConfig {

  @NotEmpty
  @JsonProperty
  private String host = "localhost";

  @Range(min = 1, max = 65535)
  @JsonProperty
  private int port = 8080;

  @NotNull
  @JsonProperty
  private String prefix = "";

  @NotNull
  @JsonProperty
  private TimeUnit durationUnit = TimeUnit.MILLISECONDS;

  @NotNull
  @JsonProperty
  private TimeUnit rateUnit = TimeUnit.SECONDS;

  @NotNull
  @JsonProperty
  private Set<String> excludes = ImmutableSet.of();

  @NotNull
  @JsonProperty
  private Set<String> includes = ImmutableSet.of();

  @Range(min = 1)
  @JsonProperty
  private long frequency = 1;

  @NotNull
  @JsonProperty
  private TimeUnit frequencyUnit = TimeUnit.SECONDS;

  public void enable() {
    Graphite graphite = new Graphite(new InetSocketAddress(host, port));
    GraphiteReporter graphiteReporter = GraphiteReporter.forRegistry(DefaultMetricRegistry.REGISTRY)
        .prefixedWith(prefix)
        .convertDurationsTo(durationUnit)
        .convertRatesTo(rateUnit)
        .filter(new GraphitePredicate())
        .build(graphite);
    graphiteReporter.start(frequency, frequencyUnit);
  }

  private class GraphitePredicate implements MetricFilter {
    @Override
    public boolean matches(String name, Metric metric) {
      return (!includes.isEmpty() && includes.contains(name)) || !excludes.contains(name);
    }
  }
}
