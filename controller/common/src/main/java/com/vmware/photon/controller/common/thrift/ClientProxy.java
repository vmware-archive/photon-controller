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

package com.vmware.photon.controller.common.thrift;

import org.apache.thrift.async.TAsyncSSLClient;

/**
 * Dynamic proxy for async thrift interfaces. Uses a {@link ClientPool} to load balance and
 * discover services at the time of method invocation.
 * <p/>
 * The underlying client is only acquired for the duration of the call, so this is a relatively inexpensive object.
 * <p/>
 * It is thread-safe.
 *
 * @param <C> async thrift client
 */
public interface ClientProxy<C extends TAsyncSSLClient> {

  /**
   * @return proxy instance
   */
  C get();
}
