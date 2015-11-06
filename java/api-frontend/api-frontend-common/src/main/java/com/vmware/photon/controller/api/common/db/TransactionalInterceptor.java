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

package com.vmware.photon.controller.api.common.db;

import com.vmware.photon.controller.common.metrics.DefaultMetricRegistry;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.codahale.metrics.MetricRegistry.name;

import javax.inject.Provider;

import java.util.concurrent.TimeUnit;

/**
 * Intercepts calls to methods marked by {@link Transactional} and wraps them with a Hibernate transaction.
 */
public class TransactionalInterceptor implements MethodInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(TransactionalInterceptor.class);

  private final Timer transactions;

  private final Meter transactionExceptions;

  @Inject
  private Provider<SessionFactory> sessionFactoryProvider;

  public TransactionalInterceptor() {
    transactions = DefaultMetricRegistry.REGISTRY.timer(name(TransactionalInterceptor.class, "transactions"));
    transactionExceptions = DefaultMetricRegistry.REGISTRY.meter(name(TransactionalInterceptor.class,
        "transaction-exceptions"));
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    SessionFactory sessionFactory = sessionFactoryProvider.get();

    Session session;
    if (ManagedSessionContext.hasBind(sessionFactory)) {
      session = sessionFactory.getCurrentSession();
    } else {
      session = sessionFactory.openSession();
      ManagedSessionContext.bind(session);
    }

    Transaction transaction = session.getTransaction();
    if (transaction.isActive()) {
      return invocation.proceed();
    }

    Stopwatch stopwatch = Stopwatch.createUnstarted();

    try {
      logger.trace("beginning transaction: {}", transaction);
      stopwatch.start();
      transaction.begin();
      Object result = invocation.proceed();
      transaction.commit();
      stopwatch.stop();
      logger.debug("committed: {}", transaction);
      return result;
    } catch (Throwable t) {
      logger.debug("rolling back: {}", transaction, t);
      transaction.rollback();
      transactionExceptions.mark();
      throw t;
    } finally {
      final long elapsedTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
      transactions.update(elapsedTime, TimeUnit.MILLISECONDS);
      ManagedSessionContext.unbind(sessionFactory);
      if (session.isOpen()) {
        session.close();
      }
      final long transactionTimeWarningThresholdInMilliseconds = 2000L;
      if (elapsedTime > transactionTimeWarningThresholdInMilliseconds) {
        logger.warn("Transaction {} took {} milliseconds", transaction, elapsedTime);
      }
    }
  }
}
