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

package com.vmware.photon.controller.api.frontend.clients.status;

import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.common.clients.StatusProvider;
import com.vmware.photon.controller.common.thrift.ServerSet;

import java.net.InetSocketAddress;

/**
 * Interface defining method to create StatusProvider.
 */
public interface StatusProviderFactory {

  ServerSet getServerSet();

  StatusProvider create(InetSocketAddress server) throws InternalException;
}
