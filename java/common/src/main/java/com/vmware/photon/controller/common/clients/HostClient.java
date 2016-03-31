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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.common.clients.exceptions.DatastoreNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.DestinationAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.DirectoryNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.DiskAttachedException;
import com.vmware.photon.controller.common.clients.exceptions.DiskDetachedException;
import com.vmware.photon.controller.common.clients.exceptions.DiskNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.ImageAlreadyExistException;
import com.vmware.photon.controller.common.clients.exceptions.ImageInUseException;
import com.vmware.photon.controller.common.clients.exceptions.ImageNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.ImageRefCountFileException;
import com.vmware.photon.controller.common.clients.exceptions.ImageTransferInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidReservationException;
import com.vmware.photon.controller.common.clients.exceptions.InvalidVmPowerStateException;
import com.vmware.photon.controller.common.clients.exceptions.IsoNotAttachedException;
import com.vmware.photon.controller.common.clients.exceptions.NetworkNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.NoSuchResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughCpuResourceException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughDatastoreCapacityException;
import com.vmware.photon.controller.common.clients.exceptions.NotEnoughMemoryResourceException;
import com.vmware.photon.controller.common.clients.exceptions.OperationInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.ResourceConstraintException;
import com.vmware.photon.controller.common.clients.exceptions.RpcException;
import com.vmware.photon.controller.common.clients.exceptions.ScanInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.StaleGenerationException;
import com.vmware.photon.controller.common.clients.exceptions.SweepInProgressException;
import com.vmware.photon.controller.common.clients.exceptions.SystemErrorException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotFoundException;
import com.vmware.photon.controller.common.clients.exceptions.VmNotPoweredOffException;
import com.vmware.photon.controller.common.thrift.ClientPool;
import com.vmware.photon.controller.common.thrift.ClientPoolFactory;
import com.vmware.photon.controller.common.thrift.ClientPoolOptions;
import com.vmware.photon.controller.common.thrift.ClientProxyFactory;
import com.vmware.photon.controller.common.zookeeper.gen.ServerAddress;
import com.vmware.photon.controller.host.gen.AttachISORequest;
import com.vmware.photon.controller.host.gen.AttachISOResponse;
import com.vmware.photon.controller.host.gen.CopyImageRequest;
import com.vmware.photon.controller.host.gen.CopyImageResponse;
import com.vmware.photon.controller.host.gen.CreateDiskError;
import com.vmware.photon.controller.host.gen.CreateDisksRequest;
import com.vmware.photon.controller.host.gen.CreateDisksResponse;
import com.vmware.photon.controller.host.gen.CreateImageFromVmRequest;
import com.vmware.photon.controller.host.gen.CreateImageFromVmResponse;
import com.vmware.photon.controller.host.gen.CreateVmRequest;
import com.vmware.photon.controller.host.gen.CreateVmResponse;
import com.vmware.photon.controller.host.gen.DeleteDirectoryRequest;
import com.vmware.photon.controller.host.gen.DeleteDirectoryResponse;
import com.vmware.photon.controller.host.gen.DeleteDiskError;
import com.vmware.photon.controller.host.gen.DeleteDisksRequest;
import com.vmware.photon.controller.host.gen.DeleteDisksResponse;
import com.vmware.photon.controller.host.gen.DeleteImageRequest;
import com.vmware.photon.controller.host.gen.DeleteImageResponse;
import com.vmware.photon.controller.host.gen.DeleteVmRequest;
import com.vmware.photon.controller.host.gen.DeleteVmResponse;
import com.vmware.photon.controller.host.gen.DetachISORequest;
import com.vmware.photon.controller.host.gen.DetachISOResponse;
import com.vmware.photon.controller.host.gen.FinalizeImageRequest;
import com.vmware.photon.controller.host.gen.FinalizeImageResponse;
import com.vmware.photon.controller.host.gen.GetConfigRequest;
import com.vmware.photon.controller.host.gen.GetConfigResponse;
import com.vmware.photon.controller.host.gen.GetDeletedImagesRequest;
import com.vmware.photon.controller.host.gen.GetDeletedImagesResponse;
import com.vmware.photon.controller.host.gen.GetImagesRequest;
import com.vmware.photon.controller.host.gen.GetImagesResponse;
import com.vmware.photon.controller.host.gen.GetInactiveImagesRequest;
import com.vmware.photon.controller.host.gen.GetInactiveImagesResponse;
import com.vmware.photon.controller.host.gen.GetVmNetworkRequest;
import com.vmware.photon.controller.host.gen.GetVmNetworkResponse;
import com.vmware.photon.controller.host.gen.Host;
import com.vmware.photon.controller.host.gen.HostMode;
import com.vmware.photon.controller.host.gen.ImageInfoRequest;
import com.vmware.photon.controller.host.gen.ImageInfoResponse;
import com.vmware.photon.controller.host.gen.MksTicketRequest;
import com.vmware.photon.controller.host.gen.MksTicketResponse;
import com.vmware.photon.controller.host.gen.NetworkConnectionSpec;
import com.vmware.photon.controller.host.gen.PowerVmOp;
import com.vmware.photon.controller.host.gen.PowerVmOpRequest;
import com.vmware.photon.controller.host.gen.PowerVmOpResponse;
import com.vmware.photon.controller.host.gen.ReserveRequest;
import com.vmware.photon.controller.host.gen.ReserveResponse;
import com.vmware.photon.controller.host.gen.ServiceTicketRequest;
import com.vmware.photon.controller.host.gen.ServiceTicketResponse;
import com.vmware.photon.controller.host.gen.ServiceType;
import com.vmware.photon.controller.host.gen.SetHostModeRequest;
import com.vmware.photon.controller.host.gen.SetHostModeResponse;
import com.vmware.photon.controller.host.gen.StartImageScanRequest;
import com.vmware.photon.controller.host.gen.StartImageScanResponse;
import com.vmware.photon.controller.host.gen.StartImageSweepRequest;
import com.vmware.photon.controller.host.gen.StartImageSweepResponse;
import com.vmware.photon.controller.host.gen.TransferImageRequest;
import com.vmware.photon.controller.host.gen.TransferImageResponse;
import com.vmware.photon.controller.host.gen.VmDiskOpError;
import com.vmware.photon.controller.host.gen.VmDisksAttachRequest;
import com.vmware.photon.controller.host.gen.VmDisksDetachRequest;
import com.vmware.photon.controller.host.gen.VmDisksOpResponse;
import com.vmware.photon.controller.resource.gen.Datastore;
import com.vmware.photon.controller.resource.gen.DiskLocator;
import com.vmware.photon.controller.resource.gen.Image;
import com.vmware.photon.controller.resource.gen.InactiveImageDescriptor;
import com.vmware.photon.controller.resource.gen.Locator;
import com.vmware.photon.controller.resource.gen.Resource;
import com.vmware.photon.controller.resource.gen.VmLocator;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Host Client Facade that hides the zookeeper/async interactions.
 * Note that this class is not thread safe.
 */
@RpcClient
public class HostClient {

  protected static final ClientPoolOptions CLIENT_POOL_OPTIONS = new ClientPoolOptions()
      .setMaxClients(1)
      .setMaxWaiters(100)
      .setTimeout(30, TimeUnit.SECONDS)
      .setServiceName("Host");
  private static final int DEFAULT_PORT_NUMBER = 8835;
  private static final int MAX_RESERVED_PORT_NUMBER = 1023;

  private static final Logger logger = LoggerFactory.getLogger(HostClient.class);
  private static final long ATTACH_DISKS_TIMEOUT_MS = 60000;
  private static final long ATTACH_ISO_TIMEOUT_MS = 60000;
  private static final long CREATE_DISKS_TIMEOUT_MS = 3600000;
  private static final long CREATE_VM_TIMEOUT_MS = 7200000; // two hours
  private static final long DELETE_DISK_TIMEOUT_MS = 1800000;
  private static final long CREATE_IMAGE_TIMEOUT_MS = 60000;
  private static final long DELETE_IMAGE_TIMEOUT_MS = 60000;
  private static final long START_IMAGE_SCAN_TIMEOUT_MS = 60000;
  private static final long START_IMAGE_SWEEP_TIMEOUT_MS = 60000;
  private static final long GET_INACTIVE_IMAGES_TIMEOUT_MS = 60000;
  private static final long GET_DELETED_IMAGES_TIMEOUT_MS = 60000;
  private static final long DELETE_DIRECTORY_TIMEOUT_MS = 60000;
  private static final long DELETE_VM_TIMEOUT_MS = 1800000;
  private static final long DETACH_DISKS_TIMEOUT_MS = 60000;
  private static final long DETACH_ISO_TIMEOUT_MS = 60000;
  private static final long FIND_DISK_TIMEOUT_MS = 60000;
  private static final long FIND_VM_TIMEOUT_MS = 60000;
  private static final long GET_HOST_CONFIG_TIMEOUT_MS = 60000;
  private static final long GET_IMAGE_INFO_TIMEOUT_MS = 60000;
  private static final long GET_IMAGES_TIMEOUT_MS = 60000;
  private static final long GET_SERVICE_TICKET_TIMEOUT_MS = 60000;
  private static final long GET_VM_MKS_TICKET_TIMEOUT_MS = 60000;
  private static final long GET_VM_NETWORK_TIMEOUT_MS = 60000;
  private static final long PLACE_TIMEOUT_MS = 60000;
  private static final long POWER_VM_OP_TIMEOUT_MS = 600000;
  private static final long RESERVE_TIMEOUT_MS = 60000;
  private final ClientProxyFactory<Host.AsyncClient> clientProxyFactory;
  private final ClientPoolFactory<Host.AsyncClient> clientPoolFactory;
  /**
   * clientProxy acquires a new client from ClientPool for every thrift call.
   * Reference: {@link ClientProxyImpl#createMethodHandler() createMethodHandler}.
   */
  private Host.AsyncClient clientProxy;
  private String hostIp;
  private int port;
  private ClientPool<Host.AsyncClient> clientPool;

  @Inject
  public HostClient(ClientProxyFactory<Host.AsyncClient> clientProxyFactory,
                    ClientPoolFactory<Host.AsyncClient> clientPoolFactory) {
    this.clientProxyFactory = clientProxyFactory;
    this.clientPoolFactory = clientPoolFactory;
  }

  public String getHostIp() {
    return hostIp;
  }

  public void setHostIp(String hostIp) {
    setIpAndPort(hostIp, DEFAULT_PORT_NUMBER);
  }

  public int getPort() {
    return port;
  }

  public void setIpAndPort(String ip, int port) {
    checkNotNull(ip, "IP can not be null");
    checkArgument(port > MAX_RESERVED_PORT_NUMBER,
        "Please set port above %s", MAX_RESERVED_PORT_NUMBER);

    if (ip.equals(this.hostIp) && port == this.port) {
      return;
    }

    this.close();
    this.hostIp = ip;
    this.port = port;
  }

  /**
   * This method performs an asynchronous Thrift call to attach one or more
   * disks to a VM. On completion, the specified handler is invoked.
   *
   * @param vmId    Supplies the ID of the VM to which the disk should be
   *                attached.
   * @param diskIds Supplies the IDs of the disk or disks which should be
   *                attached.
   * @param handler Supplies a handler object to be invoked upon completion.
   * @throws RpcException
   */
  @RpcMethod
  public void attachDisks(String vmId, List<String> diskIds,
                          AsyncMethodCallback<Host.AsyncClient.attach_disks_call> handler)
      throws RpcException {
    ensureClient();
    VmDisksAttachRequest vmDisksAttachRequest = new VmDisksAttachRequest(vmId, diskIds);
    clientProxy.setTimeout(ATTACH_DISKS_TIMEOUT_MS);
    logger.info("attach_disks vm {}, disks {}, target {} request {}",
        vmId, diskIds, getHostIp(), vmDisksAttachRequest);

    try {
      clientProxy.attach_disks(vmDisksAttachRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to attach one or more disks
   * to a VM.
   *
   * @param vmId    Supplies the ID of the VM to which the disk should be
   *                attached.
   * @param diskIds Supplies the IDs of the disk or disks which should be
   *                attached.
   * @return On success, the return value is the VmDisksOpResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public VmDisksOpResponse attachDisks(String vmId, List<String> diskIds)
      throws InterruptedException, RpcException {
    SyncHandler<VmDisksOpResponse, Host.AsyncClient.attach_disks_call> syncHandler = new SyncHandler<>();
    attachDisks(vmId, diskIds, syncHandler);
    syncHandler.await();
    logger.info("attach_disks vm {}, disks {}, target {}",
        vmId, diskIds, getHostIp());
    return ResponseValidator.checkAttachDisksResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to a to attach an ISO to
   * a VM. On completion, the specified handler is invoked.
   *
   * @param vmId    Supplies the ID of the VM to which the ISO should be
   *                attached.
   * @param isoPath Supplies the path to the ISO file which should be attached.
   * @param handler Supplies a handler object to be invoked upon completion.
   * @throws RpcException
   */
  @RpcMethod
  public void attachISOtoVM(String vmId, String isoPath,
                            AsyncMethodCallback<Host.AsyncClient.attach_iso_call> handler)
      throws RpcException {
    ensureClient();
    AttachISORequest attachISORequest = new AttachISORequest(vmId, isoPath);
    clientProxy.setTimeout(ATTACH_ISO_TIMEOUT_MS);
    logger.info("attach_iso vm {}, isoPath {}, target {} request {}",
        vmId, isoPath, getHostIp(), attachISORequest);

    try {
      clientProxy.attach_iso(attachISORequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to attach an ISO to a VM.
   *
   * @param vmId    Supplies the ID of the VM to which the ISO should be
   *                attached.
   * @param isoPath Supplies the path to the ISO file which should be attached.
   * @return On success, the return value is the AttachISOResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public AttachISOResponse attachISO(String vmId, String isoPath)
      throws InterruptedException, RpcException {
    SyncHandler<AttachISOResponse, Host.AsyncClient.attach_iso_call> syncHandler = new SyncHandler<>();
    attachISOtoVM(vmId, isoPath, syncHandler);
    syncHandler.await();
    logger.info("finished attach_iso vm {}, isoPath {}, target {}",
        vmId, isoPath, getHostIp());
    return ResponseValidator.checkAttachISOResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to copy an image from one
   * data store to another. On completion, the specified handler is invoked.
   *
   * @param imageId     Supplies the ID of an image.
   * @param source      Supplies the source data store for the copy operation.
   * @param destination Supplies the destination data store for the copy
   *                    operation.
   * @param handler     Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void copyImage(String imageId, String source, String destination,
                        AsyncMethodCallback<Host.AsyncClient.copy_image_call> handler)
      throws RpcException {
    ensureClient();
    CopyImageRequest copyImageRequest = new CopyImageRequest();
    copyImageRequest.setSource(Util.constructImage(source, imageId));
    copyImageRequest.setDestination(Util.constructImage(destination, imageId));
    // N.B. No timeout was specified here. This may be a bug.
    logger.info("copy_image target {}, request {}", getHostIp(), copyImageRequest);

    try {
      clientProxy.copy_image(copyImageRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous operation to copy an image from one
   * data store to another.
   *
   * @param imageId     Supplies the ID of an image.
   * @param source      Supplies the source data store for the copy operation.
   * @param destination Supplies the destination data store for the copy
   *                    operation.
   * @return On success, the return value is the CopyImageResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public CopyImageResponse copyImage(String imageId, String source, String destination)
      throws InterruptedException, RpcException {
    SyncHandler<CopyImageResponse, Host.AsyncClient.copy_image_call> syncHandler = new SyncHandler<>();
    copyImage(imageId, source, destination, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkCopyImageResponse(syncHandler.getResponse());
  }


  /**
   * This method performs an asynchronous Thrift call to copy an image from one
   * host to another. On completion, the specified handler is invoked.
   *
   * @param imageId     Supplies the ID of an image.
   * @param source      Supplies the source data store for the copy operation.
   * @param destination Supplies the destination data store for the copy
   *                    operation.
   * @param handler     Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void transferImage(String imageId, String source, String destination, ServerAddress destinationHost,
                            AsyncMethodCallback<Host.AsyncClient.copy_image_call> handler)
      throws RpcException {
    ensureClient();
    TransferImageRequest transferImageRequest = new TransferImageRequest();
    transferImageRequest.setDestination_datastore_id(destination);
    transferImageRequest.setDestination_host(destinationHost);
    transferImageRequest.setSource_datastore_id(source);
    transferImageRequest.setSource_image_id(imageId);
    // N.B. No timeout was specified here. This may be a bug.
    logger.info("transfer_image target {}, request {}", getHostIp(), transferImageRequest);

    try {
      clientProxy.transfer_image(transferImageRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous operation to copy an image from one
   * host to another.
   *
   * @param imageId     Supplies the ID of an image.
   * @param source      Supplies the source data store for the copy operation.
   * @param destination Supplies the destination data store for the copy
   *                    operation.
   * @return On success, the return value is the CopyImageResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public TransferImageResponse transferImage(String imageId, String source, String destination,
                                             ServerAddress destinationHost)
      throws InterruptedException, RpcException {
    SyncHandler<TransferImageResponse, Host.AsyncClient.transfer_image_call> syncHandler = new SyncHandler<>();
    transferImage(imageId, source, destination, destinationHost, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkTransferImageResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to create a disk. On
   * completion, the specified handler is invoked.
   *
   * @param reservation Supplies a disk reservation.
   * @param handler     Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void createDisks(String reservation,
                          AsyncMethodCallback<Host.AsyncClient.create_disks_call> handler)
      throws RpcException {
    ensureClient();
    CreateDisksRequest createDisksRequest = new CreateDisksRequest(reservation);
    clientProxy.setTimeout(CREATE_DISKS_TIMEOUT_MS);
    logger.info("create_disks reservation {}, target {}, request {}",
        reservation, getHostIp(), createDisksRequest);

    try {
      clientProxy.create_disks(createDisksRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to create a disk.
   *
   * @param reservation Supplies a disk reservation.
   * @return On success, the return value is the CreateDisksResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public CreateDisksResponse createDisks(String reservation)
      throws InterruptedException, RpcException {
    SyncHandler<CreateDisksResponse, Host.AsyncClient.create_disks_call> syncHandler = new SyncHandler<>();
    createDisks(reservation, syncHandler);
    syncHandler.await();
    logger.info("finished create_disks reservation {}, target {}",
        reservation, getHostIp());
    return ResponseValidator.checkCreateDisksResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to create a VM. On
   * completion, the specified handler is invoked.
   *
   * @param reservation           Supplies a VM reservation.
   * @param networkConnectionSpec Supplies the specification of network connections.
   * @param environment           Supplies extra environment settings for the VM.
   * @param handler               Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void createVm(String reservation,
                       NetworkConnectionSpec networkConnectionSpec,
                       Map<String, String> environment,
                       AsyncMethodCallback<Host.AsyncClient.create_vm_call> handler)
      throws RpcException {
    ensureClient();
    CreateVmRequest createVmRequest = new CreateVmRequest(reservation);
    createVmRequest.setNetwork_connection_spec(networkConnectionSpec);
    if (environment != null && !environment.isEmpty()) {
      createVmRequest.setEnvironment(environment);
    }

    clientProxy.setTimeout(CREATE_VM_TIMEOUT_MS);
    logger.info("create_vm target {}, reservation {}, request {}", getHostIp(), reservation, createVmRequest);

    try {
      clientProxy.create_vm(createVmRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to create a VM.
   *
   * @param reservation           Supplies a VM reservation.
   * @param networkConnectionSpec Supplies the specification of network connections.
   * @param environment           Supplies extra environment settings for the VM.
   * @return On success, the return value is the CreateVmResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public CreateVmResponse createVm(String reservation,
                                   NetworkConnectionSpec networkConnectionSpec,
                                   Map<String, String> environment)
      throws InterruptedException, RpcException {
    SyncHandler<CreateVmResponse, Host.AsyncClient.create_vm_call> syncHandler = new SyncHandler<>();
    createVm(reservation, networkConnectionSpec, environment, syncHandler);
    syncHandler.await();
    logger.info("finished create_vm target {}, reservation {}", getHostIp(), reservation);
    return ResponseValidator.checkCreateVmResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to delete one or more
   * disks. On completion, the specified handler is invoked.
   *
   * @param diskIds Supplies a list of disk IDs.
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void deleteDisks(List<String> diskIds,
                          AsyncMethodCallback<Host.AsyncClient.delete_disks_call> handler)
      throws RpcException {
    ensureClient();
    DeleteDisksRequest deleteDisksRequest = new DeleteDisksRequest(diskIds);
    clientProxy.setTimeout(DELETE_DISK_TIMEOUT_MS);
    logger.info("delete_disks diskIds {}, target {}, request {}", diskIds, getHostIp(), deleteDisksRequest);

    try {
      clientProxy.delete_disks(deleteDisksRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to delete one or more
   * disks.
   *
   * @param diskIds Supplies a list of disk IDs.
   * @return On success, the return value is the DeleteDisksResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DeleteDisksResponse deleteDisks(List<String> diskIds)
      throws InterruptedException, RpcException {
    SyncHandler<DeleteDisksResponse, Host.AsyncClient.delete_disks_call> syncHandler = new SyncHandler<>();
    deleteDisks(diskIds, syncHandler);
    syncHandler.await();
    logger.info("finished delete_disks diskIds {}, target {}", diskIds, getHostIp());
    return ResponseValidator.checkDeleteDisksResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an synchronous Thrift call to create an image by moving the image
   * file from tmp path. On completion, the specified handler is invoked.
   *
   * @param imageId      Supplies the ID of an image to be created.
   * @param datastore    Supplies the data store on which the image exists.
   * @param tmpImagePath Supplies the temporary path of the image to move from.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public FinalizeImageResponse finalizeImage(String imageId, String datastore, String tmpImagePath)
      throws InterruptedException, RpcException {
    SyncHandler<FinalizeImageResponse, Host.AsyncClient.finalize_image_call> syncHandler = new SyncHandler<>();
    finalizeImage(imageId, datastore, tmpImagePath, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkFinalizeImageResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to create an image by moving the image
   * file from tmp path. On completion, the specified handler is invoked.
   *
   * @param imageId      Supplies the ID of an image to be created.
   * @param datastore    Supplies the data store on which the image exists.
   * @param tmpImagePath Supplies the temporary path of the image to move from.
   * @param handler      Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void finalizeImage(String imageId, String datastore, String tmpImagePath,
                          AsyncMethodCallback<Host.AsyncClient.finalize_image_call> handler)
      throws RpcException {
    ensureClient();
    FinalizeImageRequest finalizeImageRequest = new FinalizeImageRequest();
    finalizeImageRequest.setImage_id(imageId);
    finalizeImageRequest.setDatastore(datastore);
    finalizeImageRequest.setTmp_image_path(tmpImagePath);
    clientProxy.setTimeout(CREATE_IMAGE_TIMEOUT_MS);
    logger.info("create_image target {}, request {}", getHostIp(), finalizeImageRequest);

    try {
      clientProxy.finalize_image(finalizeImageRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * See {@link #deleteImage(String, String, boolean, org.apache.thrift.async.AsyncMethodCallback) deleteImage}.
   */
  @RpcMethod
  public void deleteImage(String imageId, String dataStore,
                          AsyncMethodCallback<Host.AsyncClient.delete_image_call> handler)
      throws RpcException {
    deleteImage(imageId, dataStore, true, handler);
  }

  /**
   * This method performs an asynchronous Thrift call to delete an image. On
   * completion, the specified handler is invoked.
   *
   * @param imageId      Supplies the ID of an image to be deleted.
   * @param dataStore    Supplies the data store on which the image exists.
   * @param setTombstone Supplies whether the image is a tombstone.
   * @param handler      Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void deleteImage(String imageId, String dataStore, boolean setTombstone,
                          AsyncMethodCallback<Host.AsyncClient.delete_image_call> handler)
      throws RpcException {
    ensureClient();
    DeleteImageRequest deleteImageRequest = new DeleteImageRequest(new Image(imageId, new Datastore(dataStore)));
    deleteImageRequest.setTombstone(setTombstone);
    clientProxy.setTimeout(DELETE_IMAGE_TIMEOUT_MS);
    logger.info("delete_image target {}, request {}", getHostIp(), deleteImageRequest);

    try {
      clientProxy.delete_image(deleteImageRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * See {@link #deleteImage(String, String, boolean)}.
   */
  @RpcMethod
  public DeleteImageResponse deleteImage(String imageId, String datastore)
      throws InterruptedException, RpcException {
    return deleteImage(imageId, datastore, true);
  }

  /**
   * This method performs a synchronous Thrift call to delete an image.
   *
   * @param imageId      Supplies the ID of an image to be deleted.
   * @param dataStore    Supplies the data store on which the image exists.
   * @param setTombstone Supplies whether the image is a tombstone.
   * @return On success, the return value is the DeleteImageResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DeleteImageResponse deleteImage(String imageId, String dataStore, boolean setTombstone)
      throws InterruptedException, RpcException {
    SyncHandler<DeleteImageResponse, Host.AsyncClient.delete_image_call> syncHandler = new SyncHandler<>();
    deleteImage(imageId, dataStore, setTombstone, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkDeleteImageResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to start an image scan on a datastore.
   *
   * @param dataStore
   * @param scanRate
   * @param timeout
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void startImageScan(String dataStore, Long scanRate, Long timeout,
                             AsyncMethodCallback<Host.AsyncClient.start_image_scan_call> handler)
      throws RpcException {
    ensureClient();

    StartImageScanRequest request = new StartImageScanRequest(dataStore);
    if (null != scanRate) {
      request.setScan_rate(scanRate);
    }
    if (null != timeout) {
      request.setTimeout(timeout);
    }

    try {
      logger.info("start_image_scan target {}, request {}", getHostIp(), request);
      clientProxy.setTimeout(START_IMAGE_SCAN_TIMEOUT_MS);
      clientProxy.start_image_scan(request, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs an asynchronous Thrift call to retrieve the list of inactive images on a datastore.
   *
   * @param dataStore
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void getInactiveImages(String dataStore,
                                AsyncMethodCallback<Host.AsyncClient.get_inactive_images_call> handler)
      throws RpcException {
    ensureClient();

    GetInactiveImagesRequest request = new GetInactiveImagesRequest(dataStore);

    try {
      logger.info("get_inactive images target {}, request {}", getHostIp(), request);
      clientProxy.setTimeout(GET_INACTIVE_IMAGES_TIMEOUT_MS);
      clientProxy.get_inactive_images(request, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs an asynchronous Thrift call to start an image scan on a datastore.
   *
   * @param dataStore
   * @param images
   * @param sweepRate
   * @param timeout
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void startImageSweep(String dataStore, List<InactiveImageDescriptor> images,
                              Long sweepRate, Long timeout,
                              AsyncMethodCallback<Host.AsyncClient.start_image_sweep_call> handler)
      throws RpcException {
    ensureClient();

    StartImageSweepRequest request = new StartImageSweepRequest(dataStore, images);
    if (null != sweepRate) {
      request.setSweep_rate(sweepRate);
    }
    if (null != timeout) {
      request.setTimeout(timeout);
    }

    try {
      logger.info("start_image_sweep target {}, request {}", getHostIp(), request);
      clientProxy.setTimeout(START_IMAGE_SWEEP_TIMEOUT_MS);
      clientProxy.start_image_sweep(request, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs an asynchronous Thrift call to retrieve the list of deleted images on a datastore.
   *
   * @param dataStore
   * @param handler
   * @throws RpcException
   */
  @RpcMethod
  public void getDeletedImages(String dataStore,
                               AsyncMethodCallback<Host.AsyncClient.get_deleted_images_call> handler)
      throws RpcException {
    ensureClient();

    GetDeletedImagesRequest request = new GetDeletedImagesRequest(dataStore);

    try {
      logger.info("get_deleted_images target {}, request {}", getHostIp(), request);
      clientProxy.setTimeout(GET_DELETED_IMAGES_TIMEOUT_MS);
      clientProxy.get_deleted_images(request, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs an asynchronous Thrift call to create an image from vm.
   * On completion, the specified handler is invoked.
   *
   * @param vmId
   * @param imageId
   * @param datastore
   * @param tmpImagePath
   * @param handler
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public void createImageFromVm(
      String vmId,
      String imageId,
      String datastore,
      String tmpImagePath,
      AsyncMethodCallback<Host.AsyncClient.create_image_from_vm_call> handler)
      throws InterruptedException, RpcException {
    ensureClient();
    CreateImageFromVmRequest createImageFromVmRequest = new CreateImageFromVmRequest(
        vmId, imageId, datastore, tmpImagePath);
    clientProxy.setTimeout(CREATE_IMAGE_TIMEOUT_MS);
    logger.info("create_image_from_vm target {}, request {}", getHostIp(), createImageFromVmRequest);

    try {
      clientProxy.create_image_from_vm(createImageFromVmRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs an asynchronous Thrift call to create an image from vm.
   * On completion, the specified handler is invoked.
   *
   * @param vmId
   * @param imageId
   * @param datastore
   * @param tmpImagePath
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public CreateImageFromVmResponse createImageFromVm(
      String vmId,
      String imageId,
      String datastore,
      String tmpImagePath)
      throws InterruptedException, RpcException {
    SyncHandler<CreateImageFromVmResponse, Host.AsyncClient.create_image_from_vm_call> syncHandler =
        new SyncHandler<>();
    createImageFromVm(vmId, imageId, datastore, tmpImagePath, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkCreateImageResponse(syncHandler.getResponse());
  }

  /**
   * This method performs a synchronous Thrift call to delete a directory.
   *
   * @param directoryPath Supplies the path of the directory to be deleted.
   * @param dataStore     Supplies the data store on which the directory exists.
   * @return On success, the return value is the DeleteDirectoryResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  public DeleteDirectoryResponse deleteDirectory(String directoryPath, String dataStore)
      throws InterruptedException, RpcException {
    SyncHandler<DeleteDirectoryResponse, Host.AsyncClient.delete_directory_call> syncHandler = new SyncHandler<>();
    deleteDirectory(directoryPath, dataStore, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkDeleteDirectoryResponse(syncHandler.getResponse());
  }

  /**
   * This method performs a asynchronous Thrift call to delete a directory. On
   * completion, the specified handler is invoked.
   *
   * @param directoryPath Supplies the path of the directory to be deleted.
   * @param dataStore     Supplies the data store on which the directory exists.
   * @param handler       Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void deleteDirectory(String directoryPath, String dataStore,
                              AsyncMethodCallback<Host.AsyncClient.delete_directory_call> handler)
      throws RpcException {
    ensureClient();
    DeleteDirectoryRequest deleteDirectoryRequest = new DeleteDirectoryRequest(dataStore, directoryPath);
    clientProxy.setTimeout(DELETE_DIRECTORY_TIMEOUT_MS);
    logger.info("delete_directory target {}, request {]", getHostIp(), deleteDirectoryRequest);

    try {
      clientProxy.delete_directory(deleteDirectoryRequest, handler);
    } catch (TException e) {
      throw new RpcException(e);
    }
  }


  /**
   * This method performs an asynchronous Thrift call to delete a VM. On
   * completion, the specified handler is invoked.
   *
   * @param vmId            Supplies the ID of a VM to be deleted.
   * @param diskIdsToDetach Supplies the ID of one or more disks to be detached
   *                        as part of the operation.
   * @param handler         Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void deleteVm(String vmId, List<String> diskIdsToDetach,
                       AsyncMethodCallback<Host.AsyncClient.delete_vm_call> handler)
      throws RpcException {
    ensureClient();
    DeleteVmRequest deleteVmRequest = new DeleteVmRequest(vmId);
    deleteVmRequest.setDisk_ids(diskIdsToDetach);
    clientProxy.setTimeout(DELETE_VM_TIMEOUT_MS);
    logger.info("delete_vm {}, target {}, request {}", vmId, getHostIp(), deleteVmRequest);

    try {
      clientProxy.delete_vm(deleteVmRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to delete a VM.
   *
   * @param vmId            Supplies the ID of a VM to be deleted.
   * @param diskIdsToDetach Supplies the ID of one or more disks to be detached
   *                        as part of the operation.
   * @return On success, the return value is the DeleteVmResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DeleteVmResponse deleteVm(String vmId, List<String> diskIdsToDetach)
      throws InterruptedException, RpcException {
    SyncHandler<DeleteVmResponse, Host.AsyncClient.delete_vm_call> syncHandler = new SyncHandler<>();
    deleteVm(vmId, diskIdsToDetach, syncHandler);
    syncHandler.await();
    logger.info("finished delete_vm {}, target {}", vmId, getHostIp());
    return ResponseValidator.checkDeleteVmResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to detach one or more
   * disks from a VM. On completion, the specified handler is invoked.
   *
   * @param vmId    Supplies the ID of the VM from which the disks should be
   *                detached.
   * @param diskIds Supplies the IDs of the disks to be detached.
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void detachDisks(String vmId, List<String> diskIds,
                          AsyncMethodCallback<Host.AsyncClient.detach_disks_call> handler)
      throws RpcException {
    ensureClient();
    VmDisksDetachRequest vmDisksDetachRequest = new VmDisksDetachRequest(vmId, diskIds);
    clientProxy.setTimeout(DETACH_DISKS_TIMEOUT_MS);
    logger.info("detach_disks vm {}, disks {}, target {}, request {}",
        vmId, diskIds, getHostIp(), vmDisksDetachRequest);

    try {
      clientProxy.detach_disks(vmDisksDetachRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to detach one or more disks
   * from a VM.
   *
   * @param vmId    Supplies the ID of the VM from which the disks should be
   *                detached.
   * @param diskIds Supplies the IDs of the disks to be detached.
   * @return On success, the return value is the VmDisksOpResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public VmDisksOpResponse detachDisks(String vmId, List<String> diskIds)
      throws InterruptedException, RpcException {
    SyncHandler<VmDisksOpResponse, Host.AsyncClient.detach_disks_call> syncHandler = new SyncHandler<>();
    detachDisks(vmId, diskIds, syncHandler);
    syncHandler.await();
    logger.info("finished detach_disks vm {}, disks {}, target {}",
        vmId, diskIds, getHostIp());
    return ResponseValidator.checkDetachDisksResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to detach an ISO from a
   * VM. On completion, the specified handler is invoked.
   *
   * @param vmId         Supplies the ID of the VM from which the ISO should be
   *                     detached.
   * @param isDeleteFile Indicates whether the caller is a delete file
   *                     operation.
   * @param handler      Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void detachISO(String vmId, boolean isDeleteFile,
                        AsyncMethodCallback<Host.AsyncClient.detach_iso_call> handler)
      throws RpcException {
    ensureClient();
    DetachISORequest detachISORequest = new DetachISORequest(vmId);
    detachISORequest.setDelete_file(isDeleteFile);
    clientProxy.setTimeout(DETACH_ISO_TIMEOUT_MS);
    logger.info("detach_iso vm {}, target {}, request {}", vmId, getHostIp(), detachISORequest);

    try {
      clientProxy.detach_iso(detachISORequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to detach an ISO from a VM.
   *
   * @param vmId         Supplies the ID of the VM from which the ISO should be
   *                     detached.
   * @param isDeleteFile Indicates whether the caller is a delete file
   *                     operation.
   * @return On success, the return value is the DetachISOResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public DetachISOResponse detachISO(String vmId, boolean isDeleteFile)
      throws InterruptedException, RpcException {
    SyncHandler<DetachISOResponse, Host.AsyncClient.detach_iso_call> syncHandler = new SyncHandler<>();
    detachISO(vmId, isDeleteFile, syncHandler);
    syncHandler.await();
    logger.info("finished detach_iso vm {}, target {}", vmId, getHostIp());
    return ResponseValidator.checkDetachISOResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to locate a disk. On
   * completion, the specified handler is invoked.
   *
   * @param diskId  Supplies the ID of the disk to locate.
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void findDisk(String diskId,
                       AsyncMethodCallback<Host.AsyncClient.find_call> handler)
      throws RpcException {
    ensureClient();
    Locator locator = new Locator();
    locator.setDisk(new DiskLocator(diskId));
    FindRequest findRequest = new FindRequest(locator);
    clientProxy.setTimeout(FIND_DISK_TIMEOUT_MS);
    logger.info("find disk {}, target {}, request {}", diskId, getHostIp(), findRequest);

    try {
      clientProxy.find(findRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to locate a disk.
   *
   * @param diskId Supplies the ID of the disk to locate.
   * @return If the call completes as expected and the disk is found, then the
   * return value is true. If the call completes as expected and the disk is
   * not found, then the return value is false.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public boolean findDisk(String diskId)
      throws InterruptedException, RpcException {
    ensureClient();
    SyncHandler<FindResponse, Host.AsyncClient.find_call> syncHandler = new SyncHandler<>();
    findDisk(diskId, syncHandler);
    syncHandler.await();
    logger.info("finished find disk {}, target {}", diskId, getHostIp());
    return ResponseValidator.checkFindDiskResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to locate a VM. On
   * completion, the specified handler is invoked.
   *
   * @param vmId    Supplies the ID of the VM to locate.
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void findVm(String vmId,
                     AsyncMethodCallback<Host.AsyncClient.find_call> handler)
      throws RpcException {
    ensureClient();
    Locator locator = new Locator();
    locator.setVm(new VmLocator(vmId));
    FindRequest findRequest = new FindRequest(locator);
    clientProxy.setTimeout(FIND_VM_TIMEOUT_MS);
    logger.info("find vm {}, target {}, request {}", vmId, getHostIp(), findRequest);

    try {
      clientProxy.find(findRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to locate a VM.
   *
   * @param vmId Supplies the ID of the VM to locate.
   * @return If the call completes as expected and the VM is found, then the
   * return value is true. If the call completes as expected and the VM is not
   * found, then the return value is false.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public boolean findVm(String vmId)
      throws InterruptedException, RpcException {
    SyncHandler<FindResponse, Host.AsyncClient.find_call> syncHandler = new SyncHandler<>();
    findVm(vmId, syncHandler);
    syncHandler.await();
    logger.info("finished find vm {}, target {}, request {}", vmId, getHostIp());
    return ResponseValidator.checkFindVmResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to get the configuration
   * state for a host. On completion, the specified handler is invoked.
   *
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void getHostConfig(AsyncMethodCallback<Host.AsyncClient.get_host_config_call> handler)
      throws RpcException {
    ensureClient();
    GetConfigRequest getConfigRequest = new GetConfigRequest();
    clientProxy.setTimeout(GET_HOST_CONFIG_TIMEOUT_MS);
    logger.info("get_host_config target {}, request {}", getHostIp(), getConfigRequest);

    try {
      clientProxy.get_host_config(getConfigRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to get the configuration
   * state for a host.
   *
   * @return On success, the return value is the GetConfigResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public GetConfigResponse getHostConfig()
      throws InterruptedException, RpcException {
    SyncHandler<GetConfigResponse, Host.AsyncClient.get_host_config_call> syncHandler = new SyncHandler<>();
    getHostConfig(syncHandler);
    syncHandler.await();
    return ResponseValidator.checkGetConfigResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to get the state of an
   * image. On completion, the specified handler is invoked.
   *
   * @param imageId     Supplies the ID of an image.
   * @param dataStoreId Supplies the ID of a data store.
   * @param handler     Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void getImageInfo(String imageId, String dataStoreId,
                           AsyncMethodCallback<Host.AsyncClient.get_image_info_call> handler)
      throws RpcException {
    ensureClient();
    ImageInfoRequest imageInfoRequest = new ImageInfoRequest(imageId, dataStoreId);
    clientProxy.setTimeout(GET_IMAGE_INFO_TIMEOUT_MS);
    logger.info("get_image_info target {}, request {}", getHostIp(), imageInfoRequest);

    try {
      clientProxy.get_image_info(imageInfoRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs an asynchronous Thrift call to get the state of an
   * image. On completion, the specified handler is invoked.
   *
   * @param imageId     Supplies the ID of an image.
   * @param dataStoreId Supplies the ID of a data store.
   * @return On success, the return value is the ImageInfoResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public ImageInfoResponse getImageInfo(String imageId, String dataStoreId)
      throws InterruptedException, RpcException {
    SyncHandler<ImageInfoResponse, Host.AsyncClient.get_image_info_call> syncHandler = new SyncHandler<>();
    getImageInfo(imageId, dataStoreId, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkImageInfoResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to get the images on a
   * data store. On completion, the specified handler is invoked.
   *
   * @param dataStoreId Supplies the ID of a data store.
   * @param handler     Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void getImages(String dataStoreId,
                        AsyncMethodCallback<Host.AsyncClient.get_images_call> handler)
      throws RpcException {
    ensureClient();
    GetImagesRequest getImagesRequest = new GetImagesRequest(dataStoreId);
    clientProxy.setTimeout(GET_IMAGES_TIMEOUT_MS);
    logger.info("get_images target {}, request {}", getHostIp(), getImagesRequest);

    try {
      clientProxy.get_images(getImagesRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to get the images on a data
   * store.
   *
   * @param dataStoreId Supplies the ID of a data store.
   * @return On success, the return value is the GetImagesResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public GetImagesResponse getImages(String dataStoreId)
      throws InterruptedException, RpcException {
    SyncHandler<GetImagesResponse, Host.AsyncClient.get_images_call> syncHandler = new SyncHandler<>();
    getImages(dataStoreId, syncHandler);
    syncHandler.await();
    return ResponseValidator.checkGetImagesResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to get an NFC ticket for
   * an ESX host. On completion, the specified handler is invoked.
   *
   * @param dataStore Supplies the name of a data store.
   * @param handler   Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void getNfcServiceTicket(String dataStore,
                                  AsyncMethodCallback<Host.AsyncClient.get_service_ticket_call> handler)
      throws RpcException {
    ensureClient();
    ServiceTicketRequest serviceTicketRequest = new ServiceTicketRequest(ServiceType.NFC);
    serviceTicketRequest.setDatastore_name(dataStore);
    clientProxy.setTimeout(GET_SERVICE_TICKET_TIMEOUT_MS);
    logger.info("get_service_ticket dataStore {}, target {}, request {}",
        dataStore, getHostIp(), serviceTicketRequest);

    try {
      clientProxy.get_service_ticket(serviceTicketRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to get an NFC ticket for a
   * data store.
   *
   * @param dataStore Supplies the name of a data store.
   * @return On success, the return value is the ServiceTicketResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public ServiceTicketResponse getNfcServiceTicket(String dataStore)
      throws InterruptedException, RpcException {
    SyncHandler<ServiceTicketResponse, Host.AsyncClient.get_service_ticket_call> syncHandler = new SyncHandler<>();
    getNfcServiceTicket(dataStore, syncHandler);
    syncHandler.await();
    logger.info("finished get_service_ticket dataStore {}, target {}", dataStore, getHostIp());
    return ResponseValidator.checkGetNfcServiceTicketResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to get the networks for a
   * VM. On completion, the specified handler is invoked.
   *
   * @param vmId    Supplies the ID of a VM.
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void getVmNetworks(String vmId,
                            AsyncMethodCallback<Host.AsyncClient.get_vm_networks_call> handler)
      throws RpcException {
    ensureClient();
    GetVmNetworkRequest getVmNetworkRequest = new GetVmNetworkRequest(vmId);
    clientProxy.setTimeout(GET_VM_NETWORK_TIMEOUT_MS);
    logger.info("get_vm_networks vm {}, target {}, request {}", vmId, getHostIp(), getVmNetworkRequest);

    try {
      clientProxy.get_vm_networks(getVmNetworkRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  @RpcMethod
  public void getVmMksTicket(String vmId,
                             AsyncMethodCallback<Host.AsyncClient.get_mks_ticket_call> handler)
      throws RpcException {
    ensureClient();
    MksTicketRequest mksTicketRequest = new MksTicketRequest(vmId);
    clientProxy.setTimeout(GET_VM_MKS_TICKET_TIMEOUT_MS);
    logger.info("get_vm_mks_ticket vm {}, target {}, request {}", vmId, getHostIp(), mksTicketRequest);

    try {
      clientProxy.get_mks_ticket(mksTicketRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to get the networks for a
   * VM.
   *
   * @param vmId Supplies the ID of a VM.
   * @return On success, the return value is the GetVmNetworkResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public GetVmNetworkResponse getVmNetworks(String vmId)
      throws InterruptedException, RpcException {
    SyncHandler<GetVmNetworkResponse, Host.AsyncClient.get_vm_networks_call> syncHandler = new SyncHandler<>();
    getVmNetworks(vmId, syncHandler);
    syncHandler.await();
    logger.info("finished get_vm_networks vm {}, target {}", vmId, getHostIp());
    return ResponseValidator.checkGetVmNetworkResponse(syncHandler.getResponse());
  }

  @RpcMethod
  public MksTicketResponse getVmMksTicket(String vmId)
      throws InterruptedException, RpcException {
    SyncHandler<MksTicketResponse, Host.AsyncClient.get_mks_ticket_call> syncHandler = new SyncHandler<>();
    getVmMksTicket(vmId, syncHandler);
    syncHandler.await();
    logger.info("finished get_mks_ticket vm {}, target {}", vmId, getHostIp());
    return ResponseValidator.checkGetMksTicketResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to place a resource on a
   * host. On completion, the specified handler is invoked.
   *
   * @param resource Supplies a resource to place.
   * @param handler  Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void place(Resource resource,
                    AsyncMethodCallback<Host.AsyncClient.place_call> handler)
      throws RpcException {
    ensureClient();
    PlaceRequest placeRequest = new PlaceRequest(resource);
    clientProxy.setTimeout(PLACE_TIMEOUT_MS);
    logger.debug("place resource {}, target {}, request {}", resource, getHostIp(), placeRequest);

    try {
      clientProxy.place(placeRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to place a resource on a
   * host.
   *
   * @param resource Supplies a resource to place.
   * @return On success, the return value is the PlaceResponse object generated
   * by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public PlaceResponse place(Resource resource)
      throws InterruptedException, RpcException {
    SyncHandler<PlaceResponse, Host.AsyncClient.place_call> syncHandler = new SyncHandler<>();
    place(resource, syncHandler);
    syncHandler.await();
    logger.debug("finished place resource {}, target {}", resource, getHostIp());
    return ResponseValidator.checkPlaceResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to perform a power state
   * operation on a VM. On completion, the specified handler is invoked.
   *
   * @param vmId    Supplies the ID of a VM.
   * @param op      Supplies the power state operation to be performed.
   * @param handler Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void powerVmOp(String vmId, PowerVmOp op,
                        AsyncMethodCallback<Host.AsyncClient.power_vm_op_call> handler)
      throws RpcException {
    ensureClient();
    PowerVmOpRequest powerVmOpRequest = new PowerVmOpRequest(vmId, op);
    clientProxy.setTimeout(POWER_VM_OP_TIMEOUT_MS);
    logger.info("power_vm_op vm {}, target {}, request {}", vmId, getHostIp(), powerVmOpRequest);

    try {
      clientProxy.power_vm_op(powerVmOpRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs a synchronous Thrift call to perform a power state
   * operation on a VM.
   *
   * @param vmId Supplies the ID of a VM.
   * @param op   Supplies the power state operation to be performed.
   * @return On success, the return value is the PowerVmOpResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public PowerVmOpResponse powerVmOp(String vmId, PowerVmOp op)
      throws InterruptedException, RpcException {
    SyncHandler<PowerVmOpResponse, Host.AsyncClient.power_vm_op_call> syncHandler = new SyncHandler<>();
    powerVmOp(vmId, op, syncHandler);
    syncHandler.await();
    logger.info("finished power_vm_op vm {}, target {}", vmId, getHostIp());
    return ResponseValidator.checkPowerVmOpResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to reserve space for a
   * resource on a host. On completion, the specified handler is invoked.
   *
   * @param resource   Supplies a resource for which space should be reserved.
   * @param generation Supplies an optional generation value for the operation.
   * @param handler    Supplies a handler object to be invoked on completion.
   * @throws RpcException
   */
  @RpcMethod
  public void reserve(Resource resource, Integer generation,
                      AsyncMethodCallback<Host.AsyncClient.reserve_call> handler)
      throws RpcException {
    ensureClient();
    ReserveRequest reserveRequest = new ReserveRequest();
    reserveRequest.setResource(resource);

    if (null != generation) {
      reserveRequest.setGeneration(generation);
    }

    clientProxy.setTimeout(RESERVE_TIMEOUT_MS);
    logger.info("reserve resource {}, generation {}, target {}, request {}",
        resource, generation, getHostIp(), reserveRequest);

    try {
      clientProxy.reserve(reserveRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  /**
   * This method performs an asynchronous Thrift call to reserve space for a
   * resource on a host.
   *
   * @param resource   Supplies a resource for which space should be reserved.
   * @param generation Supplies a generation value for the operation.
   * @return On success, the return value is the ReserveResponse object
   * generated by the Thrift call.
   * @throws InterruptedException
   * @throws RpcException
   */
  @RpcMethod
  public ReserveResponse reserve(Resource resource, Integer generation)
      throws RpcException, InterruptedException {
    SyncHandler<ReserveResponse, Host.AsyncClient.reserve_call> syncHandler = new SyncHandler<>();
    reserve(resource, generation, syncHandler);
    syncHandler.await();
    logger.info("finished reserve resource {}, generation {}, target {}",
        resource, generation, getHostIp());
    return ResponseValidator.checkReserveResponse(syncHandler.getResponse());
  }

  /**
   * This method performs an asynchronous Thrift call to set an agent's mode.
   *
   * @param hostMode Supplies the mode
   * @param handler  Supplies the callback handler.
   * @throws RpcException
   */
  @RpcMethod
  public void setHostMode(HostMode hostMode, AsyncMethodCallback<Host.AsyncClient.set_host_mode_call> handler)
      throws RpcException {
    ensureClient();
    logger.info("set_host_mode_call, target {}", getHostIp());

    try {
      SetHostModeRequest setHostModeRequest = new SetHostModeRequest(hostMode);
      clientProxy.set_host_mode(setHostModeRequest, handler);
    } catch (TException e) {
      throw new RpcException(e.getMessage());
    }
  }

  public void close() {
    clientProxy = null;

    if (clientPool != null) {
      clientPool.close();
      clientPool = null;
    }
  }

  @VisibleForTesting
  protected void ensureClient() {
    if (clientProxy != null) {
      return;
    }

    close();

    createClientProxyWithIpAndPort();
  }

  @VisibleForTesting
  protected Host.AsyncClient getClientProxy() {
    return clientProxy;
  }

  @VisibleForTesting
  protected void setClientProxy(Host.AsyncClient clientProxy) {
    this.clientProxy = clientProxy;
  }

  private void createClientProxyWithIpAndPort() {
    logger.debug("Creating host async client of hostIp {} and port {}", this.getHostIp(), this.getPort());
    this.clientPool = this.clientPoolFactory.create(
        ImmutableSet.of(new InetSocketAddress(this.getHostIp(), this.getPort())),
        CLIENT_POOL_OPTIONS);
    this.clientProxy = clientProxyFactory.create(clientPool).get();
  }

  /**
   * Utility class for validating result of response.
   */
  public static class ResponseValidator {

    /**
     * This method validates a GetConfigResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param getConfigResponse Supplies a GetConfigResponse object generated by
     *                          a getHostConfig call.
     * @return On success, the return value is the GetConfigResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    public static GetConfigResponse checkGetConfigResponse(GetConfigResponse getConfigResponse)
        throws RpcException {
      logger.info("Checking {}", getConfigResponse);
      switch (getConfigResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(getConfigResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", getConfigResponse.getResult()));
      }

      return getConfigResponse;
    }

    public static void checkDeleteDiskError(DeleteDiskError error) throws RpcException {
      logger.info("started to check delete disk error");
      switch (error.getResult()) {
        case OK:
          break;
        case DISK_ATTACHED:
          throw new DiskAttachedException();
        case DISK_NOT_FOUND:
          throw new DiskNotFoundException(error.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(error.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", error.getResult()));
      }
    }

    public static void checkCreateDiskError(CreateDiskError error) throws RpcException {
      switch (error.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(error.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", error.getResult()));
      }
    }

    public static void checkVmDisksOpError(VmDiskOpError error)
        throws RpcException {
      switch (error.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(error.getError());
        case DISK_DETACHED:
          throw new DiskDetachedException();
        case DISK_ATTACHED:
          throw new DiskAttachedException();
        case VM_NOT_FOUND:
          throw new VmNotFoundException(error.getError());
        case DISK_NOT_FOUND:
          throw new DiskNotFoundException(error.getError());
        case INVALID_VM_POWER_STATE:
          throw new InvalidVmPowerStateException(error.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", error.getResult()));
      }
    }

    /**
     * This method validates a VmDisksOpResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param vmDisksOpResponse Supplies a VmDisksOpResponse object generated by
     *                          an attachDisks call.
     * @return On success, the return value is the VmDisksOpResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static VmDisksOpResponse checkAttachDisksResponse(VmDisksOpResponse vmDisksOpResponse)
        throws RpcException {
      return checkVmDisksOpResponse(vmDisksOpResponse);
    }

    /**
     * This method validates an AttachISOResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param attachISOResponse Supplies an AttachISOResponse object generated by
     *                          an attachISO call.
     * @return On success, the return value is the AttachISOResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static AttachISOResponse checkAttachISOResponse(AttachISOResponse attachISOResponse)
        throws RpcException {
      logger.info("Checking {}", attachISOResponse);
      switch (attachISOResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(attachISOResponse.getError());
        case VM_NOT_FOUND:
          throw new VmNotFoundException(attachISOResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", attachISOResponse.getResult()));
      }

      return attachISOResponse;
    }

    /**
     * This method validates a TransferImageResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param transferImageResponse Supplies a TransferImageResponse object generated by
     *                              a transfer_image call.
     * @return On success, the return value is the TransferImageResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static TransferImageResponse checkTransferImageResponse(TransferImageResponse transferImageResponse)
        throws RpcException {
      logger.info("Checking {}", transferImageResponse);
      switch (transferImageResponse.getResult()) {
        case OK:
          break;
        case TRANSFER_IN_PROGRESS:
          throw new ImageTransferInProgressException(transferImageResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(transferImageResponse.getError());
        default:
          throw new RpcException(String.format("Unexpected result: %s", transferImageResponse.getResult()));
      }

      return transferImageResponse;
    }

    /**
     * This method validates a CopyImageResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param copyImageResponse Supplies a CopyImageResponse object generated by
     *                          a copyImage call.
     * @return On success, the return value is the CopyImageResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static CopyImageResponse checkCopyImageResponse(CopyImageResponse copyImageResponse)
        throws RpcException {
      logger.info("Checking {}", copyImageResponse);
      switch (copyImageResponse.getResult()) {
        case OK:
          break;
        case IMAGE_NOT_FOUND:
          throw new ImageNotFoundException(copyImageResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(copyImageResponse.getError());
        default:
          throw new RpcException(String.format("Unexpected result: %s", copyImageResponse.getResult()));
      }

      return copyImageResponse;
    }


    /**
     * This method validates a CreateDisksResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param createDisksResponse Supplies a CreateDisksResponse object generated
     *                            by a createDisks call.
     * @return On success, the return value is the CreateDisksResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static CreateDisksResponse checkCreateDisksResponse(CreateDisksResponse createDisksResponse)
        throws RpcException {
      logger.info("Checking {}", createDisksResponse);
      switch (createDisksResponse.getResult()) {
        case OK:
          break;
        case INVALID_RESERVATION:
          throw new InvalidReservationException(createDisksResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(createDisksResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", createDisksResponse.getResult()));
      }

      return createDisksResponse;
    }

    /**
     * This method validates a CreateVmResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param createVmResponse Supplies a CreateVmResponse object generated by a
     *                         createVm call.
     * @return On success, the return value is the CreateVmResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static CreateVmResponse checkCreateVmResponse(CreateVmResponse createVmResponse)
        throws RpcException {
      logger.info("Checking {}", createVmResponse);
      switch (createVmResponse.getResult()) {
        case OK:
          break;
        case DISK_NOT_FOUND:
          throw new DiskNotFoundException(createVmResponse.getError());
        case IMAGE_NOT_FOUND:
          throw new ImageNotFoundException(createVmResponse.getError());
        case INVALID_RESERVATION:
          throw new InvalidReservationException(createVmResponse.getError());
        case NETWORK_NOT_FOUND:
          throw new NetworkNotFoundException(createVmResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(createVmResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", createVmResponse.getResult()));
      }

      return createVmResponse;
    }

    /**
     * This method validates a DeleteDisksResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param deleteDisksResponse Supplies a DeleteDisksResponse object generated
     *                            by a deleteDisks call.
     * @return On success, the return value is the DeleteDisksResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static DeleteDisksResponse checkDeleteDisksResponse(DeleteDisksResponse deleteDisksResponse)
        throws RpcException {
      logger.info("Checking {}", deleteDisksResponse);
      switch (deleteDisksResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(deleteDisksResponse.getError());
        default:
          throw new RpcException(String.format("Unknown response: %s", deleteDisksResponse.getResult()));
      }

      return deleteDisksResponse;
    }

    private static FinalizeImageResponse checkFinalizeImageResponse(FinalizeImageResponse finalizeImageResponse)
        throws RpcException {
      logger.info("Checking {}", finalizeImageResponse);
      switch (finalizeImageResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(finalizeImageResponse.getError());
        case IMAGE_NOT_FOUND:
          throw new ImageNotFoundException(finalizeImageResponse.getError());
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(finalizeImageResponse.getError());
        case DESTINATION_ALREADY_EXIST:
          throw new DestinationAlreadyExistException(finalizeImageResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", finalizeImageResponse.getResult()));
      }

      return finalizeImageResponse;
    }

    /**
     * This method validates a DeleteImageResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param deleteImageResponse Supplies a DeleteImageResponse object generated
     *                            by a deleteImage call.
     * @return On success, the return value is the DeleteImageResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static DeleteImageResponse checkDeleteImageResponse(DeleteImageResponse deleteImageResponse)
        throws RpcException {
      logger.info("Checking {}", deleteImageResponse);
      switch (deleteImageResponse.getResult()) {
        case OK:
          break;
        case IMAGE_IN_USE:
          throw new ImageInUseException(deleteImageResponse.getError());
        case IMAGE_NOT_FOUND:
          throw new ImageNotFoundException(deleteImageResponse.getError());
        case INVALID_REF_COUNT_FILE:
          throw new ImageRefCountFileException(deleteImageResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(deleteImageResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", deleteImageResponse.getResult()));
      }

      return deleteImageResponse;
    }

    /**
     * This method validates a CreateImageFromVmResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param createImageFromVmResponse
     * @return
     * @throws RpcException
     */
    private static CreateImageFromVmResponse checkCreateImageResponse(
        CreateImageFromVmResponse createImageFromVmResponse)
        throws RpcException {
      logger.info("Checking {}", createImageFromVmResponse);
      switch (createImageFromVmResponse.getResult()) {
        case OK:
          break;
        case INVALID_VM_POWER_STATE:
          throw new InvalidVmPowerStateException(createImageFromVmResponse.getError());
        case IMAGE_ALREADY_EXIST:
          throw new ImageAlreadyExistException(createImageFromVmResponse.getError());
        case VM_NOT_FOUND:
          throw new VmNotFoundException(createImageFromVmResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(createImageFromVmResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", createImageFromVmResponse.getResult()));
      }

      return createImageFromVmResponse;
    }

    private static DeleteDirectoryResponse checkDeleteDirectoryResponse(DeleteDirectoryResponse deleteDirectoryResponse)
        throws RpcException {
      logger.info("Checking {}", deleteDirectoryResponse);
      switch (deleteDirectoryResponse.getResult()) {
        case OK:
          break;
        case DIRECTORY_NOT_FOUND:
          throw new DirectoryNotFoundException(deleteDirectoryResponse.getError());
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(deleteDirectoryResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(deleteDirectoryResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", deleteDirectoryResponse.getError()));
      }

      return deleteDirectoryResponse;
    }

    /**
     * This method validates a DeleteVmResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param deleteVmResponse Supplies a DeleteVmResponse object generated by a
     *                         deleteVm call.
     * @return On success, the return value is the DeleteVmResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static DeleteVmResponse checkDeleteVmResponse(DeleteVmResponse deleteVmResponse)
        throws RpcException {
      logger.info("Checking {}", deleteVmResponse);
      switch (deleteVmResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(deleteVmResponse.getError());
        case VM_NOT_FOUND:
          throw new VmNotFoundException(deleteVmResponse.getError());
        case VM_NOT_POWERED_OFF:
          throw new VmNotPoweredOffException(deleteVmResponse.getError());
        default:
          throw new RpcException(String.format("Unknown response: %s", deleteVmResponse.getResult()));
      }

      return deleteVmResponse;
    }

    /**
     * This method validates a VmDisksOpResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param vmDisksOpResponse Supplies a VmDisksOpResponse object generated by
     *                          a detachDisks call.
     * @return On success, the return value is the VmDisksOpResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static VmDisksOpResponse checkDetachDisksResponse(VmDisksOpResponse vmDisksOpResponse)
        throws RpcException {
      return checkVmDisksOpResponse(vmDisksOpResponse);
    }

    /**
     * This method validates a DetachISOResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param detachISOResponse Supplies a DetachISOResponse object generated by
     *                          a detachISO call.
     * @return On success, the return value is the DetachISOResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static DetachISOResponse checkDetachISOResponse(DetachISOResponse detachISOResponse)
        throws RpcException {
      logger.info("Checking {}", detachISOResponse);
      switch (detachISOResponse.getResult()) {
        case OK:
          break;
        case ISO_NOT_ATTACHED:
          logger.warn("detach_iso returned IsoNotAttachedException, response: {}", detachISOResponse);
          throw new IsoNotAttachedException(detachISOResponse.getError());
        case SYSTEM_ERROR:
          logger.warn("detach_iso returned SYSTEM_ERROR, response: {}", detachISOResponse);
          throw new SystemErrorException(detachISOResponse.getError());
        case VM_NOT_FOUND:
          logger.warn("detach_iso returned VmNotFoundException, response: {}", detachISOResponse);
          throw new VmNotFoundException(detachISOResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", detachISOResponse.getResult()));
      }

      return detachISOResponse;
    }

    /**
     * This method validates a FindResponse object, raising an exception if the
     * response reflects an operation failure.
     *
     * @param findResponse Supplies a FindResponse object generated by a findDisk
     *                     call.
     * @return If the operation completed successfully and the VM or disk was
     * found, then the return value is true. If the operation completed
     * successfully and the VM or disk was not found, then the return value is
     * false.
     * @throws RpcException
     */
    private static boolean checkFindDiskResponse(FindResponse findResponse)
        throws RpcException {
      return checkFindResponse(findResponse);
    }

    /**
     * This method validates a FindResponse object, raising an exception if the
     * response reflects an operation failure.
     *
     * @param findResponse Supplies a FindResponse object generated by a findVm
     *                     call.
     * @return If the operation completed successfully and the VM or disk was
     * found, then the return value is true. If the operation completed
     * successfully and the VM or disk was not found, then the return value is
     * false.
     * @throws RpcException
     */
    private static boolean checkFindVmResponse(FindResponse findResponse)
        throws RpcException {
      return checkFindResponse(findResponse);
    }

    /**
     * This method validates an ImageInfoResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param imageInfoResponse Supplies an ImageInfoResponse object generated by
     *                          a getImageInfo call.
     * @return On success, the return value is the ImageInfoResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static ImageInfoResponse checkImageInfoResponse(ImageInfoResponse imageInfoResponse)
        throws RpcException {
      logger.info("Checking {}", imageInfoResponse);
      switch (imageInfoResponse.getResult()) {
        case OK:
          break;
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(imageInfoResponse.getError());
        case IMAGE_NOT_FOUND:
          throw new ImageNotFoundException(imageInfoResponse.getError());
        case INVALID_REF_COUNT_FILE:
          throw new ImageRefCountFileException(imageInfoResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(imageInfoResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", imageInfoResponse.getResult()));
      }

      return imageInfoResponse;
    }

    /**
     * This method validates a GetImagesResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param getImagesResponse Supplies a GetImagesResponse object generated by
     *                          a getImages call.
     * @return On success, the return value is the GetImagesResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static GetImagesResponse checkGetImagesResponse(GetImagesResponse getImagesResponse)
        throws RpcException {
      logger.info("Checking {}", getImagesResponse);
      switch (getImagesResponse.getResult()) {
        case OK:
          break;
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(getImagesResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(getImagesResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", getImagesResponse.getResult()));
      }

      return getImagesResponse;
    }

    /**
     * This method validates a ServiceTicketResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param serviceTicketResponse Supplies a ServiceTicketResponse object
     *                              generated by a getNfcServiceTicket call.
     * @return On success, the return value is the ServiceTicketResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static ServiceTicketResponse checkGetNfcServiceTicketResponse(ServiceTicketResponse serviceTicketResponse)
        throws RpcException {
      logger.info("Checking {}", serviceTicketResponse);
      switch (serviceTicketResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(serviceTicketResponse.getError());
        case NOT_FOUND:
          throw new DatastoreNotFoundException(serviceTicketResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", serviceTicketResponse.getError()));
      }

      return serviceTicketResponse;
    }

    /**
     * This method validates a GetVmNetworkResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param getVmNetworkResponse Supplies a GetVmNetworkResponse object
     *                             generated by a getVmNetworks call.
     * @return On success, the return value is the GetVmNetworkResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static GetVmNetworkResponse checkGetVmNetworkResponse(GetVmNetworkResponse getVmNetworkResponse)
        throws RpcException {
      logger.info("Checking {}", getVmNetworkResponse);
      switch (getVmNetworkResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(getVmNetworkResponse.getError());
        case VM_NOT_FOUND:
          throw new VmNotFoundException(getVmNetworkResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", getVmNetworkResponse.getResult()));
      }

      return getVmNetworkResponse;
    }

    /**
     * This method validates a MksTicketResponse object, raising an exception
     * if the response reflects an operation failure.
     *
     * @param mksTicketResponse Supplies a MksTicketResponse object
     *                          generated by a getVmMksTicket call.
     * @return On success, the return value is the MksTicketResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static MksTicketResponse checkGetMksTicketResponse(MksTicketResponse mksTicketResponse)
        throws RpcException {
      logger.info("Checking {}", mksTicketResponse);
      switch (mksTicketResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(mksTicketResponse.getError());
        case VM_NOT_FOUND:
          throw new VmNotFoundException(mksTicketResponse.getError());
        case INVALID_VM_POWER_STATE:
          throw new InvalidVmPowerStateException(mksTicketResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", mksTicketResponse.getResult()));
      }

      return mksTicketResponse;
    }

    /**
     * This method validates a PlaceResponse object, raising an exception if the
     * response reflects an operation failure.
     *
     * @param placeResponse Supplies a PlaceResponse object generated by a place
     *                      call.
     * @return On success, the return value is the PlaceResponse object specified
     * as a parameter.
     * @throws RpcException
     */
    private static PlaceResponse checkPlaceResponse(PlaceResponse placeResponse)
        throws RpcException {
      logger.debug("Checking {}", placeResponse);
      switch (placeResponse.getResult()) {
        case OK:
          break;
        case NOT_LEADER:
          throw new RpcException(placeResponse.getError());
        case NO_SUCH_RESOURCE:
          throw new NoSuchResourceException(placeResponse.getError());
        case NOT_ENOUGH_CPU_RESOURCE:
          throw new NotEnoughCpuResourceException(placeResponse.getError());
        case NOT_ENOUGH_MEMORY_RESOURCE:
          throw new NotEnoughMemoryResourceException(placeResponse.getError());
        case NOT_ENOUGH_DATASTORE_CAPACITY:
          throw new NotEnoughDatastoreCapacityException(placeResponse.getError());
        case RESOURCE_CONSTRAINT:
          throw new ResourceConstraintException(placeResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(placeResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", placeResponse.getResult()));
      }

      return placeResponse;
    }

    /**
     * This method validates a PowerVmOpResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param powerVmOpResponse Supplies a PowerVmOpResponse object generated by
     *                          a powerVmOp operation.
     * @return On success, the return value is the PowerVmOpResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static PowerVmOpResponse checkPowerVmOpResponse(PowerVmOpResponse powerVmOpResponse)
        throws RpcException {
      logger.info("Checking {}", powerVmOpResponse);
      switch (powerVmOpResponse.getResult()) {
        case OK:
          break;
        case INVALID_VM_POWER_STATE:
          throw new InvalidVmPowerStateException(powerVmOpResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(powerVmOpResponse.getError());
        case VM_NOT_FOUND:
          throw new VmNotFoundException(powerVmOpResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", powerVmOpResponse.getResult()));
      }

      return powerVmOpResponse;
    }

    /**
     * This method validates a ReserveResponse object, raising an exception if
     * the response reflects an operation failure.
     *
     * @param reserveResponse Supplies a ReserveResponse object generated by a
     *                        reserve call.
     * @return On success, the return value is the ReserveResponse object
     * specified as a parameter.
     * @throws RpcException
     */
    private static ReserveResponse checkReserveResponse(ReserveResponse reserveResponse)
        throws RpcException {
      logger.info("Checking {}", reserveResponse);
      switch (reserveResponse.getResult()) {
        case OK:
          break;
        case STALE_GENERATION:
          logger.warn("Reserving resource failed with error: {}", reserveResponse.getError());
          throw new StaleGenerationException(reserveResponse.getError());
        case SYSTEM_ERROR:
          logger.warn("Reserving resource failed with error: {}", reserveResponse.getError());
          throw new SystemErrorException(reserveResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", reserveResponse.getResult()));
      }

      return reserveResponse;
    }

    private static VmDisksOpResponse checkVmDisksOpResponse(VmDisksOpResponse vmDisksOpResponse)
        throws RpcException {
      logger.info("Checking {}", vmDisksOpResponse);
      switch (vmDisksOpResponse.getResult()) {
        case OK:
          break;
        case INVALID_VM_POWER_STATE:
          throw new InvalidVmPowerStateException(vmDisksOpResponse.getError());
        case SYSTEM_ERROR:
          throw new SystemErrorException(vmDisksOpResponse.getError());
        case VM_NOT_FOUND:
          throw new VmNotFoundException(vmDisksOpResponse.getError());
        default:
          throw new RpcException(String.format("Unexpected result: %s", vmDisksOpResponse.getResult()));
      }

      return vmDisksOpResponse;
    }

    private static boolean checkFindResponse(FindResponse findResponse)
        throws RpcException {
      logger.info("Checking {}", findResponse);
      switch (findResponse.getResult()) {
        case OK:
          return true;
        case NOT_FOUND:
          return false;
        case SYSTEM_ERROR:
          throw new SystemErrorException(findResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", findResponse.getResult()));
      }
    }

    /**
     * This method validates a SetHostModeResponse object, raising an
     * exception if the response reflects an operation failure.
     *
     * @param setHostModeResponse Supplies a SetHostModeResponse object
     *                            generated by an SetHostMode call.
     * @return On success, the return value is the SetHostModeResponse
     * object specified as a parameter.
     * @throws RpcException
     */

    public static SetHostModeResponse checkSetHostModeResponse(SetHostModeResponse setHostModeResponse)
        throws RpcException {
      logger.info("Checking {}", setHostModeResponse);
      switch (setHostModeResponse.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(setHostModeResponse.getError());
        default:
          throw new RpcException(String.format("Unknown result: %s", setHostModeResponse.getResult()));
      }
      return setHostModeResponse;
    }

    /**
     * Validates a StartImageScanResponse object, raising an
     * exception if the response code is not 'OK'.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    public static StartImageScanResponse checkStartImageScanResponse(StartImageScanResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getError());
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(response.getError());
        case SCAN_IN_PROGRESS:
          throw new ScanInProgressException(response.getError());
        case SWEEP_IN_PROGRESS:
          throw new SweepInProgressException(response.getError());
        default:
          throw new RpcException(String.format("Unexpected return code: %s", response.getResult()));
      }

      return response;
    }

    /**
     * Validates a GetInactiveImagesResponse object, raising an
     * exception if the response code is not 'OK'.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    public static GetInactiveImagesResponse checkGetInactiveImagesResponse(GetInactiveImagesResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getError());
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(response.getError());
        case OPERATION_IN_PROGRESS:
          throw new OperationInProgressException(response.getError());
        default:
          throw new RpcException(String.format("Unexpected return code: %s", response.getResult()));
      }

      return response;
    }

    /**
     * Validates a StartImageSweepResponse object, raising an
     * exception if the response code is not 'OK'.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    public static StartImageSweepResponse checkStartImageSweepResponse(StartImageSweepResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getError());
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(response.getError());
        case SCAN_IN_PROGRESS:
          throw new ScanInProgressException(response.getError());
        case SWEEP_IN_PROGRESS:
          throw new SweepInProgressException(response.getError());
        default:
          throw new RpcException(String.format("Unexpected return code: %s", response.getResult()));
      }

      return response;
    }

    /**
     * Validates a GetDeletedImagesResponse object, raising an
     * exception if the response code is not 'OK'.
     *
     * @param response
     * @return
     * @throws RpcException
     */
    public static GetDeletedImagesResponse checkGetDeletedImagesResponse(GetDeletedImagesResponse response)
        throws RpcException {
      logger.info("Checking {}", response);
      switch (response.getResult()) {
        case OK:
          break;
        case SYSTEM_ERROR:
          throw new SystemErrorException(response.getError());
        case DATASTORE_NOT_FOUND:
          throw new DatastoreNotFoundException(response.getError());
        case OPERATION_IN_PROGRESS:
          throw new OperationInProgressException(response.getError());
        default:
          throw new RpcException(String.format("Unexpected return code: %s", response.getResult()));
      }

      return response;
    }
  }

  /**
   * Class for general utility functions.
   */
  private static class Util {

    private static Image constructImage(String datastoreId, String imageId) {
      Datastore datastore = new Datastore();
      datastore.setId(datastoreId);
      Image image = new Image();
      image.setDatastore(datastore);
      image.setId(imageId);
      return image;
    }
  }
}
