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

import com.google.common.base.Optional;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

import javax.persistence.Entity;

import java.io.Serializable;
import java.util.List;

/**
 * This DAO extends the AbstractDAO from dropwizard and adds more utility
 * methods on top of it.
 *
 *  @param <T> the class which this DAO manages.
 */
public class ExtendedAbstractDao<T> extends AbstractDAO<T> {
  protected final String findAll;
  protected final String findByName;

  public ExtendedAbstractDao(SessionFactory sessionFactory) {
    super(sessionFactory);

    String entityName = getEntityClassName();

    findAll = entityName + ".listAll";
    findByName = entityName + ".findByName";
  }

  public Optional<T> findById(Serializable id) {
    return Optional.fromNullable(get(id));
  }

  public Optional<T> findByName(String name) {
    T result = uniqueResult(namedQuery(findByName)
        .setString("name", name));
    return Optional.fromNullable(result);
  }

  public T create(T entity) {
    currentSession().save(entity);
    return entity;
  }

  public void update(T entity) {
    currentSession().update(entity);
  }

  public void delete(T entity) {
    currentSession().delete(entity);
  }

  public List<T> listByName(String name) {
    return list(namedQuery(findByName)
        .setString("name", name));
  }

  public List<T> listAll() {
    return list(namedQuery(findAll));
  }

  public T merge(T entity) {
    return (T) currentSession().merge(entity);
  }

  protected String getEntityClassName() {
    String entityName;
    Entity annotation = getEntityClass().getAnnotation(Entity.class);
    if (annotation != null && !annotation.name().isEmpty()) {
      entityName = annotation.name();
    } else {
      entityName = getEntityClass().getSimpleName();
    }
    return entityName;
  }
}
