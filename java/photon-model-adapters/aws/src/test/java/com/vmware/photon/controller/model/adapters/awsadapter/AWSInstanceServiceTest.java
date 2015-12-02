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

package com.vmware.photon.controller.model.adapters.awsadapter;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.helpers.BaseModelTest;
import com.vmware.photon.controller.model.helpers.TestHost;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.services.common.AuthCredentialsFactoryService;
import com.vmware.xenon.services.common.AuthCredentialsService;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * This class implements tests for the {@link AWSInstanceService} class.
 */
public class AWSInstanceServiceTest {

  private static ServiceDocument createAuthCredentialsDocument(TestHost host, String privateKey, String privateKeyId)
      throws Throwable {
    AuthCredentialsService.AuthCredentialsServiceState creds = new AuthCredentialsService.AuthCredentialsServiceState();
    creds.privateKey = privateKey;
    creds.privateKeyId = privateKeyId;
    return host.postServiceSynchronously(AuthCredentialsFactoryService.SELF_LINK, creds,
        AuthCredentialsService.AuthCredentialsServiceState.class);
  }

  @Test
  private void dummy() {

  }

  /**
   * This class implements tests for the handleRequest method.
   */
  public class HandleRequestTest extends BaseModelTest {

    private String authCredentialsLink;

    @Override
    protected Class[] getFactoryServices() {
      return new Class[] {
        AWSInstanceService.class,
      };
    }

    @BeforeClass
    public void setUpClass() throws Throwable {
      super.setUpClass();
      String privateKey = "privateKey";
      String privateKeyId = "privateKeyID";

      //create credentials
      ServiceDocument awsCredentials = createAuthCredentialsDocument(host, privateKey, privateKeyId);
      authCredentialsLink = awsCredentials.documentSelfLink;
    }

    @Test
    public void testValidationSuccess() throws Throwable {
      ComputeInstanceRequest req = new ComputeInstanceRequest();
      req.requestType = ComputeInstanceRequest.InstanceRequestType.VALIDATE_CREDENTIALS;
      req.authCredentialsLink = authCredentialsLink;
      req.regionId = "us-east-1";
      req.isMockRequest = true;
      int statusCode = host.patchServiceSynchronously(AWSInstanceService.SELF_LINK, req);
      assertThat(statusCode, equalTo(200));
    }
  }
}
