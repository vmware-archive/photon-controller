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

package com.vmware.photon.controller.apife.lib.ova.ovf;

import com.vmware.photon.controller.apife.exceptions.internal.InvalidOvfException;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;


/**
 * Class representing the OVF description of a VM hardware item.
 * <p/>
 * Hardware <Item> elements:
 * |ElementName         |Required field that contains a display-friendly message about the content of the RASD
 * |Description         |Human-readable description
 * |InstanceID          |Required field that contains unique ID within this <VirtualHardwareSection>
 * |ResourceType        |Required field that indicates the kind of resource
 * |ResourceSubType     |A vendor-specific identifier for specific devices
 * |VirtualQuantity     |Specifies the amount (used for memory and CPU)
 * |AllocationUnits     |Specifies the unit of resource (used for memory and CPU)
 * |Reservation         |Specifies resource allocation policy (CPU and memory)
 * |Limit               |Specifies resource allocation policy (CPU and memory)
 * |Weight              |Specifies resource allocation policy (CPU and memory)
 * |Address             |Typically used as the unit number of a controller
 * |Parent              |Instance ID of parent controller (for devices on a controller)
 * |AddressOnParent     |Used to specify the order for devices on a controller
 * |AutomaticAllocation |Used to specify whether a device should be connected on power-on (e.g., for a CDROM)
 * |Connection          |Reference to a network for an ethernet adaptor
 * |HostResource        |Reference to a virtual disk for a disk drive
 * <p/>
 * <ResourceType> corresponding devices:
 * |Kind              |ResourceType
 * |Other             |0
 * |Processor         |3
 * |Memory            |4
 * |IDE Controller    |5
 * |SCSI Controller   |6
 * |Ethernet Adapter  |10
 * |Floppy Drive      |14
 * |CD/DVD Drive      |15/16
 * |Disk Drive        |17
 * |SerialPort        |21
 * |USB Controller    |23
 */
public class Device extends OvfFile.OvfElement {

  // Hide constructor outside assembly.
  Device(OvfFile ovfFile, Element element) {
    super(ovfFile, element);
  }

  /**
   * Device item properties.
   */
  public String getDiskHostResource() throws InvalidOvfException {
    return getElementText("rasd:HostResource");
  }

  public String getName() throws InvalidOvfException {
    return getElementText("rasd:ElementName");
  }

  public String getDescription() throws InvalidOvfException {
    return getElementText("rasd:Description");
  }

  public String getInstanceId() throws InvalidOvfException {
    return getElementText("rasd:InstanceID");
  }

  public String getControllerInstanceId() throws InvalidOvfException {
    return getElementText("rasd:Parent");
  }

  public String getDeviceSubType() throws InvalidOvfException {
    return getElementText("rasd:ResourceSubType");
  }

  public String getControllerUnitNumber() throws InvalidOvfException {
    return getElementText("rasd:Address");
  }

  public String getOrdinalOnController() throws InvalidOvfException {
    return getElementText("rasd:AddressOnParent");
  }

  public String getNetworkConnection() throws InvalidOvfException {
    return getElementText("rasd:Connection");
  }

  public String getResourceType() throws InvalidOvfException {
    return getElementText("rasd:ResourceType");
  }

  /**
   * Get device specific configuration.
   *
   * @return
   */
  public Map<String, String> getConfig() throws InvalidOvfException {
    Map<String, String> map = new HashMap<>();
    NodeList nodes = element.getElementsByTagName("vmw:Config");
    try {
      for (int i = 0; i < nodes.getLength(); i++) {
        NamedNodeMap attributes = nodes.item(i).getAttributes();
        String key = attributes.getNamedItem("vmw:key").getNodeValue();
        String value = attributes.getNamedItem("vmw:value").getNodeValue();
        map.put(key, value);
      }
    } catch (NullPointerException e) {
      throw new InvalidOvfException("Failed to read config: corrupt OVF.");
    }
    return map;
  }

  /**
   * For a controlled device, get controller device.
   *
   * @return
   */
  public Device getControllerDevice() throws InvalidOvfException {
    String controllerRef = getControllerInstanceId();
    for (Device item : ovfFile.getDevices()) {
      if (controllerRef.equals(item.getInstanceId())) {
        return item;
      }
    }
    return null;
  }

  /**
   * Get the device type as an ENUM.
   *
   * @return
   */
  public DeviceType getDeviceType() throws InvalidOvfException {
    int resourceType = Integer.parseInt(getResourceType());
    for (DeviceType deviceType : DeviceType.values()) {
      if (deviceType.getResourceType() == resourceType) {
        return deviceType;
      }
    }
    return DeviceType.Other;
  }

  private String getElementText(String tagName) throws InvalidOvfException {
    NodeList nodes = element.getElementsByTagName(tagName);
    if (nodes.getLength() != 1) {
      throw new InvalidOvfException(String.format("Element %s not found.", tagName));
    }
    return nodes.item(0).getTextContent();
  }

  /**
   * Possible device types.
   */
  public enum DeviceType {
    Other(0),
    Processor(3),
    Memory(4),
    IDEController(5),
    SCSIController(6),
    EthernetAdapter(10),
    FloppyDrive(14),
    CDDrive(15),
    DVDDrive(16),
    DiskDrive(17),
    SerialPort(21),
    USBController(23);

    int resourceType;

    private DeviceType(int resourceType) {
      this.resourceType = resourceType;
    }

    public int getResourceType() {
      return resourceType;
    }
  }
}
