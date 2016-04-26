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

package com.vmware.photon.controller.apife.backends.clients;

import com.vmware.photon.controller.apife.BackendTaskExecutor;
import com.vmware.photon.controller.apife.RootSchedulerServerSet;
import com.vmware.photon.controller.common.thrift.ServerSet;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.BadRequestException;
import com.vmware.photon.controller.common.xenon.exceptions.DocumentNotFoundException;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

/**
 * HTTP REST client to communicate with Xenon.
 * This class allows for injection of RootSchedulerServerSet and executor specific to API-FE.
 */
@Singleton
public class SchedulerXenonRestClient extends XenonRestClient {
  private static final Logger logger = LoggerFactory.getLogger(SchedulerXenonRestClient.class);

  @Inject
  public SchedulerXenonRestClient(@RootSchedulerServerSet ServerSet serverSet,
                                  @BackendTaskExecutor ExecutorService executor) throws URISyntaxException {
    super(serverSet, executor);
    this.start();
  }

  @Override
  protected int getPort(InetSocketAddress inetSocketAddress) {
    // Calculate Xenon port from Thrift port, Thrift endpoint is still used to communicate with
    // the status checker in apife and deployer. This will be removed when the scheduler
    // thrift endpoint is removed.
    return inetSocketAddress.getPort() + 1;
  }

  @Override
  public Operation post(String serviceSelfLink, ServiceDocument body) {

    try {
      return super.post(serviceSelfLink, body);
    } catch (DocumentNotFoundException documentNotFoundException) {
      throw new XenonRuntimeException(documentNotFoundException);
    } catch (BadRequestException badRequestException) {
      throw new XenonRuntimeException(badRequestException);
    } catch (TimeoutException timeoutException) {
      throw new RuntimeException(timeoutException);
    } catch (InterruptedException interruptedException) {
      throw new RuntimeException(interruptedException);
    }
  }
}
