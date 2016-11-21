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

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests {@link DataDictionary}.
 */
public class DataDictionaryTest extends BaseTestWithRealZookeeper {

  private ExecutorService executor = Executors.newCachedThreadPool();

  @Test
  public void testCreateAndRead() throws Throwable {
    zkClient.start();

    try {
      DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");

      dictionary.write("bar", "fff".getBytes());
      assertThat(new String(dictionary.read("bar")), is("fff"));

    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testReadDictVersion() throws Throwable {
    zkClient.start();

    try {
      DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");
      int version = dictionary.getCurrentVersion();
      assertThat(version, is(0));
      dictionary.write("key1", "data1".getBytes());
      version = dictionary.getCurrentVersion();
      assertThat(version, is(1));
      dictionary.write("key1", "data2".getBytes());
      version = dictionary.getCurrentVersion();
      assertThat(version, is(2));
    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testGetDictKeys() throws Throwable {
    zkClient.start();

    try {
      DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");
      List<String> keys = dictionary.getKeys();
      assertThat(keys.size(), is(0));
      dictionary.write("key1", "data1".getBytes());
      dictionary.write("key2", "data2".getBytes());
      keys = dictionary.getKeys();
      assertThat(keys.size(), is(2));
      assertThat(keys.contains("key1"), is(true));
      assertThat(keys.contains("key2"), is(true));
    } finally {
      zkClient.close();
    }
  }


  @Test
  public void testWriteAndDelete() throws Throwable {
    zkClient.start();

    try {
      DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");
      int version = dictionary.getCurrentVersion();
      assertThat(version, is(0));
      dictionary.write("key1", "data1".getBytes());
      dictionary.write("key2", "data2".getBytes());
      dictionary.write("key3", "data3".getBytes());
      assertThat(dictionary.getKeys().size(), is(3));
      dictionary.write("key1", null);
      dictionary.write("key2", null);
      assertThat(dictionary.getKeys().size(), is(1));
      assertThat(dictionary.getKeys().contains("key3"), is(true));
    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testVersionedWrite() throws Throwable {
    zkClient.start();

    try {
      DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");
      dictionary.write("key1", "data1".getBytes());
      int version = dictionary.getCurrentVersion();
      // delete key1 to make the dict version outdated
      Throwable exception = null;
      try {
        dictionary.write("key1", null);
        Map<String, byte[]> entry = new HashMap<>();
        entry.put("key2", "data2".getBytes());
        dictionary.write(entry, version);
      } catch (KeeperException.BadVersionException e) {
        exception = e;
      }
      assertTrue(exception instanceof KeeperException.BadVersionException);
    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testCreateWriteAndRead() throws Throwable {
    zkClient.start();

    try {
      DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");

      dictionary.write("bar", "fff".getBytes());
      dictionary.write("bar", "aaa".getBytes());

      assertThat(new String(dictionary.read("bar")), is("aaa"));

    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testReadBeforeWriteSetsWatchIfRequested() throws Exception {
    zkClient.start();

    try {
      DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");
      final CountDownLatch notified = new CountDownLatch(1);
      final String[] keys = new String[1];

      byte[] firstRead = dictionary.read("bar", new BaseChangeListener() {
        @Override
        public void onKeyAdded(String key) {
          notified.countDown();
          keys[0] = key;
        }
      }, true);

      assertThat(firstRead, is(nullValue()));
      dictionary.write("bar", "fff".getBytes());
      assertThat(notified.await(5, TimeUnit.SECONDS), is(true));
      assertThat(keys[0], is("bar"));

    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testMultipleWritesAndRead() throws Throwable {
    zkClient.start();

    try {
      final DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");
      final CountDownLatch done = new CountDownLatch(5);

      dictionary.write("bar", "fff".getBytes());
      Stat stat = new Stat();
      zkClient.getData().storingStatIn(stat).forPath("/foo/bar");

      int initialVersion = stat.getVersion();

      for (int i = 0; i < 5; i++) {
        executor.submit(new Runnable() {
          @Override
          public void run() {
            try {
              dictionary.write("bar", "aaa".getBytes());
            } catch (Exception e) {
              throw new RuntimeException(e);
            } finally {
              done.countDown();
            }
          }
        });
      }

      assertTrue(done.await(5, TimeUnit.SECONDS));

      assertThat(new String(dictionary.read("bar")), is("aaa"));
      zkClient.getData().storingStatIn(stat).forPath("/foo/bar");
      // All updates go through
      assertThat(stat.getVersion(), is(initialVersion + 5));

    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testDataChangeWatcherOnlyFiresOnce() throws Exception {
    zkClient.start();

    try {
      final DataDictionary dictionary = new DataDictionary(zkClient, executor, "foo");
      final AtomicInteger timesWatcherFired = new AtomicInteger();

      dictionary.write("bar", "fff".getBytes());

      byte[] bytes = dictionary.read("bar", new BaseChangeListener() {
        @Override
        public void onDataChanged(String key) {
          timesWatcherFired.incrementAndGet();
        }
      });

      assertThat(new String(bytes), is("fff"));
      dictionary.write("bar", "aaa".getBytes());

      final CountDownLatch done = new CountDownLatch(1);

      dictionary.read("bar", new BaseChangeListener() {
        @Override
        public void onDataChanged(String key) {
          done.countDown();
        }
      });

      dictionary.write("bar", "bbb".getBytes());

      assertThat(done.await(5, TimeUnit.SECONDS), is(true));
      assertThat(timesWatcherFired.get(), is(1));

    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testDataChangeWatcherMultipleWatches() throws Exception {
    zkClient.start();

    try {
      DataDictionary d1 = new DataDictionary(zkClient, executor, "foo");
      DataDictionary d2 = new DataDictionary(zkClient, executor, "foo");

      d2.write("bar", "fff".getBytes());

      Semaphore sync = new Semaphore(1);
      sync.acquire();

      DataWatcherExample dataWatcher = new DataWatcherExample(d1, sync);

      assertThat(dataWatcher.readAndWatchKey("bar"), is("fff".getBytes()));
      assertThat(sync.tryAcquire(5, TimeUnit.SECONDS), is(true));

      d2.write("bar", "aaa".getBytes());
      assertThat(sync.tryAcquire(5, TimeUnit.SECONDS), is(true));

      d2.write("bar", "bbb".getBytes());
      assertThat(sync.tryAcquire(5, TimeUnit.SECONDS), is(true));

      assertThat(dataWatcher.getDataHistory().size(), is(3));
      assertThat(dataWatcher.getDataHistory(), hasItems("fff".getBytes(), "aaa".getBytes(), "bbb".getBytes()));
    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testSubscribeToUpdates() throws Exception {
    zkClient.start();

    try {
      final List<String> addedKeys = new ArrayList<>();
      final List<String> removedKeys = new ArrayList<>();
      final List<String> changedKeys = new ArrayList<>();

      final CountDownLatch added = new CountDownLatch(2);
      final CountDownLatch removed = new CountDownLatch(1);
      final CountDownLatch changed = new CountDownLatch(1);

      DataDictionary.ChangeListener listener = new DataDictionary.ChangeListener() {
        @Override
        public synchronized void onKeyAdded(String key) {
          addedKeys.add(key);
          added.countDown();
        }

        @Override
        public synchronized void onKeyRemoved(String key) {
          removedKeys.add(key);
          removed.countDown();
        }

        @Override
        public synchronized void onDataChanged(String key) {
          changedKeys.add(key);
          changed.countDown();
        }
      };

      DataDictionary d1 = new DataDictionary(zkClient, executor, "foo");
      d1.subscribeToUpdates(listener);

      DataDictionary d2 = new DataDictionary(zkClient, executor, "foo");

      d2.write("bar", "bbb".getBytes());

      d1.read("bar", listener);
      d2.write("bar", "aaa".getBytes());
      assertThat(changed.await(5, TimeUnit.SECONDS), is(true));

      d2.write("baz", "fff".getBytes());
      assertThat(added.await(5, TimeUnit.SECONDS), is(true));

      zkClient.delete().forPath("/foo/baz");
      assertThat(removed.await(5, TimeUnit.SECONDS), is(true));

      Collections.sort(addedKeys);
      assertThat(addedKeys, contains("bar", "baz"));
      assertThat(removedKeys, contains("baz"));
      assertThat(changedKeys, contains("bar"));
    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testMultipleWriteMap() throws Exception {
    zkClient.start();

    try {
      final DataDictionary dictionary = new DataDictionary(zkClient, executor, "host");
      final Map<String, byte[]> m = new HashMap<String, byte[]>() {{
        put("k1", "v1".getBytes());
        put("k2", "v1".getBytes());
      }};
      // Test create
      dictionary.write(m);
      assertThat(new String(dictionary.read("k1")), is("v1"));
      assertThat(new String(dictionary.read("k2")), is("v1"));

      final Map<String, byte[]> m1 = new HashMap<String, byte[]>() {{
        put("k1", "v2".getBytes());
        put("k2", "v2".getBytes());
      }};
      // Test update
      dictionary.write(m1);
      assertThat(new String(dictionary.read("k1")), is("v2"));
      assertThat(new String(dictionary.read("k2")), is("v2"));

    } finally {
      zkClient.close();
    }
  }

  @Test
  public void testMultipleWriteList() throws Exception {
    zkClient.start();

    try {
      final DataDictionary dictionary = new DataDictionary(zkClient, executor, "missing");
      final List<String> l = new ArrayList<String>() {{
        add("k1");
        add("k2");
      }};
      // Test create
      dictionary.write(l);
      assertThat(new String(dictionary.read("k1")), is(""));
      assertThat(new String(dictionary.read("k2")), is(""));

      final List<String> l1 = new ArrayList<String>() {{
        add("k3");
        add("k4");
      }};

      // Test update
      dictionary.write(l1);
      assertThat(new String(dictionary.read("k3")), is(""));
      assertThat(new String(dictionary.read("k4")), is(""));

    } finally {
      zkClient.close();
    }
  }

  class BaseChangeListener implements DataDictionary.ChangeListener {
    @Override
    public void onKeyAdded(String key) {
    }

    @Override
    public void onKeyRemoved(String key) {
    }

    @Override
    public void onDataChanged(String key) {
    }
  }

  class DataWatcherExample extends BaseChangeListener {
    private final DataDictionary dictionary;
    private final Semaphore sync;
    private List<byte[]> dataHistory;

    DataWatcherExample(DataDictionary dictionary, Semaphore sync) {
      this.dictionary = dictionary;
      this.sync = sync;
      dataHistory = new ArrayList<>();
    }

    public synchronized byte[] readAndWatchKey(String key) throws Exception {
      byte[] data = dictionary.read(key, this);
      dataHistory.add(data);
      sync.release();
      return data;
    }

    public List<byte[]> getDataHistory() {
      return this.dataHistory;
    }

    @Override
    public void onDataChanged(String key) {
      try {
        readAndWatchKey(key);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
