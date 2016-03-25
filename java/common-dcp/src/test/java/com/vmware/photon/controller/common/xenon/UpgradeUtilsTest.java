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

import com.vmware.photon.controller.common.xenon.upgrade.UpgradeUtils;
import com.vmware.photon.controller.common.xenon.validation.DefaultBoolean;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultTaskState;
import com.vmware.photon.controller.common.xenon.validation.DefaultUuid;
import com.vmware.photon.controller.common.xenon.validation.RenamedField;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Test {@link UpgradeUtils} class.
 */
public class UpgradeUtilsTest {

  @Test
  public void successWithRenamedFieldPresent() {
    Document source = new Document(true, 1, "uuid123", new TaskState());
    Document2 destination = new Document2();

    UpgradeUtils.handleRenamedField(Utils.toJson(source), destination);
    assertThat(destination.guid.equals(source.uuid), is(true));
    assertThat(destination.aBoolean.equals(source.bool), is(true));
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

  /**
   * Test ServiceDocument.
   */
  public class Document2 extends ServiceDocument {
    @RenamedField(originalName = "uuid")
    public String guid;

    @RenamedField(originalName = "bool")
    public Boolean aBoolean;
  }
}
