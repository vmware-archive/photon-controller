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

import com.vmware.photon.controller.api.model.UsageTag;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Tests {@link DatastoreEntity}.
 */
public class DatastoreEntityTest {

  private DatastoreEntity datastoreEntity;

  @BeforeMethod
  public void setUp() {
    datastoreEntity = new DatastoreEntity();
  }

  @Test
  public void testDatastoreSuccessful() {

    datastoreEntity.setId("test id");
    datastoreEntity.setName("url-1");
    datastoreEntity.setHostIps("127.0.0.1");
    datastoreEntity.setTags(UsageTag.CLOUD.name());

    assertThat(datastoreEntity.getName(), is("url-1"));
    assertThat(datastoreEntity.getHostIps(), is("127.0.0.1"));
    assertThat(datastoreEntity.getTags(), is(UsageTag.CLOUD.name()));
    assertThat(datastoreEntity.getKind(), is(DatastoreEntity.KIND));
  }

  @Test
  public void testHashCodeEquals() {
    datastoreEntity.setId("test id");
    datastoreEntity.setName("url-1");
    datastoreEntity.setHostIps("127.0.0.1");
    datastoreEntity.setTags(UsageTag.CLOUD.name());

    DatastoreEntity anotherEntity = new DatastoreEntity();
    anotherEntity.setId("test id");
    anotherEntity.setName("url-1");
    anotherEntity.setHostIps("127.0.0.1");
    anotherEntity.setTags(UsageTag.CLOUD.name());

    assertThat(datastoreEntity.hashCode(), is(anotherEntity.hashCode()));
    assertThat(datastoreEntity, not(sameInstance(anotherEntity)));
    assertThat(datastoreEntity, is(anotherEntity));

    anotherEntity.setId("bad id");
    assertThat(datastoreEntity, not(equalTo(anotherEntity)));
    assertThat(datastoreEntity.hashCode(), not(equalTo(anotherEntity.hashCode())));
    anotherEntity.setId("test id");
    assertThat(datastoreEntity, is(anotherEntity));
    assertThat(datastoreEntity.hashCode(), is(anotherEntity.hashCode()));

    anotherEntity.setName("url-2");
    assertThat(datastoreEntity, not(equalTo(anotherEntity)));
    assertThat(datastoreEntity.hashCode(), not(equalTo(anotherEntity.hashCode())));
    anotherEntity.setName("url-1");
    assertThat(datastoreEntity, is(anotherEntity));
    assertThat(datastoreEntity.hashCode(), is(anotherEntity.hashCode()));

    anotherEntity.setHostIps("10.0.0.1");
    assertThat(datastoreEntity, not(equalTo(anotherEntity)));
    assertThat(datastoreEntity.hashCode(), not(equalTo(anotherEntity.hashCode())));
    anotherEntity.setHostIps("127.0.0.1");
    assertThat(datastoreEntity, is(anotherEntity));
    assertThat(datastoreEntity.hashCode(), is(anotherEntity.hashCode()));

    anotherEntity.setTags(UsageTag.IMAGE.name());
    assertThat(datastoreEntity, not(equalTo(anotherEntity)));
    assertThat(datastoreEntity.hashCode(), not(equalTo(anotherEntity.hashCode())));
    anotherEntity.setTags(UsageTag.CLOUD.name());
    assertThat(datastoreEntity, is(anotherEntity));
    assertThat(datastoreEntity.hashCode(), is(anotherEntity.hashCode()));

  }
}
