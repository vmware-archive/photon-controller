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
import org.hibernate.LockOptions;
import org.hibernate.SessionFactory;

import java.io.Serializable;
import java.util.List;

/**
 * Base DAO for entities with a scoped parent.
 *
 * @param <T> entity type
 * @param <U> scoped parent entity type
 */
public abstract class BaseDao<T, U> extends ExtendedAbstractDao<T> {

  protected final String parentEntity;
  protected final String findAll;
  protected final String findByName;
  protected final String findByTag;

  public BaseDao(SessionFactory sessionFactory, String parentEntity) {
    super(sessionFactory);
    this.parentEntity = parentEntity;

    String entityName = getEntityClassName();

    findAll = entityName + ".findAll";
    findByName = entityName + ".findByName";
    findByTag = entityName + ".findByTag";
  }

  public void lock(T entity) {
    currentSession().buildLockRequest(LockOptions.UPGRADE).lock(entity);
  }

  public List<T> findAll(U parent) {
    return list(namedQuery(findAll).setEntity(parentEntity, parent));
  }

  public Optional<T> findByName(String name, U parent) {
    T result = uniqueResult(namedQuery(findByName)
        .setString("name", name)
        .setEntity(parentEntity, parent));
    return Optional.fromNullable(result);
  }

  public List<T> listByName(String name, U parent) {
    return list(namedQuery(findByName)
        .setString("name", name)
        .setEntity(parentEntity, parent));
  }

  public List<T> findByTag(String tag, U parent) {
    return list(namedQuery(findByTag)
        .setString("value", tag)
        .setEntity(parentEntity, parent));
  }

  /**
   * The methods loads an entity with an exclusive lock.
   * This is different from locking an already loaded entity.
   *
   * @param id
   * @return
   */
  public T loadWithUpgradeLock(Serializable id) {
    return (T) currentSession().load(getEntityClass(), id, LockOptions.UPGRADE);
  }
}
