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

package com.vmware.photon.controller.api.frontend.lib.ova.ovf;

import com.vmware.photon.controller.api.frontend.exceptions.internal.InvalidOvfException;

import org.w3c.dom.Element;

import java.util.regex.Pattern;

/**
 * Class encapsulating OVF metadata elements.
 */
public class OvfMetadata {

  /**
   * Class representing the OVF reference to an external file.
   */
  public static class FileReference extends OvfFile.OvfElement {

    // Hide constructor outside assembly.
    FileReference(OvfFile ovfFile, Element element) {
      super(ovfFile, element);
    }

    public String getFileName() {
      return element.getAttribute("ovf:href");
    }

    public String getId() {
      return element.getAttribute("ovf:id");
    }
  }

  /**
   * Class representing the OVF high level description of a VM disk.
   */
  public static class VirtualDisk extends OvfFile.OvfElement {

    // Hide constructor outside assembly.
    VirtualDisk(OvfFile ovfFile, Element element) {
      super(ovfFile, element);
    }

    public FileReference getFileReference() throws InvalidOvfException {
      String fileReferenceName = element.getAttribute("ovf:fileRef");
      for (FileReference fileReference : ovfFile.getFileReferences()) {
        if (fileReferenceName.equals(fileReference.getId())) {
          return fileReference;
        }
      }
      throw new InvalidOvfException("Disk file reference not found.");
    }

    public String getId() {
      return element.getAttribute("ovf:diskId");
    }

    public String getDiskHostResource() {
      return String.format("(ovf:)?/disk/%s", getId());
    }

    // Get disk device corresponding to a virtual disk.
    public Device getDiskDevice() throws InvalidOvfException {
      Pattern pattern = Pattern.compile(this.getDiskHostResource());
      for (Device item : ovfFile.getDevices()) {
        if (item.getDeviceType() == Device.DeviceType.DiskDrive &&
            pattern.matcher(item.getDiskHostResource()).matches()) {
          return item;
        }
      }
      throw new InvalidOvfException("Disk Device not found.");
    }
  }

  /**
   * Class representing a virtual network.
   */
  public static class VirtualNetwork extends OvfFile.OvfElement {

    // Hide constructor outside assembly.
    VirtualNetwork(OvfFile ovfFile, Element element) {
      super(ovfFile, element);
    }

    public String getName() {
      return element.getAttribute("ovf:name");
    }
  }
}
