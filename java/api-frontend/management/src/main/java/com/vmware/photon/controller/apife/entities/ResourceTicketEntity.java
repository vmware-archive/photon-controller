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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.common.entities.base.VisibleModelEntity;
import com.vmware.photon.controller.api.model.QuotaLineItem;
import com.vmware.photon.controller.api.model.ResourceTicket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resource ticket entity.
 * <p/>
 * This class is the ResourceTicket and its job is to manage quota/usage, flavor lists,
 * and mandated tags. A tenant may link to several ResourceTickets. A project may only link
 * to a single ResourceTicket. The project level ResourceTicket being carved out from a tenant
 * level resource ticket.
 * <p/>
 * The Quota system and the Cost system associated with Kinds and Flavors (see config/vm.yml)
 * are both based on collections of QuotaLine items where each one of these contains:
 * - key - used to represent the metrics name (vm, vm.cpu, vm.memory, disk.capacity, etc.)
 * - value - a double that contains the value of this key
 * - unit - the value's unit (GB, KB, etc.)
 * <p/>
 * The systems are very loosely coupled. For instance, the cost of a vm might be specified
 * using keys: vm, vm.cost, vm.cpu, vm.memory, etc. The quota block enforcing quota on this
 * project might be specified using a much narrower set: vm.cost, disk.cost, network.cost, etc.
 * <p/>
 * The quota system matches the cost of a resource with the limit keys and enforces quota and
 * performs usage accounting only in the key space defined by the quota. The key space of this
 * object is considered immutable. This allows us to perform quota consume/return operations without
 * having to track in a cost object which keys participated in a quota consume operation.
 * The only keys that participate are those in the quota object and this set can not change.
 * The only mutations allowed on this object are usage tracking across the entire key space,
 * and limit adjustments both up/down on existing keys.
 * <p/>
 * <p/>
 * TODO(olegs): split into project and tenant resource tickets.
 * TODO(markl): extend resource ticket: include white/blacklists: https://www.pivotaltracker.com/story/show/48531861
 * TODO(markl): extend resource ticket: include forced tags:
 */
public class ResourceTicketEntity extends VisibleModelEntity {

  public static final String KIND = "resource-ticket";

  private String tenantId;

  // the parent is used in project level resource tickets
  // and points to the tenant level resource ticket that this
  // ticket is based on. when a ticket with a parent is destroyed,
  // its current usage needs to be returned to the parent
  private String parentId;

  // maps to track usage/limits and provide direct lookup
  private Map<String, QuotaLineItemEntity> limitMap = new HashMap<>();

  private Map<String, QuotaLineItemEntity> usageMap = new HashMap<>();

  @Override
  public String getKind() {
    return KIND;
  }

  public QuotaLineItemEntity getUsage(String key) {
    return usageMap.get(key);
  }

  public QuotaLineItemEntity getLimit(String key) {
    return limitMap.get(key);
  }

  public Map<String, QuotaLineItemEntity> getUsageMap() {
    return usageMap;
  }

  // used strictly by the mappers, not the public interface
  public void setUsageMap(Map<String, QuotaLineItemEntity> usageMap) {
    this.usageMap = usageMap;
  }

  public Map<String, QuotaLineItemEntity> getLimitMap() {
    return limitMap;
  }

  // used strictly by the mappers, not the public interface
  public void setLimitMap(Map<String, QuotaLineItemEntity> limitMap) {
    this.limitMap = limitMap;
  }

  public List<QuotaLineItemEntity> getLimits() {
    return new ArrayList<>(limitMap.values());
  }

  public void setLimits(List<QuotaLineItemEntity> limits) {
    this.limitMap = new HashMap<>();
    for (QuotaLineItemEntity qli : limits) {
      limitMap.put(qli.getKey(), qli);
      // ensure that there is always a usage value for any covered limit
      // preserve existing usage if set, else create a 0.0 usage record
      if (!usageMap.containsKey(qli.getKey())) {
        usageMap.put(qli.getKey(), new QuotaLineItemEntity(qli.getKey(), 0.0, qli.getUnit()));
      }
    }
  }

  public List<QuotaLineItemEntity> getUsage() {
    return new ArrayList<>(usageMap.values());
  }

  public void setUsage(List<QuotaLineItemEntity> usage) {
    this.usageMap = new HashMap<>();
    for (QuotaLineItemEntity qli : usage) {
      usageMap.put(qli.getKey(), qli);
    }
  }

  public Set<String> getLimitKeys() {
    return limitMap.keySet();
  }

  public Set<String> getUsageKeys() {
    return usageMap.keySet();
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public ResourceTicket toApiRepresentation() {
    ResourceTicket result = new ResourceTicket();

    result.setId(getId());
    result.setName(getName());
    result.setTenantId(getTenantId());

    List<QuotaLineItem> limits = new ArrayList<>();
    List<QuotaLineItem> usage = new ArrayList<>();

    for (QuotaLineItemEntity qli : limitMap.values()) {
      limits.add(new QuotaLineItem(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    for (QuotaLineItemEntity qli : usageMap.values()) {
      usage.add(new QuotaLineItem(qli.getKey(), qli.getValue(), qli.getUnit()));
    }

    result.setLimits(limits);
    result.setUsage(usage);

    return result;
  }
}
