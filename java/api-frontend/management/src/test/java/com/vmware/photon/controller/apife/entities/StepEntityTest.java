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

package com.vmware.photon.controller.apife.entities;

import com.vmware.photon.controller.api.common.exceptions.external.ErrorCode;
import com.vmware.photon.controller.api.model.EphemeralDisk;
import com.vmware.photon.controller.api.model.PersistentDisk;
import com.vmware.photon.controller.api.model.Vm;
import com.vmware.photon.controller.apife.exceptions.external.InvalidVmStateException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link StepEntity}.
 */
public class StepEntityTest {

  @Test
  public void testTransientResource() {
    StepEntity step = new StepEntity();
    assertThat(step.getTransientResource("foo"), is(nullValue()));

    step.createOrUpdateTransientResource("foo", "bar");
    assertThat((String) step.getTransientResource("foo"), is("bar"));

    step.createOrUpdateTransientResource("foo", "foobar");
    assertThat((String) step.getTransientResource("foo"), is("foobar"));
  }

  @Test
  public void testResourceEntities() {
    StepEntity step = new StepEntity();
    assertThat(step.getTransientResourceEntities().isEmpty(), is(true));

    step.addTransientResourceEntity(new VmEntity());
    step.addTransientResourceEntity(new PersistentDiskEntity());
    step.addTransientResourceEntity(new EphemeralDiskEntity());

    assertThat(step.getTransientResourceEntities().size(), is(3));
    assertThat(step.getTransientResourceEntities(Vm.KIND).size(), is(1));
    assertThat(step.getTransientResourceEntities(PersistentDisk.KIND).size(), is(1));
    assertThat(step.getTransientResourceEntities(EphemeralDisk.KIND).size(), is(1));
  }

  @Test
  public void testAddException() {
    String errorMsg = "Invalid VM State";
    InvalidVmStateException ex = new InvalidVmStateException(errorMsg);

    StepEntity step = new StepEntity();
    step.addException(ex);
    step.addException(new RpcException());

    assertThat(step.getErrors().size(), is(2));

    StepErrorEntity firstError = step.getErrors().get(0);
    assertThat(firstError.getCode(), is(ErrorCode.INVALID_VM_STATE.getCode()));
    assertThat(firstError.getMessage(), is(errorMsg));

    StepErrorEntity secondError = step.getErrors().get(1);
    assertThat(secondError.getCode(), is(ErrorCode.INTERNAL_ERROR.getCode()));
    assertThat(secondError.getMessage(), is("Please contact the system administrator about request #null"));
  }

  @Test
  public void testAddWarning() {
    String errorMsg = "Invalid VM State";
    InvalidVmStateException ex = new InvalidVmStateException(errorMsg);

    StepEntity step = new StepEntity();
    step.addWarning(ex);
    step.addWarning(new RpcException());

    assertThat(step.getWarnings().size(), is(2));

    StepWarningEntity firstError = step.getWarnings().get(0);
    assertThat(firstError.getCode(), is(ErrorCode.INVALID_VM_STATE.getCode()));
    assertThat(firstError.getMessage(), is(errorMsg));

    StepWarningEntity secondError = step.getWarnings().get(1);
    assertThat(secondError.getCode(), is(ErrorCode.INTERNAL_ERROR.getCode()));
    assertThat(secondError.getMessage(), is("Please contact the system administrator about request #null"));
  }
}
