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

package com.vmware.photon.controller.model.resources;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Describes a resource pool. Resources are associated with a resource pool through the
 * resourcePoolLink which allows planning and allocation tasks to track the desired versus
 * realized resources.
 */
public class ResourcePoolService extends StatefulService {

  /**
   * This class represents the document state associated with a
   * {@link ResourcePoolService} task.
   */
  public static class ResourcePoolState extends ServiceDocument {

    /**
     * Enumeration used to define properties of the resource pool.
     */
    public enum ResourcePoolProperty {
      ELASTIC,
      HYBRID
    }

    /**
     * Identifier of this resource pool.
     */
    public String id;

    /**
     * Name of this resource pool.
     */
    public String name;

    /**
     * Project name of this resource pool.
     */
    public String projectName;

    /**
     * Properties of this resource pool, if it is elastic, hybrid etc.
     */
    public EnumSet<ResourcePoolProperty> properties;

    /**
     * Minimum number of CPU Cores in this resource pool.
     */
    public long minCpuCount;

    /**
     * Minimum number of GPU Cores in this resource pool.
     */
    public long minGpuCount;

    /**
     * Minimum amount of memory (in bytes) in this resource pool.
     */
    public long minMemoryBytes;

    /**
     * Minimum disk capacity (in bytes) in this resource pool.
     */
    public long minDiskCapacityBytes;

    /**
     * Maximum number of CPU Cores in this resource pool.
     */
    public long maxCpuCount;

    /**
     * Maximum number of GPU Cores in this resource pool.
     */
    public long maxGpuCount;

    /**
     * Maximum amount of memory (in bytes) in this resource pool.
     */
    public long maxMemoryBytes;

    /**
     * Maximum disk capacity (in bytes) in this resource pool.
     */
    public long maxDiskCapacityBytes;

    /**
     * Maximum CPU Cost (per minute) in this resource pool.
     */
    public Double maxCpuCostPerMinute;

    /**
     * Maximum Disk cost (per minute) in this resource pool.
     */
    public Double maxDiskCostPerMinute;

    /**
     * Currency unit used for pricing.
     */
    public String currencyUnit;

    /**
     * Custom property bag that can be used to store pool specific properties.
     */
    public Map<String, String> customProperties;

    /**
     * A list of tenant links which can access this resource pool,
     * represented in the form of documentSelfLinks.
     */
    public List<String> tenantLinks;

    /**
     * Query description for the resource pool.
     */
    public QueryTask.QuerySpecification querySpecification;
  }

  public ResourcePoolService() {
    super(ResourcePoolState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.HTML_USER_INTERFACE, true);
  }

  @Override
  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      start.fail(new IllegalArgumentException("body is required"));
      return;
    }

    ResourcePoolState initState = start.getBody(ResourcePoolState.class);
    validateState(initState);
    createResourceQuerySpec(initState);
    start.complete();
  }

  @Override
  public void handlePatch(Operation patch) {
    ResourcePoolState currentState = getState(patch);
    ResourcePoolState patchBody = patch.getBody(ResourcePoolState.class);

    boolean isChanged = false;

    if (patchBody.name != null && !patchBody.name.equals(currentState.name)) {
      currentState.name = patchBody.name;
      isChanged = true;
    }

    if (patchBody.maxCpuCostPerMinute != null
        && !patchBody.maxCpuCostPerMinute.equals(currentState.maxCpuCostPerMinute)) {
      currentState.maxCpuCostPerMinute = patchBody.maxCpuCostPerMinute;
      isChanged = true;
    }

    if (patchBody.maxDiskCostPerMinute != null
        && !patchBody.maxDiskCostPerMinute.equals(currentState.maxDiskCostPerMinute)) {
      currentState.maxDiskCostPerMinute = patchBody.maxDiskCostPerMinute;
      isChanged = true;
    }

    if (patchBody.projectName != null
        && !patchBody.projectName.equals(currentState.projectName)) {
      currentState.projectName = patchBody.projectName;
      isChanged = true;
    }

    if (!isChanged) {
      patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
    }
    patch.complete();
  }

  public static void validateState(ResourcePoolState state) {

    if (state.properties == null) {
      state.properties = EnumSet.noneOf(ResourcePoolState.ResourcePoolProperty.class);
    }
  }

  private void createResourceQuerySpec(ResourcePoolState initState) {

    QueryTask.QuerySpecification querySpec = new QueryTask.QuerySpecification();

    String kind = Utils.buildKind(ComputeService.ComputeState.class);
    QueryTask.Query kindClause = new QueryTask.Query()
        .setTermPropertyName(ServiceDocument.FIELD_NAME_KIND)
        .setTermMatchValue(kind);
    querySpec.query.addBooleanClause(kindClause);

    // we want compute resources only in the same resource pool as this task
    QueryTask.Query resourcePoolClause = new QueryTask.Query()
        .setTermPropertyName(ComputeService.ComputeState.FIELD_NAME_RESOURCE_POOL_LINK)
        .setTermMatchValue(getSelfLink());
    querySpec.query.addBooleanClause(resourcePoolClause);

    initState.querySpecification = querySpec;
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    return td;
  }
}
