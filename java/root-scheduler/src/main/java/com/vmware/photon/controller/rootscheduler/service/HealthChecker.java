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

package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.chairman.gen.Chairman;
import com.vmware.photon.controller.chairman.gen.ReportMissingRequest;
import com.vmware.photon.controller.chairman.gen.ReportMissingResultCode;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedRequest;
import com.vmware.photon.controller.chairman.gen.ReportResurrectedResultCode;
import com.vmware.photon.controller.common.thrift.ClientProxy;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.roles.gen.ChildInfo;
import com.vmware.photon.controller.roles.gen.SchedulerRole;
import com.vmware.photon.controller.rootscheduler.HealthCheckConfig;
import com.vmware.photon.controller.rootscheduler.HeartbeatServerSetFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tracks child schedulers and sends out missing children reports when they go down.
 */
public class HealthChecker {
  private static final Logger logger = LoggerFactory.getLogger(HealthChecker.class);

  private final ClientProxy<Chairman.AsyncClient> chairmanClient;
  private final HeartbeatServerSetFactory serverSetFactory;
  private final ScheduledExecutorService executorService;
  private final HealthCheckConfig config;

  private final String id;
  private final Map<InetSocketAddress, String> schedulers;
  private final Map<String, Long> schedulerUpdates;
  private final Set<String> missingSchedulers;
  private final Set<String> resurrectedSchedulers;
  // Host IDs of currently active schedulers
  private final Set<String> activeSchedulers;
  private ScheduledFuture<?> scheduledReport;
  private ServerSet serverSet;

  @VisibleForTesting
  ServerSet.ChangeListener listener;

  @Inject
  public HealthChecker(@Assisted SchedulerRole schedulerRole,
                       ClientProxy<Chairman.AsyncClient> chairmanClient,
                       ScheduledExecutorService executorService,
                       HealthCheckConfig config,
                       HeartbeatServerSetFactory serverSetFactory) {
    this.chairmanClient = chairmanClient;
    this.serverSetFactory = serverSetFactory;
    this.executorService = executorService;
    this.config = config;

    id = schedulerRole.getId();

    if (schedulerRole.getHosts() != null) {
      throw new IllegalArgumentException("Root scheduler role cannot have child hosts");
    }

    schedulerUpdates = new ConcurrentHashMap<>();
    missingSchedulers = Collections.synchronizedSet(new HashSet<String>());
    resurrectedSchedulers = Collections.synchronizedSet(new HashSet<String>());
    activeSchedulers = Collections.synchronizedSet(new HashSet<String>());
    schedulers = getSchedulers(schedulerRole);
  }

  /**
   * Converts SchedulerRole to a map from InetSocketAddress to host ID.
   */
  public Map<InetSocketAddress, String> getSchedulers(SchedulerRole schedulerRole) {
    Map<InetSocketAddress, String> schedulers = new HashMap<>();
    if (schedulerRole.getScheduler_children() != null) {
      for (ChildInfo child : schedulerRole.getScheduler_children()) {
        logger.debug("Adding a child: {}", child);
        InetSocketAddress address = InetSocketAddress.createUnresolved(
            child.getAddress(), child.getPort());
        schedulers.put(address, child.getOwner_host());
      }
    }
    return schedulers;
  }

  /**
   * Starts health checker.
   */
  public synchronized void start() {
    startWatching();

    scheduledReport = executorService.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        try {
          reportResurrectedChildren();
          reportMissingChildren();
        } catch (Throwable t) {
          logger.error("Failed reporting missing children to chairman", t);
        }
      }
    }, config.getPeriodMs(), config.getPeriodMs(), TimeUnit.MILLISECONDS);
  }

  /**
   * Stops health checker.
   */
  public synchronized void stop() {
    if (scheduledReport != null) {
      scheduledReport.cancel(false);
    }

    stopWatching();
    schedulerUpdates.clear();
  }

  public void reportResurrectedChildren() throws TException {
    synchronized (this) {
      if (resurrectedSchedulers.isEmpty()) {
        return;
      }

      ReportResurrectedRequest request = new ReportResurrectedRequest();
      request.setScheduler_id(id);
      request.setSchedulers(new ArrayList(resurrectedSchedulers));
      chairmanClient.get().report_resurrected(
        request,
        new ReportResurrectedResponseHandler(request, resurrectedSchedulers)
      );
    }
    logger.info("Reported resurrected children: {}", resurrectedSchedulers);
  }

  public void reportMissingChildren() throws TException {
    synchronized (this) {
      Set<String> missingSchedulerIds = Sets.difference(getTimedOutIds(), missingSchedulers);
      if (missingSchedulerIds.isEmpty()) {
        return;
      }

      ReportMissingRequest request = new ReportMissingRequest();
      request.setScheduler_id(id);
      request.setSchedulers(new ArrayList(missingSchedulerIds));
      chairmanClient.get().report_missing(
          request,
          new ReportMissingResponseHandler(missingSchedulerIds)
      );
      logger.info("Reported missing children: {}", missingSchedulerIds);
    }
  }

  private void startWatching() {
    List<InetSocketAddress> childList = new ArrayList(schedulers.keySet());
    logger.info("Monitoring {} with timeout {} ms", childList, config.getTimeoutMs());
    serverSet = serverSetFactory.create(id, childList, config.getTimeoutMs());
    listener = new ServerSet.ChangeListener() {
      @Override
      public void onServerAdded(InetSocketAddress address) {
        String hostId = schedulers.get(address);
        missingSchedulers.remove(hostId);
        logger.info("scheduler {} ({}) came online", hostId, address);
        schedulerUpdates.remove(hostId);
        resurrectedSchedulers.add(hostId);
        activeSchedulers.add(hostId);
      }

      @Override
      public void onServerRemoved(InetSocketAddress address) {
        String hostId = schedulers.get(address);
        logger.info("scheduler {} ({}) went away", hostId, address);
        schedulerUpdates.put(hostId, System.currentTimeMillis());
        resurrectedSchedulers.remove(hostId);
        activeSchedulers.remove(hostId);
      }
    };
    serverSet.addChangeListener(listener);
  }

  private void stopWatching() {
    try {
      if (serverSet != null) {
        serverSet.close();
        serverSet = null;
      }
    } catch (IOException ex) {
      logger.warn("failed to close server set", ex);
    }
  }

  private Set<String> getTimedOutIds() {
    Set<String> result = new HashSet<>();
    for (Map.Entry<String, Long> update : schedulerUpdates.entrySet()) {
      String schedulerId = update.getKey();
      long lastSeenAt = update.getValue();

      if (System.currentTimeMillis() - lastSeenAt >= config.getTimeoutMs()) {
        result.add(schedulerId);
      }
    }
    return result;
  }

  class ReportResurrectedResponseHandler
      implements AsyncMethodCallback<Chairman.AsyncClient.report_resurrected_call> {
    private final ReportResurrectedRequest request;
    private final Set<String> reportedIds;

    ReportResurrectedResponseHandler(ReportResurrectedRequest request,
                                     Set<String> reportedIds) {
      this.request = request;
      this.reportedIds = reportedIds;
    }

    @Override
    public void onComplete(Chairman.AsyncClient.report_resurrected_call response) {
      try {
        logger.info("Reported resurrected children, host ids {} to chairman: {}",
            reportedIds, response.getResult());

        if (response.getResult().getResult() == ReportResurrectedResultCode.OK) {
          // remove reported schedulers from the resurrected scheduler set to
          // avoid reporting them again.
          synchronized (HealthChecker.this) {
            resurrectedSchedulers.removeAll(reportedIds);
          }
        }
      } catch (TException e) {
        // It will get retried in the next iteration
        logger.warn("Failed to report resurrected children to chairman", e);
      }
    }

    @Override
    public void onError(Exception e) {
      logger.error("Failed to report resurrected children to chairman", e);
    }
  }

  class ReportMissingResponseHandler implements AsyncMethodCallback<Chairman.AsyncClient.report_missing_call> {
    private final Collection<String> hostIds;

    ReportMissingResponseHandler(Collection<String> hostIds) {
      this.hostIds = hostIds;
    }

    @Override
    public void onComplete(Chairman.AsyncClient.report_missing_call response) {
      try {
        logger.info("Reported missing children host ids {} to chairman: {}",
            hostIds, response.getResult());
        if (response.getResult().getResult() == ReportMissingResultCode.OK) {
          synchronized (HealthChecker.this) {
            missingSchedulers.addAll(hostIds);
          }
        }
      } catch (TException e) {
        // It will get retried in the next iteration
        logger.error("Failed to report missing children to chairman", e);
      }
    }

    @Override
    public void onError(Exception e) {
      logger.error("Failed to report missing children to chairman", e);
    }
  }

  Set<String> getActiveSchedulers() {
    return new HashSet<>(activeSchedulers);
  }

  @VisibleForTesting
  Map<String, Long> getSchedulerUpdates() {
    return schedulerUpdates;
  }

  @VisibleForTesting
  Set<String> getMissingSchedulers() {
    return missingSchedulers;
  }

  @VisibleForTesting
  Set<String> getResurrectedSchedulers() {
    return resurrectedSchedulers;
  }

}
