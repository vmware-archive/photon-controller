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

package com.vmware.photon.controller.api.frontend.entities;

/**
 * Storage Types.
 * <p/>
 * <p/>
 * <code>VMFS</code> - VMware File System (ESX Server only).
 * <br/>
 * <code>NFS</code> - Network file system v3 and below (Linux and ESX Server only).
 * <br/>
 * <code>NFSV41</code> - Network file system version v4.1 or later (Linux only and ESX Server only).
 * <br/>
 * <code>CIFS</code> - Common Internet file system (Windows only).
 * <br/>
 * <code>VFAT</code> - Virtual FAT (ESX Server only).
 * <br/>
 * <code>VFFS</code> - vFlash File System (ESX Server only).
 * <br/>
 * <code>VSAN</code> - VSAN (ESX Server only).
 */
public enum StorageType {
  VMFS,
  NFS,
  NFSV41,
  CIFS,
  VFAT,
  VFFS,
  VSAN
}
