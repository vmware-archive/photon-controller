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

package com.vmware.photon.controller.api.frontend.lib;

import com.vmware.transfer.nfc.HostServiceTicket;
import com.vmware.transfer.nfc.NfcClient;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class stores iso in vSphere datastore.
 */
public class VsphereIsoStore {
  private static final Logger logger = LoggerFactory.getLogger(VsphereImageStore.class);
  // Nfc client timeout in millisecond
  private static final int NFC_CLIENT_TIMEOUT = 10000;
  // Reserve 1G Byte space in datastore
  private static final int RESERVED_DS_SPACE = 1 << 30;
  private HostServiceTicket hostServiceTicket;
  private String datastoreName;
  private String vmId;

  /**
   * Set the store target.
   *
   * @param hostServiceTicket
   * @param vmName
   * @param datastoreName
   */
  public void setTarget(HostServiceTicket hostServiceTicket, String vmName, String datastoreName) {
    this.hostServiceTicket = hostServiceTicket;
    this.vmId = vmName;
    this.datastoreName = datastoreName;
  }


  /**
   * Create an image folder.
   *
   * @param isoDatastorePath
   * @param inputStream
   * @return
   */
  public long uploadIsoFile(String isoDatastorePath, InputStream inputStream) {
    NfcClient nfcClient = getNfcClient(hostServiceTicket);
    return uploadFile(nfcClient, isoDatastorePath, inputStream);
  }

  public String getDatastore() {
    return checkNotNull(datastoreName);
  }

  private NfcClient getNfcClient(HostServiceTicket ticket) throws RuntimeException {
    try {
      return new NfcClient(ticket, NFC_CLIENT_TIMEOUT);
    } catch (IOException e) {
      logger.error("Failed to create nfc client", e);
      throw new RuntimeException(e);
    }
  }

  private long uploadFile(NfcClient nfcClient, String filePath, InputStream inputStream) {
    logger.info("write to {}", filePath);
    OutputStream outputStream = null;
    long size = 0;
    try {
      outputStream = nfcClient.putFileAutoClose(filePath, RESERVED_DS_SPACE);
      IOUtils.copyLarge(inputStream, outputStream);
    } catch (IOException e) {
      logger.error("Failed to upload ISO file", e);
      throw new RuntimeException(e);
    } finally {
      try {
        inputStream.close();
        outputStream.close();
      } catch (IOException e) {
        logger.warn("Unable to close streaming for iso '{}' after {}: ",
            filePath, Long.toString(size), e);
        throw new RuntimeException(e);
      }
    }

    return size;
  }
}
