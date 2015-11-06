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

package com.vmware.photon.controller.deployer.service.client;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.photon.controller.api.UsageTag;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostService;
import com.vmware.photon.controller.cloudstore.dcp.entity.HostServiceFactory;
import com.vmware.photon.controller.deployer.dcp.DeployerDcpServiceHost;
import com.vmware.photon.controller.deployer.gen.DeleteHostRequest;
import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.helpers.dcp.TestEnvironment;

import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * This class tests the {@link HostServiceClient}.
 */
public class HostServiceClientTest {

  @Test
  private void dummy() {
  }

  /**
   * This class tests the has method.
   */
  public class HasHostEntity {

    private HostServiceClient client;
    private TestEnvironment testEnvironment;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

    @BeforeMethod
    public void setUpTest() throws Throwable {
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      testEnvironment = new TestEnvironment.Builder().cloudServerSet(cloudStoreMachine.getServerSet())
          .hostCount(1).build();
      client = new HostServiceClient(testEnvironment.getHosts()[0], cloudStoreMachine.getServerSet());
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      cloudStoreMachine.stop();
      testEnvironment.stop();
    }

    @Test
    public void successHasWithSameAddress() throws Throwable {
      TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));

      Map<String, String> criteria = new HashMap<>();
      criteria.put(HostService.State.FIELD_NAME_HOST_ADDRESS, "hostAddress");

      assertThat(client.match(criteria, null), is(true));
    }

    @Test
    public void successHasWithoutSameAddress() throws Throwable {
      TestHelper.createHostService(cloudStoreMachine, Collections.singleton(UsageTag.MGMT.name()));

      Map<String, String> criteria = new HashMap<>();
      criteria.put(HostService.State.FIELD_NAME_HOST_ADDRESS, "otherHostAddress");

      assertThat(client.match(criteria, null), is(false));
    }

    @Test
    public void successHasWithoutHypervisorEntity() throws Throwable {
      Map<String, String> criteria = new HashMap<>();
      criteria.put(HostService.State.FIELD_NAME_HOST_ADDRESS, "hostAddress");

      assertThat(client.match(criteria, null), is(false));
    }
  }

  /**
   * This class tests the delete method.
   */
  public class DeleteHostEntity {

    private HostServiceClient target;
    private DeployerDcpServiceHost host;
    private com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment cloudStoreMachine;

    @BeforeMethod
    public void before() throws Throwable {
      host = mock(DeployerDcpServiceHost.class);
      cloudStoreMachine = com.vmware.photon.controller.cloudstore.dcp.helpers.TestEnvironment.create(1);
      target = new HostServiceClient(host, cloudStoreMachine.getServerSet());
    }

    @AfterMethod
    public void tearDown() throws Throwable {
      if (null != cloudStoreMachine) {
        cloudStoreMachine.stop();
        cloudStoreMachine = null;
      }
    }

    @Test
    public void successDelete() throws Throwable {
      DeleteHostRequest request = createDeleteHostRequest();

      setupMock(
          host,
          true,
          HostServiceFactory.SELF_LINK + "/" + request.getHost_id(),
          ServiceDocument.class,
          null,
          null);
      target.delete(request);
    }

    @Test(expectedExceptions = Exception.class)
    public void failsDeleteWhenDcpHostThrowsException() throws Throwable {
      DeleteHostRequest request = createDeleteHostRequest();

      setupMock(
          host,
          false,
          HostServiceFactory.SELF_LINK + "/" + request.getHost_id(),
          null,
          null,
          null);
      target.delete(request);
    }
  }

  private <T extends ServiceDocument> void setupMock(
      DeployerDcpServiceHost host,
      boolean isSuccess,
      final String operationUri,
      final Class<T> documentType,
      final Predicate<T> assertion,
      final T returnedDocument) {

    ArgumentMatcher<Operation> opMatcher = new ArgumentMatcher<Operation>() {
      @Override
      public boolean matches(Object argument) {
        Operation op = (Operation) argument;

        return op.getUri().toString().contains(operationUri);
      }
    };

    if (isSuccess) {
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          Operation op = (Operation) invocation.getArguments()[0];

          if (assertion != null && op.getBodyRaw() != null) {
            T document = op.getBody(documentType);
            assertion.test(document);
          }

          if (returnedDocument != null) {
            op.setBody(returnedDocument);
          }
          op.complete();
          return null;
        }
      }).when(host).sendRequest(argThat(opMatcher));
    } else {
      doThrow((new RuntimeException())).when(host).sendRequest(argThat(opMatcher));
    }
  }

  private DeleteHostRequest createDeleteHostRequest() {
    DeleteHostRequest request = new DeleteHostRequest();
    request.setHost_id("host-id");
    return request;
  }
}
