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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.common.clients.exceptions.ReplicationFailedException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ServiceUnavailableException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResponse;
import com.vmware.photon.controller.housekeeper.gen.RemoveImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResponse;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResult;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageResultCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatus;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusCode;
import com.vmware.photon.controller.housekeeper.gen.ReplicateImageStatusResponse;
import com.vmware.photon.controller.resource.gen.ImageReplication;

import org.apache.thrift.TException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link HousekeeperClient}.
 */
public class HousekeeperClientTest {
  private static final String DATASTORE = "datastore";
  private static final String IMAGE = "image";
  private static final String OPERATION_ID = "opid";
  private static final ImageReplication REPLICATION_TYPE = ImageReplication.EAGER;

  private HousekeeperClient client;

  @BeforeMethod
  public void setUp() throws IOException, InterruptedException {
    HousekeeperClientConfig config = new HousekeeperClientConfig();
    config.setImageReplicationTimeout((int) TimeUnit.SECONDS.toMillis(5));

    client = spy(new HousekeeperClient(null, config));
  }

  @Test
  public void testSuccessReplicate() throws InterruptedException, RpcException, TException {
    // Trigger replication.
    ReplicateImageResponse triggerResponse = new ReplicateImageResponse(new ReplicateImageResult
        (ReplicateImageResultCode.OK));
    triggerResponse.setOperation_id(OPERATION_ID);
    doReturn(triggerResponse).when(client).triggerReplication(anyString(), anyString(), any());

    // Replication status.
    ReplicateImageStatusResponse statusResponse = new ReplicateImageStatusResponse(new ReplicateImageResult
        (ReplicateImageResultCode.OK));
    statusResponse.setStatus(new ReplicateImageStatus(ReplicateImageStatusCode.FINISHED));
    doReturn(statusResponse).when(client).getReplicationStatusNoCheck(OPERATION_ID);

    //Replicate.
    client.replicateImage(DATASTORE, IMAGE, REPLICATION_TYPE);
    verify(client).triggerReplication(DATASTORE, IMAGE, REPLICATION_TYPE);
  }

  @Test(expectedExceptions = SystemErrorException.class)
  protected void failTriggerWithSystemError() throws InterruptedException, RpcException, TException {
    // Trigger replication.
    ReplicateImageResult systemResult = new ReplicateImageResult(ReplicateImageResultCode.SYSTEM_ERROR);
    systemResult.setError("some error");
    doThrow(new SystemErrorException("")).when(client).triggerReplication(anyString(), anyString(), any());

    //Replicate.
    client.replicateImage(DATASTORE, IMAGE, REPLICATION_TYPE);
    fail("should fail with system error while triggering replication");
  }

  @Test(expectedExceptions = SystemErrorException.class)
  protected void failStatusWithSystemError() throws InterruptedException, RpcException, TException {
    // Trigger replication.
    ReplicateImageResult systemResult = new ReplicateImageResult(ReplicateImageResultCode.SYSTEM_ERROR);
    systemResult.setError("some error");
    ReplicateImageResponse triggerResponse = new ReplicateImageResponse(systemResult);
    triggerResponse.setOperation_id(OPERATION_ID);
    doReturn(triggerResponse).when(client).triggerReplication(anyString(), anyString(), any());

    // Replicate.
    client.replicateImage(DATASTORE, IMAGE, REPLICATION_TYPE);
    fail("should fail with system error while getting replication status");
  }

  @Test(expectedExceptions = ServiceUnavailableException.class)
  protected void failStatusWithServiceUnavailableError() throws Throwable {
    // Trigger replication.
    ReplicateImageResult result = new ReplicateImageResult(ReplicateImageResultCode.SERVICE_NOT_FOUND);
    result.setError("ImageReplicatorService is unavailable");
    ReplicateImageResponse triggerResponse = new ReplicateImageResponse(result);
    doReturn(triggerResponse).when(client).triggerReplication(anyString(), anyString(), any());

    // Replicate.
    client.replicateImage(DATASTORE, IMAGE, REPLICATION_TYPE);
    fail("should fail with service unavailable while getting replication status");
  }

  @Test
  public void testRemoveNullImage() throws Throwable {
    try {
      client.removeImage(null);
      fail("Remove null image should fail");
    } catch (NullPointerException e) {
      assertThat(e.getMessage(), is("image is null"));
    }
  }

  @Test
  public void testRemoveImageSuccess() throws Throwable {
    HousekeeperClientMock client = new HousekeeperClientMock(null);
    client.setRemoveImageResultCode(RemoveImageResultCode.OK);
    RemoveImageResponse response = client.removeImage(IMAGE);

    MatcherAssert.assertThat(response.getResult().getCode(), Is.is(RemoveImageResultCode.OK));
    MatcherAssert.assertThat(response.getResult().getError(), nullValue());
  }

  @Test
  public void testRemoveImageError() throws Throwable {
    HousekeeperClientMock client = new HousekeeperClientMock(null);
    client.setRemoveImageResultCode(RemoveImageResultCode.SYSTEM_ERROR);
    try {
      client.removeImage(IMAGE);
      fail("removeImage call should fail");
    } catch (SystemErrorException e) {
      assertThat(e.getMessage(), is("SystemError"));
    }
  }

}
