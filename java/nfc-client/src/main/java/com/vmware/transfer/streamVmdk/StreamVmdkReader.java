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

package com.vmware.transfer.streamVmdk;

import com.vmware.transfer.nfc.SparseUtil;

import com.google.common.base.Charsets;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * StreamVmdkReader reads stream-optimized VMDKs. Used for converting disks
 * during upload to ESX via NFC.
 */
public class StreamVmdkReader {
  // TODO(jandersen): NIO
  private DataInputStream input;
  private long capacity;
  private long grainSize;
  private long currentLba;
  private String adapterType;
  private Map<String, String> ddb;

  public StreamVmdkReader(InputStream input) throws VmdkFormatException, IOException {
    this.input = new DataInputStream(input);
    adapterType = "buslogic";
    ddb = new LinkedHashMap<>(); // Preserve the order of the DDB entries
    currentLba = -1;
    initialize();
  }

  private void initialize() throws VmdkFormatException, IOException {
    byte[] headerBytes = new byte[512];
    readFully(headerBytes);
    ByteBuffer header = ByteBuffer.wrap(headerBytes);
    header.order(ByteOrder.LITTLE_ENDIAN);
    int magic = header.getInt();
    int version = header.getInt();
    int flags = header.getInt();
    capacity = header.getLong();
    grainSize = header.getLong();
    long descriptorOffset = header.getLong();
    long descriptorSizeLong = header.getLong();
    // Skip over the GT/GD-related entries. We'll rely on the embedded LBAs.
    header.getInt(); // numGTEsPerGT
    header.getLong(); // rgdOffset
    header.getLong(); // gdOffset
    long overhead = header.getLong();
    header.get(); // uncleanShutdown
    int newlineDetector = header.getInt();
    short compressAlgorithm = header.getShort();

    if (magic != SparseUtil.SPARSE_MAGICNUMBER) {
      throw new VmdkFormatException("Invalid sparse header (invalid magic)");
    }
    if (version < SparseUtil.SPARSE_VERSION_INCOMPAT_FLAGS) {
      throw new VmdkFormatException("Invalid sparse header (version too old)");
    }
    if ((flags & SparseUtil.SPARSEFLAG_VALID_NEWLINE_DETECTOR) != 0 &&
        newlineDetector != SparseUtil.SPARSE_NEWLINE_DETECTOR) {
      throw new VmdkFormatException("Invalid sparse header (newline corruption detected)");
    }
    if ((flags & SparseUtil.SPARSEFLAG_COMPRESSED) == 0 ||
        (flags & SparseUtil.SPARSEFLAG_EMBEDDED_LBA) == 0 ||
        compressAlgorithm != SparseUtil.DISKLIB_COMP_DEFLATE) {
      throw new VmdkFormatException("Invalid disk format (not stream-optimized)");
    }
    if (capacity > SparseUtil.SPARSE_MAX_EXTENTSIZE) {
      throw new VmdkFormatException("Invalid disk format (capacity too large)");
    }
    if (grainSize < SparseUtil.SPARSE_MIN_GRAINSIZE) {
      throw new VmdkFormatException("Invalid disk format (grain size too small)");
    }
    if (descriptorSizeLong > Integer.MAX_VALUE) {
      throw new VmdkFormatException("Invalid disk format (descriptor too large)");
    }
    int descriptorSize = (int) descriptorSizeLong;
    long overheadSectors = overhead - 1; // We've already read 1 sector (the header)
    if (descriptorSize > 0) {
      descriptorOffset -= 1;
      long toSkip = descriptorOffset * SparseUtil.DISKLIB_SECTOR_SIZE;
      while (toSkip > 0) {
        toSkip -= input.skip(toSkip);
      }
      overheadSectors -= descriptorOffset;

      byte[] descriptorBytes = new byte[descriptorSize * SparseUtil.DISKLIB_SECTOR_SIZE];
      readFully(descriptorBytes);
      overheadSectors -= descriptorSize;
      parseDescriptor(new String(descriptorBytes, Charsets.UTF_8));
    }
    long toSkip = overheadSectors * 512;
    while (toSkip > 0) {
      toSkip -= input.skip(toSkip);
    }
  }

  /**
   * Parse disk descriptor and extract disk DB.
   */
  private void parseDescriptor(String descriptor) throws VmdkFormatException {
    String[] lines = descriptor.split("\r\n");
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      // We're only interested in the DDB for now
      if (!trimmed.startsWith("ddb.")) {
        continue;
      }
      String[] keyValue = trimmed.substring(4).split("=");
      if (keyValue.length != 2) {
        throw new VmdkFormatException("Invalid ddb entry in descriptor");
      }
      if ("adaptertype".equalsIgnoreCase(keyValue[0])) {
        adapterType = keyValue[1];
      }
      ddb.put(keyValue[0], keyValue[1]);
    }
  }

  public long getCapacityInSectors() {
    return capacity;
  }

  public String getAdapterType() {
    return adapterType;
  }

  public Map<String, String> getDdb() {
    return ddb;
  }

  public int getGrainSize() {
    return (int) grainSize;
  }

  public int getNextGrain(byte[] grain) throws VmdkFormatException, IOException {
    byte[] headerBytes = new byte[12];
    readFully(headerBytes);
    ByteBuffer header = ByteBuffer.wrap(headerBytes);
    header.order(ByteOrder.LITTLE_ENDIAN);
    long sector = header.getLong();
    int cmpSize = header.getInt();

    // Sanity check
    if (cmpSize > grain.length * 3) {
      throw new VmdkFormatException("Disk format error: Invalid grain size");
    }

    while (cmpSize == 0) {
      // Metadata grain
      byte[] metadataBytes = new byte[512 - 12];
      readFully(metadataBytes);
      ByteBuffer metadata = ByteBuffer.wrap(metadataBytes);
      metadata.order(ByteOrder.LITTLE_ENDIAN);
      int type = metadata.getInt();
      long value = metadata.getLong();
      if (type == SparseUtil.GRAIN_MARKER_EOS) {
        input.close();
        return -1; // End of stream
      }
      if (type == SparseUtil.GRAIN_MARKER_PROGRESS) {
        // Update current LBA, and let the caller know something's happening.
        currentLba = value;
        return 0;
      }
      // Some other metadata grain (GT/GD). Skip to next grain and retry from there.
      long toSkip = sector * 512;
      while (toSkip > 0) {
        toSkip -= input.skip(toSkip);
      }
      readFully(headerBytes);
      header = ByteBuffer.wrap(headerBytes);
      header.order(ByteOrder.LITTLE_ENDIAN);
      sector = header.getLong();
      cmpSize = header.getInt();
    }
    assert cmpSize > 0;
    byte[] cmpBytes = new byte[cmpSize];
    readFully(cmpBytes);
    Inflater inf = new Inflater();
    inf.setInput(cmpBytes);
    int grainSize = -1;
    try {
      grainSize = inf.inflate(grain);
    } catch (DataFormatException e) {
      throw new VmdkFormatException("Zlib error: " + e.getMessage(), e);
    }
    inf.end();

    long totalGrainSize = 12 + cmpSize;
    long paddingSize = ((totalGrainSize + 512 - 1) / 512) * 512 - totalGrainSize;
    while (paddingSize > 0) {
      paddingSize -= input.skip(paddingSize);
    }
    currentLba = sector;
    return grainSize;
  }

  private void readFully(byte[] buffer) throws IOException, VmdkFormatException {
    try {
      input.readFully(buffer);
    } catch (EOFException e) {
      throw new VmdkFormatException("Unexpected end of file", e);
    }
  }

  public long getCurrentLba() {
    return currentLba;
  }

  public void close() throws IOException {
    input.close();
  }
}
