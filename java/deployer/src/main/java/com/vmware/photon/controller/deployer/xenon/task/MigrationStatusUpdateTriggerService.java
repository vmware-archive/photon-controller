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
package com.vmware.photon.controller.deployer.xenon.task;

import com.vmware.photon.controller.cloudstore.xenon.entity.DeploymentService;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.deployer.xenon.util.HostUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This service reports the status of the migration initialization.
 */
public class MigrationStatusUpdateTriggerService extends StatefulService {

  private static final long DEFAULT_TRIGGER_INTERVAL = TimeUnit.SECONDS.toMicros(5);

  /**
   * This class defines the document state associated with a single
   * {@link MigrationStatusUpdateTriggerService} instance.
   */
  @NoMigrationDuringUpgrade
  @NoMigrationDuringDeployment
  public static class State extends ServiceDocument {
    /**
     * This value represents the link to the deployment document.
     */
    @Immutable
    @NotNull
    public String deploymentServiceLink;
  }

  public MigrationStatusUpdateTriggerService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
    super.setMaintenanceIntervalMicros(DEFAULT_TRIGGER_INTERVAL);
  }

  @Override
  public void handleStart(Operation start) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    State startState = start.getBody(State.class);
    InitializationUtils.initialize(startState);
    ValidationUtils.validateState(startState);
    start.setBody(startState).complete();
  }

  @Override
  public void handlePatch(Operation patchOp) {
    State currentState = getState(patchOp);
    patchOp.complete();

    QueryTask queryTask = QueryTask.Builder.createDirectTask()
        .setQuery(QueryTask.Query.Builder.create()
            .addKindFieldClause(CopyStateTaskService.State.class, QueryTask.Query.Occurance.SHOULD_OCCUR)
            .build())
        .addOptions(EnumSet.of(
            QueryTask.QuerySpecification.QueryOption.BROADCAST,
            QueryTask.QuerySpecification.QueryOption.EXPAND_CONTENT))
        .build();

    sendRequest(Operation
        .createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
        .setBody(queryTask)
        .setCompletion(
            (o, e) -> {
              try {
                if (e != null) {
                  ServiceUtils.logSevere(this, e);
                } else {
                  processTaskQueryResults(currentState.deploymentServiceLink,
                      o.getBody(QueryTask.class).results.documents);
                }
              } catch (Throwable t) {
                ServiceUtils.logSevere(this, t);
              }
            }));
  }

  private void processTaskQueryResults(String deploymentServiceLink, Map<String, Object> documents) {

    //
    // Filter the query results by document type and update the deployment document with the
    // results.
    //
    // N.B. This should at some point be re-implemented as a continous query using only the
    // document count. Sending the full document body over the wire will be impractical at scale.
    //

    List<CopyStateTaskService.State> copyStateTaskStates = documents.entrySet().stream()
        .filter((entry) -> entry.getKey().startsWith(CopyStateTaskFactoryService.SELF_LINK))
        .map((entry) -> Utils.fromJson(entry.getValue(), CopyStateTaskService.State.class))
        .collect(Collectors.toList());

    Map<String, Integer> dataMigrationProgress = HostUtils.getDeployerContext(this)
        .getUpgradeInformation().stream()
        .map((upgradeInfo) -> appendIfNotExists(upgradeInfo.sourceFactoryServicePath, "/"))
        .collect(Collectors.toMap(
            (factoryServicePath) -> factoryServicePath,
            (factoryServicePath) -> (int) copyStateTaskStates.stream()
                .filter((state) -> state.sourceFactoryLink.equals(factoryServicePath) &&
                    state.taskState.stage == TaskStage.FINISHED)
                .count()));

    DeploymentService.State patchState = new DeploymentService.State();
    patchState.dataMigrationProgress = dataMigrationProgress;

    sendRequest(HostUtils
        .getCloudStoreHelper(this)
        .createPatch(deploymentServiceLink)
        .setBody(patchState)
        .setCompletion(
            (o, e) -> {
              if (e != null) {
                ServiceUtils.logSevere(this, e);
              }
            }));
  }

  private String appendIfNotExists(String factoryLink, String string) {
    return factoryLink.endsWith(string) ? factoryLink : factoryLink + string;
  }

  @Override
  public void handlePeriodicMaintenance(Operation maintenanceOp) {
    maintenanceOp.complete();
    sendRequest(Operation.createPatch(this, getSelfLink()).setBody(new State()));
  }
}
