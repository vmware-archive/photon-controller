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

package com.vmware.photon.controller.deployer.xenon.util;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;

import org.testng.annotations.Test;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * This class implements tests for the {@link HostUtils} class.
 */
public class HostUtilsTest {

  /**
   * This test case enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the getContainersConfig method.
   */
  public class GetContainersConfigTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getContainersConfig(service);
    }
  }

  /**
   * This class implements tests for the getDeployerContext method.
   */
  public class GetDeployerContextTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getDeployerContext(service);
    }
  }

  /**
   * This class implements tests for the getDockerProvisionerFactory method.
   */
  public class GetDockerProvisionerFactoryTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getDockerProvisionerFactory(service);
    }
  }

  /**
   * This class implements tests for the getApiClient method.
   */
  public class GetApiClientTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getApiClient(service);
    }
  }

  /**
   * This class implements tests for the getApiClientFactory method.
   */
  public class GetApiClientFactoryTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getApiClientFactory(service);
    }
  }

  /**
   * This class implements tests for the getHealthCheckerHelperFactory method.
   */
  public class GetHealthCheckerHelperFactoryTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getHealthCheckHelperFactory(service);
    }
  }

  /**
   * This class implements tests for the getHostClient method.
   */
  public class GetHostClientTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getHostClient(service);
    }
  }

  /**
   * This class implements tests for the getHttpFileServiceClientFactory method.
   */
  public class GetHttpFileServiceClientFactoryTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getHttpFileServiceClientFactory(service);
    }
  }

  /**
   * This class implements tests for the getListeningExecutorService method.
   */
  public class GetListeningExecutorServiceTest {

    private StatefulService service;

    @Test(expectedExceptions = ClassCastException.class)
    public void testClassCastError() {
      service = spy(new StatefulService(ServiceDocument.class));
      doReturn(mock(ServiceHost.class)).when(service).getHost();
      HostUtils.getListeningExecutorService(service);
    }
  }
}
