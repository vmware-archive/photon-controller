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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Describes a disk instance.
 */
public class DiskService extends StatefulService {

  /**
   * Status of disk.
   */
  public static enum DiskStatus {
    DETACHED,
    ATTACHED
  }

  /**
   * Types of disk.
   */
  public static enum DiskType {
    SSD,
    HDD,
    CDROM,
    FLOPPY,
    NETWORK
  }

  /**
   * This class represents the document state associated with a
   * {@link com.vmware.photon.controller.model.resources.DiskService} task.
   */
  public static class DiskState extends ServiceDocument {
    /**
     * Identifier of this disk service instance.
     */
    public String id;

    /**
     * Identifier of the zone associated with this disk service instance.
     */
    public String zoneId;

    /**
     * Identifier of the data center associated with this disk service instance.
     */
    public String dataCenterId;

    /**
     * Identifier of the resource pool associated with this disk service instance.
     */
    public String resourcePoolLink;

    /**
     * Self-link to the AuthCredentialsService used to access this disk service instance.
     */
    public String authCredentialsLink;

    /**
     * URI reference to the source image used to create an instance of this disk service.
     */
    public URI sourceImageReference;

    /**
     * Type of this disk service instance.
     */
    public DiskType type;

    /**
     * Name of this disk service instance.
     */
    public String name;

    /**
     * Status of this disk service instance.
     */
    public DiskStatus status = DiskStatus.DETACHED;

    /**
     * Capacity (in MB) of this disk service instance.
     */
    public long capacityMBytes;

    /**
     * Custom property bag that can be used to store disk specific properties.
     */
    public Map<String, String> customProperties;

    /**
     * A list of tenant links can access this disk resource.
     */
    public List<String> tenantLinks;

    /**
     * If set, disks will be connected in ascending order by the provisioning services.
     */
    public Integer bootOrder;

    /**
     * A list of arguments used when booting from this disk.
     */
    public String[] bootArguments;

    /**
     * The bootConfig field, if set, will trigger a PATCH request to the sourceImageReference
     * with bootConfig set as the request body. The sourceImageReference in this case is
     * expected to respond with image in fat (DiskType.FLOPPY) or iso (DiskType.CDROM) format,
     * with the BootConfig.template rendered to a file on the image named by
     * bootConfig.fileName. This image can then be used for configuration by a live CD, such as
     * CoreOS' cloud-config.
     */
    public BootConfig bootConfig;

    /**
     * Reference to service that customizes this disk for a particular compute. This service
     * accepts a POST with a DiskCustomizationRequest body and streams back the
     * resulting artifact.
     * <p>
     * It is up to the caller to cache this result and make it available through this service's
     * sourceImageReference.
     */
    public URI customizationServiceReference;

    /**
     * Currency unit used for pricing.
     */
    public String currencyUnit;

    /**
     * This class represents the boot configuration for the disk service instance.
     */
    public static class BootConfig {
      /**
       * Label of the disk.
       */
      public String label;

      /**
       * Data on the disk.
       */
      public Map<String, String> data;

      /**
       * Files on the disk.
       */
      public FileEntry[] files;

      /**
       * This class represents a file on the disk.
       */
      public static class FileEntry {
        /**
         * The path of the file.
         */
        public String path;

        /**
         * Raw contents for this file.
         */
        public String contents;

        /**
         * Reference to contents for this file. If non-empty, this takes precedence over the
         * contents field.
         */
        public URI contentsReference;
      }
    }
  }

  public DiskService() {
    super(DiskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        throw new IllegalArgumentException("body is required");
      }
      validateState(start.getBody(DiskState.class));
      start.complete();
    } catch (Throwable e) {
      start.fail(e);
    }
  }

  private void validateState(DiskState state) {
    if (state.id == null) {
      throw new IllegalArgumentException("id is required");
    }

    if (state.name == null) {
      throw new IllegalArgumentException("name is required");
    }

    if (state.type == null) {
      throw new IllegalArgumentException("type is required");
    }

    if (state.capacityMBytes <= 1 && state.sourceImageReference == null
        && state.customizationServiceReference == null) {
      throw new IllegalArgumentException(
          "capacityMBytes, sourceImageReference, or customizationServiceReference is required");
    }

    if (state.status == null) {
      state.status = DiskStatus.DETACHED;
    }

    if (state.bootConfig != null) {
      for (DiskState.BootConfig.FileEntry entry : state.bootConfig.files) {
        if (entry.path == null || entry.path.length() == 0) {
          throw new IllegalArgumentException("FileEntry.path is required");
        }
      }
    }
  }

  @Override
  public void handlePatch(Operation patch) {
    DiskState currentState = getState(patch);
    DiskState patchBody = patch.getBody(DiskState.class);

    boolean isChanged = false;

    if (patchBody.zoneId != null && !patchBody.zoneId.equals(currentState.zoneId)) {
      currentState.zoneId = patchBody.zoneId;
      isChanged = true;
    }

    if (patchBody.name != null && !patchBody.name.equals(currentState.name)) {
      currentState.name = patchBody.name;
      isChanged = true;
    }

    if (patchBody.status != null && patchBody.status != currentState.status) {
      currentState.status = patchBody.status;
      isChanged = true;
    }

    if (patchBody.capacityMBytes != 0
        && patchBody.capacityMBytes != currentState.capacityMBytes) {
      currentState.capacityMBytes = patchBody.capacityMBytes;
      isChanged = true;
    }

    if (!isChanged) {
      patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
    }

    patch.complete();
  }

  @Override
  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    DiskState template = (DiskState) td;

    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);

    template.id = UUID.randomUUID().toString();
    template.type = DiskType.SSD;
    template.status = DiskStatus.DETACHED;
    template.capacityMBytes = 2 ^ 32L;
    template.name = "disk01";

    return template;
  }
}
