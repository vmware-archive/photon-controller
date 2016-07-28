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

package com.vmware.photon.controller.api.frontend.commands;

import com.google.inject.Inject;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.powermock.modules.testng.PowerMockTestCase;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.HashMap;

/**
 * Tests {@link BaseCommand}.
 */
public class BaseCommandTest extends PowerMockTestCase {

  @Inject
  protected SessionFactory sessionFactory;

  @BeforeMethod
  public void setUp() throws Exception {
    Session session = sessionFactory.openSession();
    ManagedSessionContext.bind(session);
    session.beginTransaction();
    MDC.setContextMap(new HashMap<String, String>());
  }

  @AfterMethod
  public void tearDown() throws Exception {
    Session session = sessionFactory.getCurrentSession();
    session.getTransaction().rollback();
    ManagedSessionContext.unbind(sessionFactory);
  }

  protected void flushSession() {
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
  }
}
