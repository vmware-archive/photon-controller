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

package com.vmware.photon.controller.common.zookeeper;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.framework.api.transaction.CuratorTransaction;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;

/**
 * Stores an arbitrary data in ZK using znode names relative to some base path as keys.
 * The intended use case is storing things like API configuration in ZK, where agents can pick it up
 * and establish watchers to subscribe to any changes. It tries to minimize the number of triggered
 * watches by only updating data if it changed.
 */
public class DataDictionary {

  private static final Logger logger = LoggerFactory.getLogger(DataDictionary.class);

  private final CuratorFramework zkClient;
  private final ExecutorService executor;
  private final String basePath;
  private final Set<String> knownKeys;
  private boolean init = false;

  @Inject
  public DataDictionary(CuratorFramework zkClient, ExecutorService executor, @Assisted String basePath) {
    this.zkClient = zkClient;
    this.basePath = basePath;
    this.executor = executor;

    knownKeys = new ConcurrentSkipListSet<>();
  }

  /**
   * Reads all keys under base path and subscribes to any updates to these keys if listener is provided.
   * Every update will trigger subscribeToUpdates (in a separate thread). Every watch is a one-time
   * event, so there is no need for herd protection (each outstanding watch will
   * trigger exactly one call to subscribeToUpdates).
   *
   * @throws Exception
   */
  public void subscribeToUpdates(final ChangeListener changeListener) throws Exception {
    checkNotNull(changeListener);

    CuratorWatcher watcher = new CuratorWatcher() {
      @Override
      public void process(final WatchedEvent event) throws Exception {
        executor.submit(new Runnable() {
          @Override
          public void run() {
            try {
              subscribeToUpdates(changeListener);
            } catch (Exception e) {
              logger.error("Unable to read all keys from data dictionary", e);
              throw new RuntimeException(e);
            }
          }
        });
      }
    };

    List<String> keys;

    try {
      keys = zkClient.getChildren().usingWatcher(watcher).forPath(ZKPaths.makePath(basePath, ""));
    } catch (KeeperException.NoNodeException e) {
      createIfNotExists(basePath, null);
      subscribeToUpdates(changeListener);

      return;
    }

    for (String knownKey : knownKeys) {
      if (!keys.contains(knownKey)) {
        if (knownKeys.remove(knownKey)) {
          changeListener.onKeyRemoved(knownKey);
        }
      }
    }

    for (String key : keys) {
      if (knownKeys.add(key)) {
        changeListener.onKeyAdded(key);
      }
    }
  }

  public int getCurrentVersion() {
    int version;
    Stat stat = new Stat();
    try {
      zkClient.getData().storingStatIn(stat).forPath(ZKPaths.makePath(basePath, ""));
      version = stat.getVersion();
    } catch (KeeperException.NoNodeException e) {
      version = 0;
    } catch (Exception e) {
      logger.error("Error retrieving dict version for {}", basePath, e);
      version = 0;
    }
    return version;
  }

  /**
   * This function retrieves all the dictionary's keys.
   *
   * @return A list of strings that represents the dictionary's keys
   */
  public List<String> getKeys() {
    List<String> keys = new ArrayList<>();
    String path = ZKPaths.makePath(basePath, "");
    try {
      Stat stat = zkClient.checkExists().forPath(path);
      if (stat != null) {
        keys = zkClient.getChildren().forPath(ZKPaths.makePath(basePath, ""));
      } else {
        logger.debug("Can't retrive keys because {} doesn't exist in zk", path);
      }
    } catch (Exception e) {
      logger.error("An error occurred while try to retrieve keys for {}", basePath, e);
    }
    return keys;
  }

  /**
   * Performs the best effort of updating ZK node {basePath}/{key} to the provided value.
   * The semantics provided are as follows:
   * The value is written regardless of the current value associated with the key.
   * If the key doesn't exist and we lose the race to create the key, we will throw an exception.
   *
   * @param key      will be created under basePath
   * @param newValue is a byte array, it's up to caller to serialize the actual data (e.g. Thrift structure)
   * @throws java.lang.Exception pass-through from the curator library.
   */
  public void write(String key, byte[] newValue) throws Exception {
    Map<String, byte[]> entry = new HashMap<>();
    entry.put(key, newValue);
    write(entry);
  }


  /**
   * A non-versioned write of multiple keys in a single commit.
   *
   * @param newValues
   * @throws Exception
   */
  public void write(Map<String, byte[]> newValues) throws Exception {
    write(newValues, -1);
  }

  /**
   * A versioned write interface to update multiple key, value pairs in a single commit.
   * Keys set with a null value for the data will be deleted if they exist.
   *
   * @param newValues a map of keys under basePath to values.
   * @param version   version of base
   * @throws java.lang.Exception pass-through from the curator library.
   */
  public void write(Map<String, byte[]> newValues, int version) throws Exception {
    if (!init) {
      /* Implementing the path create as part of the transaction is non trivial.
       * Do this as a one-off.
       */
      checkAndCreateBasePath();
    }

    Preconditions.checkState(init, "base zk node path not created yet");
    CuratorTransaction transaction = zkClient.inTransaction();
    for (Map.Entry<String, byte[]> entry : newValues.entrySet()) {
      String path = ZKPaths.makePath(basePath, entry.getKey());
      Stat stat = zkClient.checkExists().forPath(path);
      if (stat != null && entry.getValue() == null) {
        transaction = transaction
            .delete()
            .forPath(path).and();
      } else if (stat != null) {
        transaction = transaction
            .setData()
            .forPath(path, entry.getValue()).and();
      } else if (entry.getValue() != null) {
        transaction = transaction
            .create()
            .withMode(CreateMode.PERSISTENT)
            .forPath(path, entry.getValue()).and();
      } else {
        //Deleting a key that doesn't exist results in a no-op
      }
    }

    // Write an empty string to base in order to increment
    // its version
    transaction = transaction
        .setData().withVersion(version).forPath(ZKPaths.makePath(basePath, ""), "".getBytes()).and();


    if (transaction instanceof CuratorTransactionFinal) {
      ((CuratorTransactionFinal) transaction).commit();
    }
  }

  /**
   * Write interface to set multiple keys in a single commit.
   *
   * @param newValues the list of keys to update under basePath.
   * @throws java.lang.Exception pass-through from the curator library.
   */
  public void write(List<String> newValues) throws Exception {
    Map<String, byte[]> map = new HashMap<>();
    for (String entry : newValues) {
      map.put(entry, "".getBytes());
    }
    write(map);
  }

  /**
   * Reads and returns ZK node {basePath}/{key} value, optionally setting up a one-time callback for the data change.
   *
   * @param key             Key to read
   * @param changeListener  Callback that gets called when node data has changed. Will only be called once
   * @param notifyWhenAdded If true, watch will be set if the node is missing and fire once it's created
   * @return Node data or null if key doesn't exist yet
   * @throws Exception
   */
  public byte[] read(final String key, final ChangeListener changeListener, boolean notifyWhenAdded) throws Exception {
    final String nodePath = ZKPaths.makePath(basePath, key);

    CuratorWatcher watcher = new CuratorWatcher() {
      @Override
      public void process(final WatchedEvent event) throws Exception {
        executor.submit(new Runnable() {
          @Override
          public void run() {
            if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
              changeListener.onDataChanged(key);
            } else if (event.getType() == Watcher.Event.EventType.NodeDeleted) {
              changeListener.onKeyRemoved(key);
            } else if (event.getType() == Watcher.Event.EventType.NodeCreated) {
              changeListener.onKeyAdded(key);
            }
          }
        });
      }
    };

    try {
      if (changeListener != null) {
        return zkClient.getData().usingWatcher(watcher).forPath(nodePath);
      } else {
        return zkClient.getData().forPath(nodePath);
      }
    } catch (KeeperException.NoNodeException e) {
      if (changeListener != null && notifyWhenAdded) {
        zkClient.checkExists().usingWatcher(watcher).forPath(nodePath);
      }
      return null;
    }
  }

  /**
   * Read from the dictionary, set up a watch for data updates but do set up watch for later creation if the node is
   * missing.
   *
   * @param key            Key to read
   * @param changeListener Change listener to fire callbacks on
   * @return Node data or null if key doesn't exists
   * @throws Exception
   */
  public byte[] read(String key, ChangeListener changeListener) throws Exception {
    return read(key, changeListener, false);
  }

  /**
   * Read from the dictionary without setting up any watches.
   *
   * @param key Key to read
   * @return Node data or null if key doesn't exist
   * @throws Exception
   */
  public byte[] read(String key) throws Exception {
    return read(key, null, false);
  }

  private void createIfNotExists(String path, byte[] value) throws Exception {
    try {
      zkClient
          .create()
          .creatingParentsIfNeeded()
          .withMode(CreateMode.PERSISTENT)
          .forPath(ZKPaths.makePath(path, ""), value);
    } catch (KeeperException.NodeExistsException e) {
      throw new ConcurrentWriteException();
    }
  }

  private void checkAndCreateBasePath() throws Exception {
    Stat stat = zkClient.checkExists().forPath(ZKPaths.makePath(basePath, ""));
    if (stat == null) {
      try {
        zkClient
            .create()
            .creatingParentsIfNeeded()
            .withMode(CreateMode.PERSISTENT)
            .forPath(ZKPaths.makePath(basePath, ""));
      } catch (KeeperException.NodeExistsException e) {
        logger.debug("Node {} already exists", basePath);
      }
    }
    init = true;
  }

  /**
   * Data change callbacks need to implement this interface.
   */
  public interface ChangeListener {
    void onKeyAdded(String key);

    void onKeyRemoved(String key);

    void onDataChanged(String key);
  }
}
