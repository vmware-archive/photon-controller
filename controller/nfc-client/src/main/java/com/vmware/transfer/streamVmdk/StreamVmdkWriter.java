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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

/**
 * StreamVmdkWriter writes stream-optimized VMDKs.
 */
public class StreamVmdkWriter {
  private static final Logger logger = LoggerFactory.getLogger(StreamVmdkWriter.class);

  private final DataOutputStream output;
  private long capacitySectors;
  private long currentOffset;
  private int gdSize;
  private long gdSizeSectors;
  private long numGDEntries;
  private int gtSize;
  private long gtSizeSectors;
  private long nextSector;
  private int currentGDIndex;
  private boolean unflushedDataGrains;
  private long lastWriteTime;
  private long lastWrittenSector;
  private ByteBuffer sparseHeader;
  private ByteBuffer currentGrainTable;
  private ByteBuffer grainDirectory;
  private ByteBuffer grainHeader;

  public StreamVmdkWriter(final OutputStream output, long capacitySectors) {
    this.output = new DataOutputStream(output);
    this.capacitySectors = capacitySectors;
    currentOffset = 0;
    nextSector = 0;
    sparseHeader = ByteBuffer.allocate(SparseUtil.DISKLIB_SECTOR_SIZE);
    sparseHeader.order(ByteOrder.LITTLE_ENDIAN);
    numGDEntries = SparseUtil.divideAndRoundUp(capacitySectors, SparseUtil.DEFAULT_GT_COVERAGE);
    gdSize = (int) numGDEntries * 4;
    gdSizeSectors = SparseUtil.bytesToSectors(gdSize);
    grainDirectory = ByteBuffer.allocate(gdSize);
    grainDirectory.order(ByteOrder.LITTLE_ENDIAN);
    gtSize = SparseUtil.DEFAULT_NUM_GTES_PER_GT * 4;
    gtSizeSectors = SparseUtil.bytesToSectors(gtSize);
    grainHeader = ByteBuffer.allocate(SparseUtil.DISKLIB_SECTOR_SIZE);
    grainHeader.order(ByteOrder.LITTLE_ENDIAN);
    unflushedDataGrains = false;
    updateLastWriteTime(0);
  }

  public void writeHeader(int cid, Map<String, String> ddb) throws IOException {
    ByteArrayOutputStream descriptor = generateDescriptor(cid, ddb);
    int descriptorSize = descriptor.size();
    writeSparseHeader(descriptorSize);
    descriptor.writeTo(output);
    currentOffset += descriptorSize;
    padTo(SparseUtil.DEFAULT_GRAIN_SIZE_BYTES);
  }

  /**
   * Write data grain to output stream.
   *
   * @param sector LBA of the grain
   * @param grain  Grain data
   * @return Size of stream written so far
   * @throws IOException
   */
  public long writeDataGrain(long sector, byte[] grain) throws IOException {
    assert grain.length == SparseUtil.DEFAULT_GRAIN_SIZE_BYTES;
    assert sector % SparseUtil.DEFAULT_GRAIN_SIZE == 0;
    assert sector >= nextSector;

    addToGrainTable(sector);

        /*
         * Maximum .1% + 12 bytes expansion according to zlib manual. It seems
         * silly to do expensive multiplication and division for this, so we
         * approximate it conservatively as 2**-9 == .195% + 13.
         */
    int maxSize = grain.length + (grain.length >> 9) + 13;
    byte[] compressed = new byte[maxSize];

    Deflater deflater = new Deflater();
    deflater.setInput(grain);
    deflater.finish();
    int cmpSize = deflater.deflate(compressed, 0, maxSize, Deflater.SYNC_FLUSH);
    grainHeader.clear();
    grainHeader.putLong(sector);
    grainHeader.putInt(cmpSize);
    write(grainHeader.array(), 0, SparseUtil.STREAMED_GRAIN_HEADER_SIZE);
    write(compressed, 0, cmpSize);
    padTo(SparseUtil.DISKLIB_SECTOR_SIZE);
    nextSector += SparseUtil.DEFAULT_GRAIN_SIZE;

    updateLastWriteTime(sector);
    unflushedDataGrains = true;
    return currentOffset;
  }

  /**
   * Write progress grain to output stream.
   *
   * @param sector LBA of the grain
   * @return Size of stream written so far
   * @throws IOException
   */
  public long writeProgressGrain(long sector) throws IOException {
    assert sector % SparseUtil.DEFAULT_GRAIN_SIZE == 0;
    assert sector >= nextSector;

    if (unflushedDataGrains) {
            /*
             * First empty grain after a series of data grains. Flush the
             * stream to make sure the data grains are sent. Bug #787853.
             */
      output.flush();
      unflushedDataGrains = false;
    }
        /*
         * Ignore empty grains. If the client has requested progress updates,
         * and too much time has passed since we last sent anything, send a
         * progress MD grain.
         */
    writeProgressGrainIfNeeded(sector);
    return currentOffset;
  }

  public void writeTrailer() throws IOException {
    // write remaining grain table, if any
    if (currentGrainTable != null) {
      flushCurrentGrainTable();
    }

    // write grain directory
    writeMarker(SparseUtil.GRAIN_MARKER_GRAIN_DIRECTORY, gdSizeSectors);
    long gdOffset = currentOffsetSector();
    write(grainDirectory.array(), 0, grainDirectory.capacity());
    padTo(SparseUtil.DISKLIB_SECTOR_SIZE);

    // write footer (with correct GD offset)
    writeMarker(SparseUtil.GRAIN_MARKER_FOOTER, 1);
    sparseHeader.putLong(SparseUtil.SPARSE_HEADER_GD_OFFSET, gdOffset);
    write(sparseHeader.array(), 0, sparseHeader.capacity());

    // write end-of-stream marker
    writeMarker(SparseUtil.GRAIN_MARKER_EOS, 0);
  }

  public void close() throws IOException {
    output.close();
  }

  private void write(byte[] data) throws IOException {
    write(data, 0, data.length);
  }

  private void write(byte[] data, int offset, int length) throws IOException {
    output.write(data, offset, length);
    currentOffset += length;
  }

  private void padTo(int alignment) throws IOException {
    long desiredSize = SparseUtil.align(currentOffset, alignment);
    if (desiredSize > currentOffset) {
      write(new byte[(int) (desiredSize - currentOffset)]);
    }
    assert currentOffset % alignment == 0;
  }

  private void writeSparseHeader(int descriptorSize) throws IOException {
    sparseHeader.clear();
    sparseHeader.putInt(SparseUtil.SPARSE_MAGICNUMBER); // 0
    sparseHeader.putInt(SparseUtil.SPARSE_VERSION_CURRENT); // 4
    sparseHeader.putInt(SparseUtil.SPARSEFLAG_DEFAULT_STREAM_FLAGS); // 8
    sparseHeader.putLong(capacitySectors); // 12
    sparseHeader.putLong(SparseUtil.DEFAULT_GRAIN_SIZE); // 20
    sparseHeader.putLong(1); // 28 = descriptor offset
    long descriptorSectors = SparseUtil.bytesToSectors(descriptorSize);
    sparseHeader.putLong(descriptorSectors); // 36
    sparseHeader.putInt(512); // 44 = numGTEsPerGT
    sparseHeader.putLong(0); // 48 = rgdOffset - we don't have a redundant grain table
    assert sparseHeader.position() == SparseUtil.SPARSE_HEADER_GD_OFFSET;
    sparseHeader.putLong(SparseUtil.SPARSE_GD_AT_END); // 56 = gdOffset
    long overhead = SparseUtil.align(1 + descriptorSectors, SparseUtil.DEFAULT_GRAIN_SIZE);
    sparseHeader.putLong(overhead); // 64 = overhead
    sparseHeader.put((byte) 0); // unclean shutdown
    sparseHeader.putInt(SparseUtil.SPARSE_NEWLINE_DETECTOR);
    sparseHeader.putShort((short) SparseUtil.DISKLIB_COMP_DEFLATE);
    write(sparseHeader.array(), 0, sparseHeader.capacity());
    assert currentOffset == SparseUtil.DISKLIB_SECTOR_SIZE;
  }

  private ByteArrayOutputStream generateDescriptor(int cid, Map<String, String> ddb) throws IOException {
    ByteArrayOutputStream descBytes = new ByteArrayOutputStream();
    PrintStream desc = new PrintStream(descBytes, false, Charsets.UTF_8.name());
    desc.println("# Disk DescriptorFile");
    desc.println("version=1");
    desc.format("CID=%08x\n", cid);
    desc.println("parentCID=ffffffff");
    desc.println("createType=\"streamOptimized\"");
    desc.println();
    desc.println("# Extent description");
    desc.format("RDONLY %d SPARSE \"generated-stream.vmdk\"\n", capacitySectors);
    desc.println();
    desc.println("#DDB");
    for (Map.Entry<String, String> entry : ddb.entrySet()) {
      desc.format("%s = \"%s\"\n", entry.getKey(), entry.getValue());
    }
    desc.flush();
    return descBytes;
  }

  private int currentOffsetSector() {
    assert currentOffset % SparseUtil.DISKLIB_SECTOR_SIZE == 0;
    return (int) (currentOffset / SparseUtil.DISKLIB_SECTOR_SIZE);
  }

  private void addToGrainTable(long sector) throws IOException {
    long grainNo = sector / SparseUtil.DEFAULT_GRAIN_SIZE;
    int gdIndex = (int) (grainNo / SparseUtil.DEFAULT_NUM_GTES_PER_GT) << 2;
    int gtIndex = (int) (grainNo % SparseUtil.DEFAULT_NUM_GTES_PER_GT) << 2;
    if (gdIndex != currentGDIndex && currentGrainTable != null) {
      flushCurrentGrainTable(); // sets currentGrainTable = null
    }
    if (currentGrainTable == null) {
      currentGrainTable = ByteBuffer.allocate(gtSize);
      currentGrainTable.order(ByteOrder.LITTLE_ENDIAN);
      currentGDIndex = gdIndex;
    }
    assert currentGrainTable.getInt(gtIndex) == 0;
    currentGrainTable.putInt(gtIndex, currentOffsetSector());
  }

  private void flushCurrentGrainTable() throws IOException {
    assert grainDirectory.getInt(currentGDIndex) == 0;
    writeMarker(SparseUtil.GRAIN_MARKER_GRAIN_TABLE, gtSizeSectors);
    grainDirectory.putInt(currentGDIndex, currentOffsetSector());
    write(currentGrainTable.array(), 0, currentGrainTable.capacity());
    padTo(SparseUtil.DISKLIB_SECTOR_SIZE);
    currentGrainTable = null;
  }

  private void writeMarker(int marker, long size) throws IOException {
    grainHeader.clear();
    grainHeader.putLong(size); // size of metadata following marker
    grainHeader.putInt(0); // == marker
    grainHeader.putInt(marker); // marker type
    write(grainHeader.array(), 0, grainHeader.capacity());
  }

  private void updateLastWriteTime(long sector) {
    lastWrittenSector = sector;
    lastWriteTime = System.nanoTime();
  }

  private void writeProgressGrainIfNeeded(long sector) throws IOException {
    long secondsElapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastWriteTime);

    // If less than 10 seconds have elapsed since last write, then don't send.
    if (secondsElapsed < 10) {
      return;
    }

        /*
         * If less than 1% of progress has been made, and less than 45 seconds
         * has elapsed since last write, don't send.
         */
    long progressMade = (sector - lastWrittenSector) * 100 / capacitySectors;
    if ((progressMade < 1) && (secondsElapsed < 45)) {
      return;
    }

    logger.trace("writing progress grain for sector {}, progressMade={}, secondsElapsed={}",
        sector, progressMade, secondsElapsed);

    // Send progress grain.
    grainHeader.clear();
    grainHeader.putLong(0); // size of metadata (in sectors)
    grainHeader.putInt(0); // metadata marker
    grainHeader.putInt(SparseUtil.GRAIN_MARKER_PROGRESS); // type of metadata
    grainHeader.putLong(sector); // metadata contents

    // Write 4 duplicate progress grain to workaround bug #1239452, due to that apache
    // ChunkedOutputStream buffer size is fixed as 2048 and progress grain size is 512.
    // Until buffer size configurable ChunkedOutputStream is implemented (bug#1248216)
    for (int i = 0; i < 4; i++) {
      write(grainHeader.array(), 0, grainHeader.capacity());
    }
    output.flush(); // make sure the progress grain reaches the client

    updateLastWriteTime(sector);
  }
}
