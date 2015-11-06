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

import com.vmware.photon.controller.common.thrift.ServerSet;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.name.Names;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.BoundedExponentialBackoffRetry;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


/**
 * Zookeeper Guice module.
 */
public class ZookeeperModule extends AbstractModule {

  private ZookeeperConfig config;

  public ZookeeperModule() {
  }

  public ZookeeperModule(ZookeeperConfig config) {
    this.config = config;
  }

  @Provides
  @Singleton
  public RetryPolicy getRetryPolicy() {
    ZookeeperConfig.RetryConfig retries = checkNotNull(config).getRetries();
    return new BoundedExponentialBackoffRetry(
        retries.getBaseSleepTimeMs(),
        retries.getMaxSleepTimeMs(),
        retries.getMaxRetries()
    );
  }

  @Provides
  @Singleton
  public CuratorFramework getCuratorFramework(RetryPolicy retryPolicy) {
    checkNotNull(config);

    CuratorFramework client = CuratorFrameworkFactory
        .builder()
        .connectString(config.getQuorum())
        .retryPolicy(retryPolicy)
        .namespace(config.getNamespace())
        .build();

    client.start();
    return client;
  }

  @Provides
  @Singleton
  @ZkHostMonitor
  public ZookeeperHostMonitor getHostMonitor(
      CuratorFramework zkClient,
      @ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory) throws Exception {
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("ZkHostMonitorPathChildrenCache" + "-%d")
        .setDaemon(true)
        .build();
    ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
    return new ZookeeperHostMonitor(zkClient, childrenCacheFactory, executor);
  }

  @Provides
  @Singleton
  @ZkMissingHostMonitor
  public ZookeeperMissingHostMonitor getMissingHostMonitor(
      CuratorFramework zkClient,
      @ServicePathCacheFactory PathChildrenCacheFactory childrenCacheFactory) throws Exception {
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("ZkHostMissingMonitorPathChildrenCache" + "-%d")
        .setDaemon(true)
        .build();
    ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
    return new ZookeeperMissingHostMonitor(zkClient, childrenCacheFactory, executor);
  }

  @Provides
  @Singleton
  @ServiceReader
  public ZookeeperServerReader getServiceServerReader() {
    return new ZookeeperServiceReader();
  }

  @Provides
  @Singleton
  @HostReader
  public ZookeeperServerReader getHostServerReader() {
    return new ZookeeperHostReader();
  }

  @Provides
  @Singleton
  @ServicePathCacheFactory
  public PathChildrenCacheFactory getServicePathCacheFactory(CuratorFramework zkClient,
                                                             @ServiceReader ZookeeperServerReader reader) {
    return new PathChildrenCacheFactory(zkClient, reader);
  }

  @Provides
  @Singleton
  @HostPathCacheFactory
  public PathChildrenCacheFactory getHostServicePathCacheFactory(CuratorFramework zkClient,
                                                                 @HostReader ZookeeperServerReader reader) {
    return new PathChildrenCacheFactory(zkClient, reader);
  }

  public void setConfig(ZookeeperConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    install(new FactoryModuleBuilder()
        .implement(ServiceNode.class, Names.named("simple"), SimpleServiceNode.class)
        .implement(ServiceNode.class, Names.named("leader"), LeaderElectedServiceNode.class)
        .build(ServiceNodeFactory.class));

    install(new FactoryModuleBuilder()
        .implement(ServerSet.class, Names.named("Service"), ZookeeperServerSet.class)
        .implement(ServerSet.class, Names.named("Host"), ZookeeperHostSet.class)
        .build(ZookeeperServerSetFactory.class));

    bind(ZookeeperServerReader.class).to(ZookeeperHostReader.class);
  }
}
