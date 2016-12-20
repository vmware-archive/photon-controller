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

package com.vmware.photon.controller.api.frontend.lib.image;

import com.vmware.photon.controller.api.frontend.exceptions.external.InvalidOvaException;
import com.vmware.photon.controller.api.frontend.exceptions.external.UnsupportedDiskControllerException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InvalidOvfException;
import com.vmware.photon.controller.api.frontend.lib.ova.TarFileStreamReader;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.Device;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.OvfFile;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.OvfMetadata;

import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

/**
 * Class encapsulating and OVA file representing an ESX image.
 */
public class EsxOvaFile {
  private static final String OVF_EXTENSION = ".ovf";
  private TarFileStreamReader tarFileStreamReader;
  private OvfFile ovfFile;

  public EsxOvaFile(InputStream ova) throws InvalidOvaException, InvalidOvfException,
      UnsupportedDiskControllerException {
    tarFileStreamReader = new TarFileStreamReader(ova);

    // Find the OVF file.
    TarFileStreamReader.TarFile file = tarFileStreamReader.iterator().next();
    if (file == null || !file.name.endsWith(OVF_EXTENSION)) {
      throw new InvalidOvaException("Invalid OVA: OVF file not found.");
    }
    ovfFile = new OvfFile(file.content);
    validateOvf(ovfFile);
  }

  /**
   * Validate an OVF. Currently the validation is that:
   * The disks use a SCSI controller.
   */
  private static void validateOvf(OvfFile ovfFile) throws UnsupportedDiskControllerException, InvalidOvfException {
    for (OvfMetadata.VirtualDisk virtualDisk : ovfFile.getVirtualDisks()) {
      Device diskDevice = virtualDisk.getDiskDevice();
      Device diskController = diskDevice.getControllerDevice();
      if (diskController.getDeviceType() != Device.DeviceType.SCSIController) {
        throw new UnsupportedDiskControllerException(String.format("Unsupported disk controller: %s.",
            diskController.getName()));
      }
    }
  }

  /**
   * Return the OVF file embedded in the OVA.
   *
   * @return
   */
  public OvfFile getOvf() {
    return ovfFile;
  }

  /**
   * Return the next DISK files embedded in the OVA. The disk is found by looking for the TAR file component that has a
   * name beginning like a file reference for a VM disk reported in OVF. The beginning is used to allow multi-file vmdk.
   *
   * @return
   */
  public Iterable<InputStream> getDisks() throws InvalidOvfException {
    final List<OvfMetadata.FileReference> fileReferences = ovfFile.getFileReferences();
    return new Iterable<InputStream>() {
      @Override
      public Iterator<InputStream> iterator() {
        return new Iterator<InputStream>() {
          @Override
          public boolean hasNext() {
            return !fileReferences.isEmpty();
          }

          @Override
          public InputStream next() {
            while (true) {
              TarFileStreamReader.TarFile tarFile = tarFileStreamReader.iterator().next();
              if (tarFile == null) {
                throw new RuntimeException("Corrupt OVA: Not all OVF referenced files are present in the archive.");
              }
              // Find the 'name' part of the file archive file name.
              String[] parts = tarFile.name.split("/");
              String fileName = parts[parts.length - 1];

              // Check if it begins like a file reference (to accommodate vmdk parts).
              for (OvfMetadata.FileReference fileReference : fileReferences) {
                parts = fileReference.getFileName().split("/");
                String referenceFileName = parts[parts.length - 1];
                if (fileName.startsWith(referenceFileName)) {
                  fileReferences.remove(fileReference);
                  return tarFile.content;
                }
              }
            }
          }
        };
      }
    };
  }
}
