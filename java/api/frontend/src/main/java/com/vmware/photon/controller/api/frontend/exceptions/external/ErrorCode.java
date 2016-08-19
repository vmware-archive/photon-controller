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

package com.vmware.photon.controller.api.frontend.exceptions.external;

import javax.ws.rs.core.Response;

/**
 * Error code and HTTP status associated with an exception.
 */
public enum ErrorCode {
  INTERNAL_ERROR("InternalError"),
  SYSTEM_PAUSED("SystemPaused", Response.Status.FORBIDDEN),
  CONFIGURATION_NOT_FOUND("ConfigurationNotFound", Response.Status.NOT_FOUND),
  QUOTA_ERROR("QuotaError", Response.Status.BAD_REQUEST),
  STATE_ERROR("StateError", Response.Status.BAD_REQUEST),
  PLACE_VM_ERROR("PlaceVmError", Response.Status.BAD_REQUEST),
  DISK_NOT_FOUND("DiskNotFound", Response.Status.NOT_FOUND),
  RESOURCE_TICKET_NOT_FOUND("ResourceTicketNotFound", Response.Status.NOT_FOUND),
  FLAVOR_NOT_FOUND("FlavorNotFound", Response.Status.NOT_FOUND),
  INVALID_FLAVOR("InvalidFlavor", Response.Status.BAD_REQUEST),
  FLAVOR_IN_USE("FlavorInUse", Response.Status.BAD_REQUEST),
  AVAILABILITYZONE_NOT_FOUND("AvailabilityZoneNotFound", Response.Status.NOT_FOUND),
  INVALID_AVAILABILITYZONE_STATE("InvalidAvailabilityZoneState", Response.Status.BAD_REQUEST),
  ISO_ALREADY_ATTACHED("IsoAlreadyAttached", Response.Status.BAD_REQUEST),
  MORE_THAN_ONE_ISO_ATTACHED("MoreThanOneIsoAttached", Response.Status.BAD_REQUEST),
  NO_ISO_ATTACHED("NoIsoAttached", Response.Status.BAD_REQUEST),
  CONTAINER_NOT_EMPTY("ContainerNotEmpty", Response.Status.BAD_REQUEST),
  VM_NOT_FOUND("VmNotFound", Response.Status.NOT_FOUND),
  CONCURRENT_TASK("ConcurrentTask", Response.Status.BAD_REQUEST),
  NAME_TAKEN("NameTaken", Response.Status.BAD_REQUEST),
  INVALID_ENTITY("InvalidEntity", Response.Status.BAD_REQUEST),
  INVALID_JSON("InvalidJson", Response.Status.BAD_REQUEST),
  TOO_MANY_REQUESTS("TooManyRequests", 429),
  NOT_FOUND("NotFound", Response.Status.NOT_FOUND),
  INSUFFICIENT_CAPACITY("InsufficientCapacity", Response.Status.BAD_REQUEST),
  NO_SUCH_RESOURCE("NoSuchResource", Response.Status.BAD_REQUEST),
  NO_CONSTRAINT_MATCHING_DATASTORE("NoConstraintMatchingDatastore", Response.Status.BAD_REQUEST),
  NOT_ENOUGH_CPU_RESOURCE("NotEnoughCpuResource", Response.Status.BAD_REQUEST),
  NOT_ENOUGH_MEMORY_RESOURCE("NotEnoughMemoryResource", Response.Status.BAD_REQUEST),
  NOT_ENOUGH_DATASTORE_CAPACITY("NotEnoughDatastoreCapacity", Response.Status.BAD_REQUEST),
  UNFULLFILLABLE_AFFINITIES("UnfullfillableAffinities", Response.Status.BAD_REQUEST),
  UNFULLFILLABLE_DISK_AFFINITIES("UnfullfillableDiskAffinities", Response.Status.BAD_REQUEST),
  MORE_THAN_ONE_HOST_AFFINITY("MoreThanOneHostAffinity", Response.Status.BAD_REQUEST),
  INVALID_VM_STATE("InvalidVmState", Response.Status.BAD_REQUEST),
  INVALID_HOST_STATE("InvalidHostState", Response.Status.BAD_REQUEST),
  INVALID_OVA("InvalidOva", Response.Status.BAD_REQUEST),
  INVALID_VMDK_FORMAT("InvalidVmdkFormat", Response.Status.BAD_REQUEST),
  INVALID_OPERATION("InvalidOperation", Response.Status.BAD_REQUEST),
  IMAGE_UPLOAD_ERROR("ImageUploadError", Response.Status.BAD_REQUEST),
  ISO_UPLOAD_ERROR("IsoUploadError", Response.Status.BAD_REQUEST),
  UNSUPPORTED_IMAGE_FILE_TYPE("UnsupportedImageFileType", Response.Status.BAD_REQUEST),
  UNSUPPORTED_DISK_CONTROLLER("UnsupportedDiskController", Response.Status.BAD_REQUEST),
  UNSUPPORTED_IMAGE_UPLOAD_PARAMETER("UnsupportedImageUploadParameter", Response.Status.BAD_REQUEST),
  INVALID_IMAGE_STATE("InvalidImageState", Response.Status.BAD_REQUEST),
  INVALID_IMAGE_REPLICATION_TYPE("InvalidImageReplicationType", Response.Status.BAD_REQUEST),
  INVALID_RESOURCE_TICKET_SUBDIVIDE("InvalidResourceTicketSubdivide", Response.Status.BAD_REQUEST),
  NETWORK_SPEC_NOT_FOUND("NetworkSpecNotFound", Response.Status.NOT_FOUND),
  NETWORK_NOT_FOUND("NetworkNotFound", Response.Status.NOT_FOUND),
  PORT_GROUP_NOT_FOUND("PortGroupNotFound", Response.Status.NOT_FOUND),
  STORAGE_SPEC_NOT_FOUND("StorageSpecNotFound", Response.Status.NOT_FOUND),
  DATASTORE_NOT_FOUND("DatastoreNotFound", Response.Status.NOT_FOUND),
  SPEC_INVALID("SpecInvalid", Response.Status.BAD_REQUEST),
  STEP_NOT_FOUND("StepNotFound", Response.Status.NOT_FOUND),
  STEP_NOT_COMPLETED("StepNotCompleted"),
  HOST_SPEC_NOT_FOUND("HostSpecNotFound", Response.Status.NOT_FOUND),
  HOST_NOT_FOUND("HostNotFound", Response.Status.NOT_FOUND),
  HOST_HAS_VMS("HostHasVms", Response.Status.BAD_REQUEST),
  HOST_DATASTORE_NOT_FOUND("HostDatastoreNotFound", Response.Status.NOT_FOUND),
  HOST_AVAILABILITYZONE_ALREADY_SET("HostAvailabilityZoneAlreadySet", Response.Status.BAD_REQUEST),
  HOST_AVAILABILITYZONE_SET_FAILED("HostSetAvailabilityZoneFailed", Response.Status.INTERNAL_SERVER_ERROR),
  DEPLOYMENT_FLAVOR_NOT_FOUND("DeploymentFlavorNotFound", Response.Status.NOT_FOUND),
  DEPLOYMENT_NOT_FOUND("DeploymentNotFound", Response.Status.NOT_FOUND),
  UPDATE_NOT_SUPPORTED("UpdateNotSupported", Response.Status.BAD_REQUEST),
  TASK_NOT_COMPLETED("TaskNotCompleted"),
  PLUGIN_NOT_FOUND("PluginNotFound", Response.Status.NOT_FOUND),
  PERSISTENT_DISK_ATTACHED("PersistentDiskAttached", Response.Status.BAD_REQUEST),
  YAML_PARSE_ERROR("YamlParseError", Response.Status.BAD_REQUEST),
  VCENTER_IMPORT_ERROR("VCenterImportError", Response.Status.INTERNAL_SERVER_ERROR),
  VCENTER_REQUEST_ERROR("VCenterRequestError", Response.Status.BAD_REQUEST),
  OPENSTACK_IMPORT_ERROR("OpenStackImportError", Response.Status.INTERNAL_SERVER_ERROR),
  OPENSTACK_REQUEST_ERROR("OpenStackRequestError", Response.Status.BAD_REQUEST),
  IMAGE_NOT_FOUND("ImageNotFound", Response.Status.NOT_FOUND),
  MANIFEST_NOT_FOUND("ManifestNotFound", Response.Status.NOT_FOUND),
  DEPLOYMENT_TRIGGER_FAILURE("DeploymentTriggerFailure", Response.Status.INTERNAL_SERVER_ERROR),
  INVALID_CONFIG_KEY("InvalidConfigKey", Response.Status.BAD_REQUEST),
  CONFIG_PAIR_NOT_FOUND("ConfigPairNotFound", Response.Status.NOT_FOUND),
  OUT_OF_THREAD_POOL_WORKER("OutOfThreadPoolWorker", Response.Status.SERVICE_UNAVAILABLE),
  AUTH_INITIALIZATION_FAILURE("AuthInitializationFailure", Response.Status.SERVICE_UNAVAILABLE),
  MISSING_AUTH_TOKEN("MissingAuthToken", Response.Status.UNAUTHORIZED),
  MALFORMED_AUTH_TOKEN("MalformedAuthToken", Response.Status.BAD_REQUEST),
  INVALID_AUTH_TOKEN("InvalidAuthToken", Response.Status.UNAUTHORIZED),
  EXPIRED_AUTH_TOKEN("ExpiredAuthToken", Response.Status.UNAUTHORIZED),
  ACCESS_FORBIDDEN("AccessForbidden", Response.Status.FORBIDDEN),
  DEPLOYMENT_FAILED("DeploymentFailed", Response.Status.BAD_REQUEST),
  HOST_PROVISION_FAILED("HostProvisionFailed", Response.Status.INTERNAL_SERVER_ERROR),
  HOST_DEPROVISION_FAILED("HostDeprovisionFailed", Response.Status.INTERNAL_SERVER_ERROR),
  INVALID_QUERY_PARAMS("InvalidQueryParams", Response.Status.BAD_REQUEST),
  DEPLOYMENT_ALREADY_EXIST("DeploymentAlreadyExist", Response.Status.BAD_REQUEST),
  DUPLICATE_HOST("DuplicateHost", Response.Status.BAD_REQUEST),
  NO_MANAGEMENT_HOST_CONFIGURED("NoManagementHostConfigured", Response.Status.BAD_REQUEST),
  INVALID_AUTH_CONFIG("InvalidAuthConfig", Response.Status.BAD_REQUEST),
  INVALID_STATS_CONFIG("InvalidStatsConfig", Response.Status.BAD_REQUEST),
  INVALID_SECURITY_GROUP_FORMAT("InvalidSecurityGroupFormat", Response.Status.BAD_REQUEST),
  INVALID_FLAVOR_SPECIFICATION("InvalidFlavorSpecification", Response.Status.BAD_REQUEST),
  INVALID_FLAVOR_STATE("InvalidFlavorState", Response.Status.BAD_REQUEST),
  INVALID_NETWORK_STATE("InvalidNetworkState", Response.Status.BAD_REQUEST),
  INVALID_NETWORK_CONFIG("InvalidNetworkConfig", Response.Status.BAD_REQUEST),
  INVALID_RESERVED_STATIC_IP_SIZE("InvalidReservedStaticIpSize", Response.Status.BAD_REQUEST),
  INVALID_LOGIN("InvalidLoginCredentials", Response.Status.BAD_REQUEST),
  IP_ADDRESS_IN_USE("IpAddressInUse", Response.Status.BAD_REQUEST),
  PORT_GROUP_ALREADY_ADDED_TO_SUBNET("PortGroupAlreadyAddedToSubnet", Response.Status.BAD_REQUEST),
  PORT_GROUPS_DO_NOT_EXIST("PortGroupsDoNotExist", Response.Status.BAD_REQUEST),
  CLUSTER_NOT_FOUND("ClusterNotFound", Response.Status.NOT_FOUND),
  SECURITY_GROUPS_ALREADY_INHERITED("SecurityGroupsAlreadyInherited", Response.Status.BAD_REQUEST),
  CLUSTER_TYPE_ALREADY_CONFIGURED("ClusterTypeAlreadyConfigured", Response.Status.BAD_REQUEST),
  CLUSTER_TYPE_NOT_CONFIGURED("ClusterTypeNotConfigured", Response.Status.NOT_FOUND),
  PAGE_EXPIRED("PageExpired", Response.Status.NOT_FOUND),
  INVALID_PAGE_SIZE("InvalidPageSize", Response.Status.BAD_REQUEST),
  INVALID_IMAGE_DATASTORE_SET("InvalidImageDatastoreSet", Response.Status.BAD_REQUEST),
  INVALID_DEPLOYMENT_DESIRED_STATE("InvalidDeploymentDesiredState", Response.Status.BAD_REQUEST);

  private final String code;
  private final int httpStatus;

  private ErrorCode(String errorCode) {
    this(errorCode, Response.Status.INTERNAL_SERVER_ERROR);
  }

  private ErrorCode(String errorCode, Response.Status httpStatus) {
    this.code = errorCode;
    this.httpStatus = httpStatus.getStatusCode();
  }

  private ErrorCode(String errorCode, int httpStatus) {
    this.code = errorCode;
    this.httpStatus = httpStatus;
  }

  public String getCode() {
    return code;
  }

  public int getHttpStatus() {
    return httpStatus;
  }
}
