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

package com.vmware.photon.controller.provisioner.xenon.entity;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.StatefulService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Used by Slingshot for imagelink and diskType.
 */
public class DiskService extends StatefulService {
  public DiskService() {
    super(DiskService.DiskState.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  public void handleStart(Operation start) {
    try {
      if (!start.hasBody()) {
        throw new IllegalArgumentException("body is required");
      }

      this.validateState((DiskService.DiskState) start.getBody(DiskService.DiskState.class));
      start.complete();
    } catch (Throwable var3) {
      start.fail(var3);
    }

  }

  private void validateState(DiskService.DiskState state) {
    if (state.id == null) {
      throw new IllegalArgumentException("id is required");
    } else if (state.name == null) {
      throw new IllegalArgumentException("name is required");
    } else if (state.type == null) {
      throw new IllegalArgumentException("type is required");
    } else if (state.capacityMBytes <= 1L && state.sourceImageReference == null && state
        .customizationServiceReference == null) {
      throw new IllegalArgumentException("capacityMBytes, sourceImageReference," +
          " or customizationServiceReference is required");
    } else {
      if (state.status == null) {
        state.status = DiskService.DiskStatus.DETACHED;
      }

      if (state.bootConfig != null) {
        DiskService.DiskState.BootConfig.FileEntry[] var2 = state.bootConfig.files;
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
          DiskService.DiskState.BootConfig.FileEntry entry = var2[var4];
          if (entry.path == null || entry.path.length() == 0) {
            throw new IllegalArgumentException("FileEntry.path is required");
          }
        }
      }

    }
  }

  public void handlePatch(Operation patch) {
    DiskService.DiskState currentState = (DiskService.DiskState) this.getState(patch);
    DiskService.DiskState patchBody = (DiskService.DiskState) patch.getBody(DiskService.DiskState.class);
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

    if (patchBody.capacityMBytes != 0L && patchBody.capacityMBytes != currentState.capacityMBytes) {
      currentState.capacityMBytes = patchBody.capacityMBytes;
      isChanged = true;
    }

    if (!isChanged) {
      patch.setStatusCode(304);
    }

    patch.complete();
  }

  public ServiceDocument getDocumentTemplate() {
    ServiceDocument td = super.getDocumentTemplate();
    DiskService.DiskState template = (DiskService.DiskState) td;
    ServiceDocumentDescription.expandTenantLinks(td.documentDescription);
    template.id = UUID.randomUUID().toString();
    template.type = DiskService.DiskType.SSD;
    template.status = DiskService.DiskStatus.DETACHED;
    template.capacityMBytes = 34L;
    template.name = "disk01";
    return template;
  }

  /**
   * Used only for sourceimagelink and diskType.
   */
  public static class DiskState extends ServiceDocument {
    public String id;
    public String zoneId;
    public String dataCenterId;
    public String resourcePoolLink;
    public String authCredentialsLink;
    public URI sourceImageReference;
    public DiskService.DiskType type;
    public String name;
    public DiskService.DiskStatus status;
    public long capacityMBytes;
    public Map<String, String> customProperties;
    public List<String> tenantLinks;
    public Integer bootOrder;
    public String[] bootArguments;
    public DiskService.DiskState.BootConfig bootConfig;
    public URI customizationServiceReference;
    public String currencyUnit;

    public DiskState() {
      this.status = DiskService.DiskStatus.DETACHED;
    }

    /**
     * BootConfig representation.
     */
    public static class BootConfig {
      public String label;
      public Map<String, String> data;
      public DiskService.DiskState.BootConfig.FileEntry[] files;

      public BootConfig() {
      }

      /**
       * File entry.
       */
      public static class FileEntry {
        public String path;
        public String contents;
        public URI contentsReference;

        public FileEntry() {
        }
      }
    }
  }

  /**
   * DiskType.
   */
  public static enum DiskType {
    SSD,
    HDD,
    CDROM,
    FLOPPY,
    NETWORK;

    private DiskType() {
    }
  }

  /**
   * Status.
   */
  public static enum DiskStatus {
    DETACHED,
    ATTACHED;

    private DiskStatus() {
    }
  }
}
