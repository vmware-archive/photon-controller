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

package com.vmware.photon.controller.apife.db.dao;

import com.vmware.photon.controller.api.DiskState;
import com.vmware.photon.controller.api.ImageState;
import com.vmware.photon.controller.api.VmState;

import com.google.inject.Inject;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * Tests {@link BaseDao}.
 */
public class BaseDaoTest extends PowerMockTestCase {

  protected static final VmState[] ALL_VM_STATES;
  protected static final DiskState[] ALL_DISK_STATES;
  protected static final ImageState[] ALL_IMAGE_STATES;

  static {
    int size = VmState.values().length + 1;
    ALL_VM_STATES = new VmState[size];
    System.arraycopy(VmState.values(), 0, ALL_VM_STATES, 0, VmState.values().length);

    size = DiskState.values().length + 1;
    ALL_DISK_STATES = new DiskState[size];
    System.arraycopy(DiskState.values(), 0, ALL_DISK_STATES, 0, DiskState.values().length);

    size = ImageState.values().length + 1;
    ALL_IMAGE_STATES = new ImageState[size];
    System.arraycopy(ImageState.values(), 0, ALL_IMAGE_STATES, 0, ImageState.values().length);
  }

  @Inject
  protected SessionFactory sessionFactory;

  @BeforeMethod
  public void setUp() throws Throwable {
    startSession();
  }

  @AfterMethod
  public void tearDown() throws Throwable {
    Session session = sessionFactory.getCurrentSession();
    session.getTransaction().rollback();
    ManagedSessionContext.unbind(sessionFactory);
  }

  protected void flushSession() {
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
  }

  protected void closeSession() {
    flushSession();
    sessionFactory.getCurrentSession().close();
  }

  protected void startSession() {
    Session session = sessionFactory.openSession();
    ManagedSessionContext.bind(session);
    session.beginTransaction();
  }
}
