package com.vmware.photon.controller.apife.entities;

import java.util.Objects;

/**
 * Represents a datastore mount to a host.
 */
public class HostDatastoreEntity {
  private String id;
  private String mountPoint;
  private boolean imageDatastore;

  public HostDatastoreEntity() {
  }

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
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

    return Objects.equals(this.getId(), other.getId())
        && Objects.equals(this.getMountPoint(), other.getMountPoint())
        && this.isImageDatastore() == other.isImageDatastore();
  }
}
