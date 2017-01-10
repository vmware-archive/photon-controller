/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.common.xenon.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.fail;

import java.util.BitSet;
import java.util.Random;

/**
 * Tests for {@link com.vmware.photon.controller.common.xenon.serializer.BitSetSerializer}.
 */
public class BitSetSerializerTest {

  @Test(dataProvider = "bitSetData")
  public void testWrite(BitSet bitSet) {
    MockKryoOutput output = new MockKryoOutput();
    BitSetSerializer serializer = new BitSetSerializer();
    serializer.write(mock(Kryo.class), output, bitSet);

    verify(bitSet, output.len, output.values);
  }

  @DataProvider(name = "bitSetData")
  public Object[][] getBitSet() {
    Random rand = new Random();
    int[] bitLens = new int[] {
        0, // boundary
        1, // boundary
        5, // random odd number
        16, // random even number
        Long.SIZE - 1, // boundary
        Long.SIZE, // boundary
        Long.SIZE + 1, // boundary
        Long.SIZE + 5, // random odd number
        Long.SIZE + 16, // random even number
        Long.SIZE * 2 - 1, // boundary
        Long.SIZE * 2}; // boundary
    Object[][] results = new Object[bitLens.length][1];

    for (int i = 0; i < bitLens.length; ++i) {
      int bitLen = bitLens[i];
      BitSet bitSet = new BitSet(bitLen);

      for (int j = 0; j < bitLen; ++j) {
        bitSet.set(j, rand.nextBoolean());
      }

      results[i][0] = bitSet;
    }

    return results;
  }

  @Test(dataProvider = "longValuesData")
  public void testRead(int len, long[] values) {
    MockKryoInput input = new MockKryoInput(len, values);
    BitSetSerializer serializer = new BitSetSerializer();
    BitSet bitSet = serializer.read(mock(Kryo.class), input, BitSet.class);

    verify(bitSet, len, values);
  }

  @DataProvider(name = "longValuesData")
  public Object[][] getLongValuesData() {
    Random rand = new Random();
    int[] bitLens = new int[] {
        0, // boundary
        1, // boundary
        5, // random odd number
        16, // random even number
        Long.SIZE - 1, // boundary
        Long.SIZE, // boundary
        Long.SIZE + 1, // boundary
        Long.SIZE + 5, // random odd number
        Long.SIZE + 16, // random even number
        Long.SIZE * 2 - 1, // boundary
        Long.SIZE * 2}; // boundary
    Object[][] results = new Object[bitLens.length][2];

    for (int i = 0; i < bitLens.length; ++i) {
      int bitLen = bitLens[i];
      int wordNum = bitLen / Long.SIZE;
      if (bitLen % Long.SIZE > 0) {
        ++wordNum;
      }

      long[] words = new long[wordNum];

      for (int j = 0; j < wordNum; ++j) {
        words[j] = rand.nextLong();
      }

      results[i][0] = bitLen;
      results[i][1] = words;
    }

    return results;
  }

  /**
   * Mocking Kryo output class.
   */
  public static class MockKryoOutput extends Output {
    public int len;
    public long[] values;

    @Override
    public int writeInt(int value, boolean optimize) {
      this.len = value;
      return 0;
    }

    @Override
    public void writeLongs(long[] values) {
      this.values = values;
    }
  }

  /**
   * Mocking Kryo input class.
   */
  public static class MockKryoInput extends Input {
    public int len;
    public long[] values;

    public MockKryoInput(int len, long[] values) {
      this.len = len;
      this.values = values;
    }

    @Override
    public int readInt(boolean optimize) {
      return this.len;
    }

    @Override
    public long[] readLongs(int length) {
      return this.values;
    }
  }

  public static void verify(BitSet bitSet, int len, long[] words) {
    for (int i = 0; i < len; ++i) {
      int wordIndex = i / Long.SIZE;
      int bitInWord = i % Long.SIZE;

      if (bitSet.get(i) ^
          ((words[wordIndex] & (1L << bitInWord)) != 0L)) {
        String error = String.format("Bit %d is %b, but value is %x",
            i, bitSet.get(i), words[wordIndex]);
        fail(error);
      }
    }
  }
}
