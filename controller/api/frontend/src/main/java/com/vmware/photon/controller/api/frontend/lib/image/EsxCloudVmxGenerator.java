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

package com.vmware.photon.controller.api.frontend.lib.image;

import com.vmware.photon.controller.api.frontend.exceptions.internal.InvalidOvfException;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.Device;
import com.vmware.photon.controller.api.frontend.lib.ova.ovf.OvfFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Esx Cloud configuration file.
 */
public class EsxCloudVmxGenerator {

  // Supported OVF extra configuration values.
  private static final Parameter[] extraConfigurations = new Parameter[]{
      new Parameter("vhv.enable"),
      new Parameter("bios.bootOrder"),
      new Parameter("monitor.suspend_on_triplefault"),
      new Parameter("vmx.allowNested"),
      new Parameter("bios.bootdeviceclasses")};

  // Serial port configurations values.
  private static final Parameter[] networkPortConfigMap = new Parameter[]{
      new Parameter("backing.direction", "network.endPoint"),
      new Parameter("yieldOnPoll", "yieldOnMsrRead")};

  // Ovf to vmx device name translation map.
  private static final Map<String, String> deviceOvfToVmx;

  static {
    deviceOvfToVmx = new HashMap<>();
    // SCSI.
    deviceOvfToVmx.put("lsilogic", "lsilogic");
    deviceOvfToVmx.put("buslogic", "buslogic");
    deviceOvfToVmx.put("lsilogicsas", "lsisas1068");
    deviceOvfToVmx.put("virtualscsi", "pvscsi");
    // Network.
    deviceOvfToVmx.put("vmxnet", "vmxnet");
    deviceOvfToVmx.put("vmxnet2", "vmxnet2");
    deviceOvfToVmx.put("vmxnet3", "vmxnet3");
    deviceOvfToVmx.put("pcnet32", "vlance");
    deviceOvfToVmx.put("e1000e", "e1000e");
    deviceOvfToVmx.put("e1000", "e1000");
  }

  private static final int MAX_NIC_DEVICES = 10;
  private final boolean[] nicDevicesUsage = new boolean[MAX_NIC_DEVICES];
  private static final int NIC_ADDRESS_OFFSET = 7;
  private final OvfFile ovfFile;
  private final EsxCloudVmx esxCloudVmx;

  /**
   * Constructor.
   *
   * @param ovfFile
   */
  public EsxCloudVmxGenerator(OvfFile ovfFile) {
    this.ovfFile = ovfFile;
    this.esxCloudVmx = new EsxCloudVmx();
  }

  /**
   * Build ECV from OVF.
   *
   * @param ovfFile
   * @return
   * @throws InvalidOvfException
   */
  public static EsxCloudVmx generate(OvfFile ovfFile) throws InvalidOvfException {
    EsxCloudVmxGenerator esxCloudVmxGenerator = new EsxCloudVmxGenerator(ovfFile);
    esxCloudVmxGenerator.addDeviceOptions();
    esxCloudVmxGenerator.addConfigOptions("", extraConfigurations, ovfFile.getExtraConfig());
    return esxCloudVmxGenerator.esxCloudVmx;
  }

  /**
   * Extract ECV parameters from the OVF device section.
   */
  private void addDeviceOptions() throws InvalidOvfException {
    for (Device device : ovfFile.getDevices()) {
      switch (device.getDeviceType()) {
        case SCSIController:
          esxCloudVmx.configuration.put(getVmwScsiControllerName(device) + ".virtualDev", getVirtualDeviceName(device));
          break;
        case EthernetAdapter:
          esxCloudVmx.configuration.put(getVmwNetworkAdapterName(device) + ".virtualDev", getVirtualDeviceName(device));
          break;
        case SerialPort:
          addSerialPortOptions(device);
          break;
      }
    }
  }

  /**
   * Extract ECV parameters defined in OVF.
   */
  private void addConfigOptions(String prefix, Parameter[] desiredConfigMap, Map<String, String> actualConfigMap) {
    for (Parameter param : desiredConfigMap) {
      if (actualConfigMap.containsKey(param.ovfName)) {
        // Add the configuration value from OVF.
        esxCloudVmx.configuration.put(prefix + param.vmxName, actualConfigMap.get(param.ovfName));
      }
    }
  }

  /**
   * Add serial port specific parameters.
   *
   * @param device
   */
  private void addSerialPortOptions(Device device) throws InvalidOvfException {
    String serialPortName = getVmwSerialPortName(device);

    // Retrieve serial port type specific parameters.
    if (SerialPortType.NETWORK.getOvfName().equals(device.getDeviceSubType())) {
      // Serial port represents a network endpoint, look for specific properties.
      esxCloudVmx.configuration.put(serialPortName + ".fileType", SerialPortType.NETWORK.getVmwName());
      addConfigOptions(serialPortName + ".", networkPortConfigMap, device.getConfig());
    } else {
      // Serial port type not understood, allow user to specify.
      esxCloudVmx.parameters.add(new EsxCloudVmx.Property(serialPortName + ".fileType"));
    }

    // serialx.fileName is not present in OVF. Allow end-user to specify it.
    esxCloudVmx.parameters.add(new EsxCloudVmx.Property(serialPortName + ".fileName"));
    // in proxy-based network serial port backing, serialx.vspc is set to the URI of the serial port concentrator.
    esxCloudVmx.parameters.add(new EsxCloudVmx.Property(serialPortName + ".vspc"));
  }

  private String getVirtualDeviceName(Device device) throws InvalidOvfException {
    String virtualDeviceName = deviceOvfToVmx.get(device.getDeviceSubType().toLowerCase());
    if (virtualDeviceName == null) {
      throw new InvalidOvfException(String.format("OVF: Unknown %s device: %s.", device.getDeviceType(),
          device.getDeviceSubType()));
    }
    return virtualDeviceName;
  }

  /**
   * Get SCSI name in VMware format.
   *
   * @return
   */
  public String getVmwScsiControllerName(Device device) {
    String deviceId = "0";
    try {
      // A controller device id is the Address.
      deviceId = device.getControllerUnitNumber();
    } catch (InvalidOvfException e) {
      // No device id found, assume there is only one scsi controller.
    }
    return String.format("scsi%s", deviceId);
  }

  /**
   * Get network adapter name in VMware format.
   *
   * @return
   */
  public String getVmwNetworkAdapterName(Device device) throws InvalidOvfException {
    int deviceId = 0;
    try {
      deviceId = Integer.parseInt(device.getOrdinalOnController());
    } catch (InvalidOvfException e) {
      // No address, assume only one device present.
    }
    // A network device id is the AddressOnParent offset by NIC_ADDRESS_OFFSET.
    if (deviceId > NIC_ADDRESS_OFFSET) {
      deviceId -= NIC_ADDRESS_OFFSET;
    } else {
      // Get first free NIC label.
      for (deviceId = 0; deviceId < MAX_NIC_DEVICES; deviceId++) {
        if (nicDevicesUsage[deviceId] == false) {
          break;
        }
      }
    }
    if (deviceId == MAX_NIC_DEVICES) {
      throw new InvalidOvfException("No NIC device ID found");
    }
    nicDevicesUsage[deviceId] = true;
    return String.format("ethernet%d", deviceId);
  }

  /**
   * Get serial port name in VMware format.
   *
   * @return
   */
  public String getVmwSerialPortName(Device device) {
    // Since there is only one SIO controller in a Virtual machine, and controller enumeration is zero-based,
    // this function will always return serial0.
    return "serial0";
  }

  /**
   * Enumeration of serial port types.
   */
  private static enum SerialPortType {
    NETWORK("vmware.serialport.uri", "network");

    private String ovfName;
    private String vmwName;

    private SerialPortType(String ovfName, String vmwName) {
      this.ovfName = ovfName;
      this.vmwName = vmwName;
    }

    public String getOvfName() {
      return ovfName;
    }

    public String getVmwName() {
      return vmwName;
    }
  }

  /**
   * Helper class for translating configuration parameters from OVF to ECV.
   */
  public static class Parameter {
    public String ovfName;
    public String vmxName;

    public Parameter(String name) {
      this.vmxName = name;
      this.ovfName = name;
    }

    public Parameter(String ovfName, String vmxName) {
      this.ovfName = ovfName;
      this.vmxName = vmxName;
    }
  }
}
