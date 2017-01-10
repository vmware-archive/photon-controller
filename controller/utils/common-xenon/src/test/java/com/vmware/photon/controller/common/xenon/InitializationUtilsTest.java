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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.DefaultUuid;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

/**
 * This class implements tests for {@link ValidationUtils}.
 */
public class InitializationUtilsTest {

  @Test
  public void initializesValues() {
    Document doc = new Document(null, null, null, null);
    InitializationUtils.initialize(doc);

    assertThat(doc.bool, is(false));
    assertThat(doc.integer, is(1));
    assertThat(doc.state.stage, is(TaskState.TaskStage.CREATED));
    assertThat(doc.uuid, is(notNullValue()));
  }

  @Test
  public void doesNotOverrideNonNullValues() {
    Document doc = new Document(true, 2, "uuid2", new TaskState());
    InitializationUtils.initialize(doc);

    assertThat(doc.bool, is(true));
    assertThat(doc.integer, is(2));
    assertThat(doc.state.stage, is(nullValue()));
    assertThat(doc.uuid, is("uuid2"));
  }

  /**
   * Test service document.
   */
  public static class Document extends ServiceDocument {
    @DefaultBoolean(value = false)
    public Boolean bool;
    @DefaultInteger(value = 1)
    public Integer integer;
    @DefaultUuid
    public String uuid;
    @DefaultTaskState(value = TaskState.TaskStage.CREATED)
    public TaskState state;

    Document(Boolean bool, Integer integer, String uuid, TaskState state) {
      this.bool = bool;
      this.integer = integer;
      this.uuid = uuid;
      this.state = state;
    }
  }
}
