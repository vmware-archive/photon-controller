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

package com.vmware.photon.controller.common.tests;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

/**
 * TestServiceIgnoresPosts implements a Xenon service class that no-ops all POST messages it receives. (i.e. no changes
 * are made to its internal state and SUCCESS is returned.
 */
public class TestServiceIgnoresPosts extends StatefulService {

  private Class<? extends ServiceDocument> stateType;

  public TestServiceIgnoresPosts(Class<? extends ServiceDocument> stateType) {
    super(stateType);
    this.stateType = stateType;
  }

  @Override
  public void handlePost(Operation post) {
    post.complete();
  }
}
