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

package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.common.Constants;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.PatchUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.MigrateDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.migration.MigrationUtils;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.photon.controller.common.xenon.validation.NotBlank;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.QueryTask;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Class ProjectService is used for data persistence of entity lock.
 */
public class ProjectService extends StatefulService {

  public static final String FIELD_NAME_SECURITY_GROUPS = "securityGroups";
  public static final String SECURITY_GROUPS_KEY =
      QueryTask.QuerySpecification.buildCollectionItemName(FIELD_NAME_SECURITY_GROUPS);
  public static final String SECURITY_GROUPS_NAME_KEY =
      QueryTask.QuerySpecification.buildCompositeFieldName(SECURITY_GROUPS_KEY, SecurityGroup.FIELD_NAME);

  public ProjectService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting ProjectService %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      validateState(startState);
      startOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling patch for service %s", getSelfLink());

    try {
      State currentState = getState(patchOperation);
      State patchState = patchOperation.getBody(State.class);
      validatePatchState(currentState, patchState);

      PatchUtils.patchState(currentState, patchState);
      validateState(currentState);
      patchOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  /**
   * Validate the service state for coherence.
   *
   * @param currentState
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  protected void validatePatchState(State startState, State patchState) {
    ValidationUtils.validatePatch(startState, patchState);
  }

  /**
   * Data object for SecurityGroup Item.
   * Although we already have a SecurityGroup class in ApiModel, fields in securityGroup in ApiModel are private.
   * Xenon requires field to be public in order to query it. That's the reason we're creating a SecurityGroup class
   * here.
   */
  public static class SecurityGroup {
    public static final String FIELD_NAME = "name";

    public String name;
    public boolean inherited;

    public SecurityGroup() {
    }

    public SecurityGroup(String name, boolean inherited) {
      this.name = name;
      this.inherited = inherited;
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      SecurityGroup other = (SecurityGroup) o;
      return Objects.equals(this.name, other.name)
          && this.inherited == other.inherited;
    }
  }

  /**
   * Durable service state data. Class encapsulating the data for Project.
   */
  @MigrateDuringUpgrade(transformationServicePath = MigrationUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = ProjectServiceFactory.SELF_LINK,
      destinationFactoryServicePath = ProjectServiceFactory.SELF_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  @MigrateDuringDeployment(
      factoryServicePath = ProjectServiceFactory.SELF_LINK,
      serviceName = Constants.CLOUDSTORE_SERVICE_NAME)
  public static class State extends ServiceDocument {

    @NotBlank
    @Immutable
    public String name;

    public Set<String> tagIds;

    @NotBlank
    @Immutable
    public String tenantId;

    public String resourceTicketId;

    @PropertyOptions(indexing = ServiceDocumentDescription.PropertyIndexingOption.EXPAND)
    public List<SecurityGroup> securityGroups;
  }
}
