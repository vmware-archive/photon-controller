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

package com.vmware.transfer.nfc;

/**
 * SparseUtil.
 */
public class SparseUtil {
  // TODO(jandersen): Sector size may soon become variable.
  public static final int DISKLIB_SECTOR_SIZE = 512;
  public static final int DEFAULT_GRAIN_SIZE = 128;
  public static final int DEFAULT_GRAIN_SIZE_BYTES = DEFAULT_GRAIN_SIZE * DISKLIB_SECTOR_SIZE;
  public static final int DEFAULT_NUM_GTES_PER_GT = 512;
  public static final long DEFAULT_GT_COVERAGE = DEFAULT_NUM_GTES_PER_GT * DEFAULT_GRAIN_SIZE;

  // Constants lifted from diskLib
  public static final int SPARSE_MAGICNUMBER = 0x564d444b; // 'VMDK' in little-endian
  public static final int SPARSE_VERSION_INCOMPAT_FLAGS = 3;
  public static final int SPARSE_VERSION_CURRENT = SPARSE_VERSION_INCOMPAT_FLAGS;

  public static final int SPARSEFLAG_VALID_NEWLINE_DETECTOR = 1 << 0;
  public static final int SPARSEFLAG_COMPRESSED = 1 << 16;
  public static final int SPARSEFLAG_EMBEDDED_LBA = 1 << 17;
  public static final int SPARSEFLAG_DEFAULT_STREAM_FLAGS =
      SPARSEFLAG_VALID_NEWLINE_DETECTOR |
          SPARSEFLAG_COMPRESSED |
          SPARSEFLAG_EMBEDDED_LBA;

  public static final int SPARSE_NEWLINE_DETECTOR = 0x0a0d200a; // "\n \r\n" in little-endian
  public static final long SPARSE_GD_AT_END = -1L;

  public static final int SPARSE_HEADER_GD_OFFSET = 56;

  public static final int DISKLIB_COMP_DEFLATE = 1;

  public static final long SPARSE_MAX_EXTENTSIZE = (1L << 32) - 1;
  public static final long SPARSE_MIN_GRAINSIZE = 8;

  public static final int STREAMED_GRAIN_HEADER_SIZE = 12;

  public static final int GRAIN_MARKER_EOS = 0;
  public static final int GRAIN_MARKER_GRAIN_TABLE = 1;
  public static final int GRAIN_MARKER_GRAIN_DIRECTORY = 2;
  public static final int GRAIN_MARKER_FOOTER = 3;
  public static final int GRAIN_MARKER_PROGRESS = 4;

  public static long divideAndRoundUp(long x, long y) {
    return (x + y - 1) / y;
  }

  public static long align(long value, long alignment) {
    return divideAndRoundUp(value, alignment) * alignment;
  }

  public static long bytesToSectors(long bytes) {
    return divideAndRoundUp(bytes, DISKLIB_SECTOR_SIZE);
  }
}
