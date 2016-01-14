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

package com.vmware.photon.controller.apife.lib;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.config.ImageConfig;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmStateException;
import com.vmware.photon.controller.apife.exceptions.internal.CreateUploadFolderException;
import com.vmware.photon.controller.apife.exceptions.internal.DeleteUploadFolderException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.HostClient;
import com.vmware.photon.controller.common.clients.HostClientFactory;
import com.vmware.photon.controller.common.clients.exceptions.*;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;
import com.vmware.transfer.nfc.HostServiceTicket;
import com.vmware.transfer.nfc.NfcClient;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;

/**
 * This class saves image in vSphere datastore. It could connect to vCenter or ESX server, and save/delete image in
 * the datastore.
 */
public class VsphereImageStore implements ImageStore {
  // Nfc client timeout in millisecond
  public static final int NFC_CLIENT_TIMEOUT = 0;
  private static final Logger logger = LoggerFactory.getLogger(VsphereImageStore.class);
  private static final String TMP_IMAGE_UPLOADS_FOLDER = "tmp_uploads";

  private final HostClientFactory hostClientFactory;
  private final ImageConfig config;

  /**
   * Constructor.
   *
   * @param hostClientFactory
   * @param config
   */
  public VsphereImageStore(HostClientFactory hostClientFactory, ImageConfig config) {
    this.hostClientFactory = hostClientFactory;
    this.config = config;
  }

  /**
   * Create an image folder.
   *
   * @param imageId
   * @return
   * @throws InternalException
   */
  @Override
  public Image createImage(String imageId) throws InternalException {
    String imageFolder = dsImageFolder(imageId);
    logger.info("create upload folder {} on datastore {}", imageFolder, this.getDatastore());
    try {
      getHostClient().createDirectory(imageFolder, this.getDatastore());
    } catch (DirectoryAlreadyExistsException e) {
      logger.info("Directory {} already exists on datastore {}. ", imageFolder, this.getDatastore(), e);
    } catch (InterruptedException | RpcException e) {
      logger.warn("Creating upload folder {} failed.", imageFolder, e);
      throw new CreateUploadFolderException(
              String.format("Failed to create upload folder %s on datastore %s.", imageFolder, this.getDatastore()), e);
    }

    final HostServiceTicket hostServiceTicket = getHostServiceTicket();
    NfcClient nfcClient = getNfcClient(hostServiceTicket);
    return new VsphereImageStoreImage(nfcClient, imageFolder, imageId);
  }

  /**
   * Call agent to move uploaded image from tmp_uploads to
   * where image is stored for system to use.
   *
   * @param imageId
   */
  @Override
  public void finalizeImage(String imageId) throws InternalException {
    String tmpImagePath = String.format("%s/%s", TMP_IMAGE_UPLOADS_FOLDER, imageId);
    logger.info("Calling createImage {} on {} {}", imageId, this.getDatastore(), tmpImagePath);
    try {
      getHostClient().createImage(imageId, this.getDatastore(), tmpImagePath);
    } catch (RpcException | InterruptedException e) {
      String errorMsg = String.format("Failed to call HostClient create_image %s on %s %s",
          imageId, this.getDatastore(), tmpImagePath);
      throw new InternalException(errorMsg, e);
    }
  }

  /**
   * Create image by cloning vm.
   *
   * @param imageId
   * @param vmId
   * @param hostIp
   * @throws InternalException
   */
  @Override
  public void createImageFromVm(String imageId, String vmId, String hostIp)
      throws ExternalException, InternalException {
    String tmpImagePath = String.format("%s/%s", TMP_IMAGE_UPLOADS_FOLDER, imageId);
    logger.info("Calling createImage {} on {} {}", imageId, this.getDatastore(), tmpImagePath);
    try {
      getHostClient(hostIp).createImageFromVm(vmId, imageId, this.getDatastore(), tmpImagePath);
    } catch (InvalidVmPowerStateException e) {
      throw new InvalidVmStateException(e);
    } catch (RpcException | InterruptedException e) {
      logger.warn("Unexpected error for create_image {} from vm {} on {} {}",
          imageId, vmId, this.getDatastore(), tmpImagePath, e);
      throw new InternalException(e);
    }
  }

  /**
   * Delete an image folder.
   *
   * @param imageId
   * @throws InternalException
   */
  @Override
  public void deleteImage(String imageId) throws InternalException {
    logger.info("delete image {} on datastore {}", imageId, this.getDatastore());
    try {
      getHostClient().deleteImage(imageId, this.getDatastore());
    } catch (ImageInUseException | ImageNotFoundException e) {
      // Ignore error, image was marked for deletion or did not exist.
      // Agent will not use the image for new VMs.
      logger.info("DeleteImage {} on {} failed.", imageId, this.getDatastore(), e);
    } catch (InterruptedException | RpcException e) {
      throw new InternalException(
          String.format("Failed to delete image %s on datastore %s", imageId, this.getDatastore()), e);
    }
  }

  @Override
  public void deleteUploadFolder(String imageId) throws DeleteUploadFolderException {
    String imageFolder = dsImageFolder(imageId);
    logger.info("delete upload folder {} on datastore {}", imageFolder, this.getDatastore());
    try {
      getHostClient().deleteDirectory(imageFolder, this.getDatastore());
    } catch (DirectoryNotFoundException e) {
      logger.info("Directory {} not found on datastore {}. Nothing to delete.", imageFolder, this.getDatastore(), e);
    } catch (InterruptedException | RpcException e) {
      logger.warn("Deleting upload folder {} failed.", imageFolder, e);
      throw new DeleteUploadFolderException(
          String.format("Failed to delete upload folder %s on datastore %s.", imageFolder, this.getDatastore()), e);
    }
  }

  @Override
  public boolean isReplicationNeeded() {
    return true;
  }

  @Override
  public String getDatastore() {
    return this.config.getDatastore();
  }

  @VisibleForTesting
  protected NfcClient getNfcClient(HostServiceTicket ticket) throws InternalException {
    checkArgument(ticket != null, "Null ticket passed to VsphereImageStore.getNfcClient");

    try {
      return new NfcClient(ticket, NFC_CLIENT_TIMEOUT);
    } catch (IOException e) {
      logger.error("Failed to create nfc client, due to: {}", e);
      throw new InternalException(e);
    }
  }

  /**
   * Get host service ticket.
   *
   * @return
   * @throws InternalException
   */
  private HostServiceTicket getHostServiceTicket() throws InternalException {
    HostClient hostClient = getHostClient();

    try {
      final ServiceTicketResponse nfcServiceTicketResponse = hostClient.getNfcServiceTicket(this.getDatastore());
      return NfcClientUtils.convertToNfcHostServiceTicket(
          nfcServiceTicketResponse.getTicket(), hostClient.getHostIp());
    } catch (InterruptedException | RpcException fault) {
      logger.error("Failed to get ticket from vC/ESX '{}'", hostClient.getHostIp(), fault);
      throw new InternalException(fault);
    }
  }

  /**
   * Image's datastore folder for an image. A typical datastore folder for image id 123456789 is:
   * [datastore1] tmp_uploads/123456789
   *
   * @param imageId image id
   * @return image
   */
  private String dsImageFolder(String imageId) {
    return String.format("[%s] %s/%s", this.getDatastore(), TMP_IMAGE_UPLOADS_FOLDER, imageId);
  }

  /**
   * Configured the HostClient according to the config.
   */
  private HostClient getHostClient(String hostIp) {
    HostClient hostClient = hostClientFactory.create();
    hostClient.setHostIp(hostIp);
    return hostClient;
  }

  private HostClient getHostClient() {
    return getHostClient(config.getEndpointHostAddress());
  }
}
