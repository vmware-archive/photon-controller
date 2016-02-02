package com.vmware.photon.controller.apife.entities;

import java.util.Objects;

/**
 * Represents a datastore mount to a host.
 */
public class HostDatastoreEntity {
  private String datastoreId;
  private String mountPoint;
  private boolean imageDatastore;

  public HostDatastoreEntity() {
  }

  public String getDatastoreId() {
    return this.datastoreId;
  }

  public void setDatastoreId(String datastoreId) {
    this.datastoreId = datastoreId;
  }

  public String getMountPoint() {
    return mountPoint;
  }

  public void setMountPoint(String mountPoint) {
    this.mountPoint = mountPoint;
  }

  public boolean isImageDatastore() {
    return imageDatastore;
  }

  public void setImageDatastore(boolean imageDatastore) {
    this.imageDatastore = imageDatastore;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    HostDatastoreEntity other = (HostDatastoreEntity) o;

    return Objects.equals(this.getDatastoreId(), other.getDatastoreId())
        && Objects.equals(this.getMountPoint(), other.getMountPoint())
        && this.isImageDatastore() == other.isImageDatastore();
  }
}
