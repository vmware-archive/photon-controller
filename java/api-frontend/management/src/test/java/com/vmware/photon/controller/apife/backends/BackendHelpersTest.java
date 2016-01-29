/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.apife.backends;

import com.vmware.photon.controller.api.ImageReplicationType;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.apife.backends.clients.ApiFeDcpRestClient;
import com.vmware.photon.controller.cloudstore.dcp.CloudStoreDcpHost;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageReplicationService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageReplicationServiceFactory;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageService;
import com.vmware.photon.controller.cloudstore.dcp.entity.ImageServiceFactory;
import com.vmware.photon.controller.common.dcp.BasicServiceHost;
import com.vmware.photon.controller.common.dcp.ServiceHostUtils;
import com.vmware.photon.controller.common.dcp.ServiceUtils;
import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.xenon.common.Operation;

import org.apache.commons.collections.CollectionUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Tests for {@link com.vmware.photon.controller.apife.backends.BackendHelpersTest}.
 */
public class BackendHelpersTest {

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for the methods that check the progress of image seeding.
   */
  public class ImageSeedingCheckerTest {

    private BasicServiceHost host;
    private ApiFeDcpRestClient dcpClient;

    @BeforeClass
    public void beforeClassSetup() throws Throwable {
      host = BasicServiceHost.create();
      ServiceHostUtils.startServices(host, CloudStoreDcpHost.FACTORY_SERVICES);

      StaticServerSet serverSet = new StaticServerSet(
          new InetSocketAddress(host.getPreferredAddress(), host.getPort()));
      dcpClient = new ApiFeDcpRestClient(serverSet, Executors.newFixedThreadPool(128));
      dcpClient.start();
    }

    @AfterMethod
    public void afterMethodCleanup() throws Throwable {
      if (host != null) {
        ServiceHostUtils.deleteAllDocuments(host, "test-host");
      }
    }

    @AfterClass
    public void afterClassCleanup() throws Throwable {
      if (dcpClient != null) {
        dcpClient.stop();
        dcpClient = null;
      }

      if (host != null) {
        host.destroy();
        host = null;
      }
    }

    @Test
    public void testImageSeedingFinished() throws Throwable {
      String imageId = createImageService(2, 2);
      boolean done = BackendHelpers.isImageSeedingDone(dcpClient, imageId);
      assertThat(done, is(true));
    }

    @Test
    public void testImageSeedingInProgress() throws Throwable {
      String imageId = createImageService(2, 3);

      List<String> imageDatastores = Arrays.asList(new String[]{"datastore1", "datastore2"});
      for (String datastoreId : imageDatastores) {
        createImageReplicationService(imageId, datastoreId);
      }

      // An unrelated imagestore, and it should not be returned by
      // getSeededImageDatastores
      createImageReplicationService("image2", "datastore3");

      boolean done = BackendHelpers.isImageSeedingDone(dcpClient, imageId);
      assertThat(done, is(false));

      List<String> candidateDatastores = BackendHelpers.getSeededImageDatastores(dcpClient, imageId);
      assertThat(CollectionUtils.isEqualCollection(imageDatastores, candidateDatastores), is(true));
    }

    private String createImageService(int numReplicatedImageStores, int numTotalImageStores) throws Throwable {
      ImageService.State state = new ImageService.State();
      state.totalImageDatastore = numTotalImageStores;
      state.replicatedImageDatastore = numReplicatedImageStores;
      state.totalDatastore = numTotalImageStores;
      state.replicatedDatastore = numReplicatedImageStores;
      state.name = "image1";
      state.replicationType = ImageReplicationType.EAGER;
      state.state = ImageState.READY;

      Operation op = dcpClient.post(ImageServiceFactory.SELF_LINK, state);
      return ServiceUtils.getIDFromDocumentSelfLink(op.getBody(ImageService.State.class).documentSelfLink);
    }

    private void createImageReplicationService(String imageId, String imageDatastoreId) {
      ImageReplicationService.State state = new ImageReplicationService.State();
      state.imageId = imageId;
      state.imageDatastoreId = imageDatastoreId;
      state.documentSelfLink = imageId + "-" + imageDatastoreId;

      dcpClient.post(ImageReplicationServiceFactory.SELF_LINK, state);
    }
  }
}
