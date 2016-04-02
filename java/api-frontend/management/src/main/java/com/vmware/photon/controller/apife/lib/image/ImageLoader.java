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

package com.vmware.photon.controller.apife.lib.image;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.apife.entities.ImageEntity;
import com.vmware.photon.controller.apife.exceptions.external.InvalidOvaException;
import com.vmware.photon.controller.apife.exceptions.external.NameTakenException;
import com.vmware.photon.controller.apife.exceptions.external.UnsupportedImageFileType;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.lib.Image;
import com.vmware.photon.controller.apife.lib.ImageStore;
import com.vmware.photon.controller.apife.lib.ova.DataField;
import com.vmware.photon.controller.apife.lib.ova.TarFileStreamReader;
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Class representing the loader of an Image file into the first ECX Cloud data store.
 */
public class ImageLoader {
  public static final String MANIFEST_FILE_SUFFIX = ".manifest";
  public static final String CONFIG_FILE_SUFFIX = ".ecv";
  public static final String DISK_FILE_SUFFIX = ".vmdk";
  private static final Logger logger = LoggerFactory.getLogger(ImageLoader.class);
  private static final DataField VMDK_SIGNATURE_FIELD = new DataField(0, 3);
  private static final String VMDK_FILE_SIGNATURE = "KDM";
  private static final ObjectMapper mapper = new ObjectMapper();
  private final ImageStore imageStore;

  public ImageLoader(ImageStore imageStore) throws InternalException {
    this.imageStore = imageStore;
  }

  /**
   * Create image by cloning vm.
   *
   * @param imageEntity
   * @param vmId
   * @param hostIp
   * @throws ExternalException
   * @throws InternalException
   * @throws IOException
   */
  public void createImageFromVm(ImageEntity imageEntity, String vmId, String hostIp)
      throws ExternalException, InternalException, IOException {
    checkArgument(StringUtils.isNotBlank(hostIp), "createImageFromVm: hostIp cannot be empty");
    logger.info("creating image {} by cloning from vm {} on host {}", imageEntity, vmId, hostIp);

    Image image = null;
    try {
      image = imageStore.createImage(imageEntity.getId());
      uploadManifestConfigurationFile(imageEntity, image);
      uploadECVFile(EsxCloudVmx.fromImageSettings(imageEntity.getImageSettingsMap()), image);

      image.close();
      imageStore.createImageFromVm(image, vmId, hostIp);

    } catch (Exception e) {
      if (image != null) {
        image.close();
        deleteUploadFolder(image);
      }
      throw e;
    }
  }

  /**
   * This routine reads the image stream and extracts the configuration and VMDK streams.
   *
   * @return
   */
  public Result uploadImage(ImageEntity imageEntity, InputStream inputStream)
      throws IOException, InternalException, VmdkFormatException, ExternalException {

    // Detect file type. Stream mark support is required.
    inputStream = new BufferedInputStream(inputStream);
    boolean isVmdkFile = isVmdkFile(inputStream);
    boolean isTarFile = isVmdkFile ? false : isTarFile(inputStream);

    // Check file type.
    if (!isVmdkFile && !isTarFile) {
      throw new UnsupportedImageFileType("Image must be either a vmdk or an ova file.");
    }

    Result result;
    // Generate configuration files.
    EsxCloudVmx ecv = null;
    EsxOvaFile esxOvaFile = null;
    if (isTarFile) {
      esxOvaFile = new EsxOvaFile(inputStream);
      ecv = EsxCloudVmxGenerator.generate(esxOvaFile.getOvf());
    }

    // Upload image in data store.
    result = new Result();
    Image image = null;
    try {
      image = imageStore.createImage(imageEntity.getId());

      // Upload MANIFEST configuration file.
      result.imageSize += uploadManifestConfigurationFile(imageEntity, image);

      if (isVmdkFile) {
        logger.info("Reading disk image from VMDK file.");
        result.imageSize += image.addDisk(DISK_FILE_SUFFIX, inputStream);
      } else {
        logger.info("Reading disk image from OVA file.");
        result.imageSize += loadImageFromOva(esxOvaFile, image, ecv);
        result.imageSettings.putAll(EsxCloudVmx.toImageSettings(ecv));
      }

      image.close();
      imageStore.finalizeImage(image);

    } catch (Exception e) {
      if (image != null) {
        image.close();
        deleteUploadFolder(image);
      }
      throw e;
    }

    return result;
  }

  private void deleteUploadFolder(Image image) {
    logger.info("Uploading image {} failed. Cleaning up partially uploaded files ...", image.getImageId());
    try {
      imageStore.deleteUploadFolder(image);
    } catch (InternalException e) {
      logger.warn("Did not clean up partially uploaded files. Moving out ...", e);
    }
  }

  /**
   * Peak into the stream for the VMDK signature.
   *
   * @param inputStream
   * @return
   * @throws IOException
   */
  private boolean isVmdkFile(InputStream inputStream) throws IOException {
    return VMDK_FILE_SIGNATURE.equals(DataField.getString(VMDK_SIGNATURE_FIELD, inputStream));
  }

  /**
   * Peak into the stream for the OVA signature.
   *
   * @param inputStream
   * @return
   * @throws IOException
   */
  private boolean isTarFile(InputStream inputStream) throws IOException {
    inputStream.mark(TarFileStreamReader.TAR_FILE_GRANULARITY);
    TarFileStreamReader tar = new TarFileStreamReader(inputStream);
    boolean isTar = tar.iterator().hasNext();
    inputStream.reset();
    return isTar;
  }

  private long loadImageFromOva(EsxOvaFile esxOvaFile,
                                Image image,
                                EsxCloudVmx ecv)
      throws InvalidOvaException, IOException, InternalException, VmdkFormatException, NameTakenException {

    long increasedImageSize = 0;

    // Upload ECV configuration file.
    increasedImageSize += uploadECVFile(ecv, image);

    // Upload disks.
    int dataDisk = 0;
    for (InputStream diskStream : esxOvaFile.getDisks()) {
      String diskFileSuffix = DISK_FILE_SUFFIX;
      if (dataDisk > 0) {
        diskFileSuffix = String.format("-data%d%s", dataDisk, diskFileSuffix);
      }
      increasedImageSize += image.addDisk(diskFileSuffix, diskStream);
      dataDisk += 1;
    }

    return increasedImageSize;
  }

  private long uploadManifestConfigurationFile(ImageEntity imageEntity, Image image)
      throws InternalException, IOException, NameTakenException {
    ImageManifest imageManifest = ImageManifestGenerator.generate(imageEntity);
    String manifestJson = generateJsonEncoding(imageManifest);

    return image.addFile(MANIFEST_FILE_SUFFIX, new ByteArrayInputStream(manifestJson.getBytes()),
        manifestJson.length());
  }

  private long uploadECVFile(EsxCloudVmx ecv, Image image)
      throws InternalException, IOException, NameTakenException {
    String ecvJson = generateJsonEncoding(ecv);
    return image.addFile(CONFIG_FILE_SUFFIX,
        new ByteArrayInputStream(ecvJson.getBytes()), ecvJson.length());
  }

  /**
   * Encode the EsxCloudVmx data as JSON.
   *
   * @return
   */
  private <T> String generateJsonEncoding(T t) throws InternalException {
    try {
      return mapper.writeValueAsString(t);
    } catch (JsonProcessingException e) {
      throw new InternalException("Failure to generate JSON.", e);
    }
  }

  /**
   * Result of an image (up)load operation.
   */
  public class Result {
    public long imageSize = 0;
    public Map<String, String> imageSettings = new HashMap<>();
  }
}
