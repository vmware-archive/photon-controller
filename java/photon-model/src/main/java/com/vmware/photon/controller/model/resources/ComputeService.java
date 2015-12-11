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

import com.vmware.photon.controller.model.adapterapi.ComputeHealthRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeHealthResponse;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.apache.commons.validator.routines.InetAddressValidator;

import java.net.URI;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a compute resource.
 */
public class ComputeService extends StatefulService {

  public static final String STAT_NAME_CPU_UTIL_MHZ = "cpuUtilizationMhz";
  public static final String STAT_NAME_MEMORY_USED_BYTES = "memoryUsedBytes";
  public static final String STAT_NAME_CPU_TOTAL_MHZ = "cpuTotalMhz";
  public static final String STAT_NAME_MEMORY_TOTAL_BYTES = "memoryTotalBytes";
  public static final String STAT_NAME_CPU_UTIL_PCT = "cpuUtilizationPct";
  public static final String STAT_NAME_MEMORY_UTIL_PCT = "memoryUtilizationPct";
  public static final String STAT_NAME_HEALTH = "isHealthy";

  /**
   * Power State.
   */
  public static enum PowerState {
    ON,
    OFF,
    UNKNOWN,
    SUSPEND
  }

  /**
   * Power Transition.
   */
  public enum PowerTransition {
    SOFT,
    HARD
  }

  /**
   * Boot Device.
   */
  public enum BootDevice {
    CDROM,
    DISK,
    NETWORK
  }

  /**
   * Compute State document.
   */
  public static class ComputeState extends ServiceDocument {
    public static final String FIELD_NAME_ID = "id";
    public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
    public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
    public static final String FIELD_NAME_PARENT_LINK = "parentLink";
    public static final String CUSTOM_PROPERTY_NAME_RUNTIME_INFO = "runtimeInfo";

    /**
     * Identifier of this compute instance.
     */
    public String id;

    /**
     * URI reference to corresponding ComputeDescription.
     */
    public String descriptionLink;

    /**
     * URI reference to corresponding resource pool.
     */
    public String resourcePoolLink;

    /**
     * Ip address of this compute instance.
     */
    public String address;

    /**
     * MAC address of this compute instance.
     */
    public String primaryMAC;

    /**
     * Power state of this compute instance.
     */
    public PowerState powerState = PowerState.UNKNOWN;

    /**
     * URI reference to parent compute instance.
     */
    public String parentLink;

    /**
     * URI reference to resource pool management site.
     */
    public URI adapterManagementReference;

    /**
     * Disks associated with this compute instance.
     */
    public List<String> diskLinks;

    /**
     * Network interfaces associated with this compute instance.
     */
    public List<String> networkLinks;

    /**
     * Custom properties of this compute instance.
     */
    public Map<String, String> customProperties;

    /**
     * A list of tenant links which can access this compute resource.
     */
    public List<String> tenantLinks;
  }

  /**
   * State with in-line, expanded description.
   */
  public static class ComputeStateWithDescription extends ComputeState {
    /**
     * Compute description associated with this compute instance.
     */
    public ComputeDescription description;

    public static URI buildUri(URI computeHostUri) {
      return UriUtils.extendUriWithQuery(computeHostUri, UriUtils.URI_PARAM_ODATA_EXPAND,
          ComputeState.FIELD_NAME_DESCRIPTION_LINK);
    }

    public static ComputeStateWithDescription create(ComputeDescription desc,
                                                     ComputeState currentState) {
      ComputeStateWithDescription chsWithDesc = new ComputeStateWithDescription();
      currentState.copyTo(chsWithDesc);

      chsWithDesc.address = currentState.address;
      chsWithDesc.diskLinks = currentState.diskLinks;
      chsWithDesc.id = currentState.id;
      chsWithDesc.parentLink = currentState.parentLink;
      chsWithDesc.powerState = currentState.powerState;
      chsWithDesc.primaryMAC = currentState.primaryMAC;
      chsWithDesc.resourcePoolLink = currentState.resourcePoolLink;
      chsWithDesc.adapterManagementReference = currentState.adapterManagementReference;
      chsWithDesc.customProperties = currentState.customProperties;
      chsWithDesc.networkLinks = currentState.networkLinks;
      chsWithDesc.tenantLinks = currentState.tenantLinks;

      chsWithDesc.description = desc;
      chsWithDesc.descriptionLink = desc.documentSelfLink;

      return chsWithDesc;
    }
  }

  public ComputeService() {
    super(ComputeState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleGet(Operation get) {
    ComputeState currentState = getState(get);
    boolean doExpand = get.getUri().getQuery() != null
        && get.getUri().getQuery().contains(UriUtils.URI_PARAM_ODATA_EXPAND);

    if (!doExpand) {
      get.setBody(currentState).complete();
      return;
    }

    // retrieve the description and include in an augmented version of our state.
    Operation getDesc = Operation
        .createGet(this, currentState.descriptionLink)
        .setCompletion((o, e) -> {
          if (e != null) {
            get.fail(e);
            return;
          }
          ComputeDescription desc = o.getBody(ComputeDescription.class);
          ComputeStateWithDescription chsWithDesc = ComputeStateWithDescription.create(
              desc, currentState);
          get.setBody(chsWithDesc).complete();
        });
    sendRequest(getDesc);
  }

  @Override
  public void handleStart(Operation start) {
    if (!start.hasBody()) {
      throw new IllegalArgumentException("body is required");
    }

    getHost().registerForServiceAvailability((o, e) -> {
      completeStart(start);
    }, ComputeDescriptionFactoryService.SELF_LINK);
  }

  private void completeStart(Operation start) {
    try {
      ComputeState s = start.getBody(ComputeState.class);
      validateState(s);

      Operation getDesc = Operation
          .createGet(this, s.descriptionLink)
          .setCompletion((o, e) -> {
            if (e != null) {
              start.fail(e);
              return;
            }

            ComputeDescription desc = o.getBody(ComputeDescription.class);
            try {
              validateSupportedChildren(s, desc);
            } catch (Throwable ex) {
              start.fail(ex);
              return;
            }

            setUpMaintenance(s, start);
          });

      sendRequest(getDesc);
    } catch (Throwable e) {
      start.fail(e);
    }
  }

  private void setUpMaintenance(ComputeState s, Operation start) {

    CompletionHandler c = (o, e) -> {
      if (e != null) {
        logWarning("failure retrieving description: %s", Utils.toString(e));
        start.complete();
        return;
      }
      ComputeDescription desc = o.getBody(ComputeDescription.class);

      if (desc.healthAdapterReference == null) {
        start.complete();
        return;
      }

      // enable maintenance on self, so we poll for health stats.
      logInfo("Enabling periodic health and stats retrieval for %s", s.documentSelfLink);
      toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
      start.complete();
    };

    sendRequest(Operation.createGet(this, s.descriptionLink).setCompletion(c));
  }

  private static void validateSupportedChildren(ComputeState state,
                                                ComputeDescription description) {

    if (description.supportedChildren == null) {
      return;
    }

    Iterator<String> childIterator = description.supportedChildren.iterator();
    while (childIterator.hasNext()) {
      ComputeType type = ComputeType.valueOf(childIterator.next());
      switch (type) {
        case VM_HOST:
        case PHYSICAL:
          if (state.adapterManagementReference == null) {
            throw new IllegalArgumentException("adapterManagementReference is required");
          }
          break;
        case DOCKER_CONTAINER:
          break;
        case OS_ON_PHYSICAL:
          break;
        case VM_GUEST:
          break;
        default:
          break;
      }
    }
  }

  public void validateState(ComputeState state) {
    if (state.id == null) {
      throw new IllegalArgumentException("id is required");
    }

    if (state.powerState == PowerState.ON) {
      // do not require service references for running hosts.
      return;
    }

    if (state.descriptionLink == null) {
      throw new IllegalArgumentException("descriptionLink is required");
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    ComputeState currentState = getState(patch);
    ComputeState patchBody = patch.getBody(ComputeState.class);

    boolean isChanged = false;

    if (patchBody.id != null && !patchBody.id.equals(currentState.id)) {
      currentState.id = patchBody.id;
      isChanged = true;
    }

    if (patchBody.address != null && !patchBody.address.equals(currentState.address)) {
      InetAddressValidator.getInstance().isValidInet4Address(patchBody.address);
      currentState.address = patchBody.address;
      isChanged = true;
    }

    if (patchBody.powerState != null &&
        patchBody.powerState != PowerState.UNKNOWN &&
        patchBody.powerState != currentState.powerState) {
      currentState.powerState = patchBody.powerState;
      isChanged = true;
    }

    if (patchBody.primaryMAC != null && !patchBody.primaryMAC.equals(currentState.primaryMAC)) {
      currentState.primaryMAC = patchBody.primaryMAC;
      isChanged = true;
    }

    if (patchBody.diskLinks != null) {
      if (currentState.diskLinks == null) {
        currentState.diskLinks = patchBody.diskLinks;
        isChanged = true;
      } else {
        for (String link : patchBody.diskLinks) {
          if (!currentState.diskLinks.contains(link)) {
            currentState.diskLinks.add(link);
            isChanged = true;
          }
        }
      }
    }

    if (patchBody.networkLinks != null) {
      if (currentState.networkLinks == null) {
        currentState.networkLinks = patchBody.networkLinks;
        isChanged = true;
      } else {
        for (String link : patchBody.networkLinks) {
          if (!currentState.networkLinks.contains(link)) {
            currentState.networkLinks.add(link);
            isChanged = true;
          }
        }
      }
    }

    if (patchBody.resourcePoolLink != null
        && !patchBody.resourcePoolLink.equals(currentState.resourcePoolLink)) {
      currentState.resourcePoolLink = patchBody.resourcePoolLink;
      isChanged = true;
    }

    if (patchBody.adapterManagementReference != null
        && !patchBody.adapterManagementReference
        .equals(currentState.adapterManagementReference)) {
      currentState.adapterManagementReference = patchBody.adapterManagementReference;
      isChanged = true;
    }

    if (patchBody.descriptionLink != null
        && !patchBody.descriptionLink.equals(currentState.descriptionLink)) {
      currentState.descriptionLink = patchBody.descriptionLink;
      isChanged = true;
    }

    if (patchBody.customProperties != null && !patchBody.customProperties.isEmpty()) {
      if (currentState.customProperties == null || currentState.customProperties.isEmpty()) {
        currentState.customProperties = patchBody.customProperties;
      } else {
        for (Map.Entry<String, String> e : patchBody.customProperties.entrySet()) {
          currentState.customProperties.put(e.getKey(), e.getValue());
        }
      }
      isChanged = true;
    }

    if (!isChanged) {
      patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
    }
    patch.complete();
  }

  @Override
  public void handleMaintenance(Operation maintOp) {
    // acquire health information from compute resource and translate into
    // stat entries for this service instance
    CompletionHandler c = (o, e) -> {
      if (e != null) {
        logWarning("failure retrieving description: %s", Utils.toString(e));
        maintOp.fail(e);
        return;
      }

      ComputeStateWithDescription state = o.getBody(ComputeStateWithDescription.class);
      ComputeHealthRequest req = new ComputeHealthRequest();
      req.computeReference = getUri();

      if (state.description.healthAdapterReference == null) {
        maintOp.complete();
        return;
      }

      CompletionHandler ci = (healthOp, ex) -> {
        if (ex != null) {
          maintOp.fail(ex);
          return;
        }
        maintOp.complete();
        updateHealthStats(healthOp.getBody(ComputeHealthResponse.class));
      };

      Operation patch = Operation.createPatch(state.description.healthAdapterReference)
          .setBody(req)
          .setCompletion(ci);
      sendRequest(patch);
    };

    // send GET to self to get description and state.
    sendRequest(Operation.createGet(ComputeStateWithDescription.buildUri(getUri()))
        .setCompletion(c));
  }

  private void updateHealthStats(ComputeHealthResponse body) {
    if (body == null) {
      return;
    }

    if (body.healthState != null) {
      setStat(STAT_NAME_HEALTH, body.healthState.ordinal());
    }
    setStat(STAT_NAME_CPU_UTIL_PCT, body.cpuUtilizationPct);
    setStat(STAT_NAME_CPU_TOTAL_MHZ, body.cpuTotalMhz);
    setStat(STAT_NAME_CPU_UTIL_MHZ, body.cpuUtilizationMhz);
    setStat(STAT_NAME_MEMORY_UTIL_PCT, body.memoryUtilizationPct);
    setStat(STAT_NAME_MEMORY_TOTAL_BYTES, body.totalMemoryBytes);
    setStat(STAT_NAME_MEMORY_USED_BYTES, body.usedMemoryBytes);
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();

    // enable indexing of custom properties map.
    ServiceDocumentDescription.PropertyDescription pdCustomProperties = td.documentDescription.propertyDescriptions
        .get(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES);
    pdCustomProperties.indexingOptions = EnumSet
        .of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND);

    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);

    ComputeState template = (ComputeState) td;

    template.id = UUID.randomUUID().toString();
    template.primaryMAC = "01:23:45:67:89:ab";
    template.descriptionLink = UriUtils.buildUriPath(
        ComputeDescriptionFactoryService.SELF_LINK,
        "on-prem-one-cpu-vm-guest");
    template.resourcePoolLink = null;
    template.adapterManagementReference = URI.create("https://esxhost-01:443/sdk");

    return template;
  }
}
