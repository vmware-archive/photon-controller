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
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.BitSet;

/**
 * This class implements a customized BitSet serializer for Kryo.
 */
public class BitSetSerializer extends Serializer<BitSet> {

  @Override
  public BitSet copy(final Kryo kryo, final BitSet original) {
    final BitSet result = new BitSet();
    final int length = original.length();
    for (int i = 0; i < length; i++) {
      result.set(i, original.get(i));
    }
    return result;
  }

  @Override
  public void write(final Kryo kryo, final Output output, final BitSet bitSet) {
    final int bitLen = bitSet.length();
    output.writeInt(bitLen, true);

    int wordNum = bitLen / Long.SIZE;
    if (bitLen % Long.SIZE > 0) {
      ++wordNum;
    }

    long[] words = new long[wordNum];
    for (int i = 0; i < bitLen; ++i) {
      if (bitSet.get(i)) {
        words[i / Long.SIZE] |= 1L << (i % Long.SIZE);
      }
    }

    output.writeLongs(words);
  }

  @Override
  public BitSet read(final Kryo kryo, final Input input, final Class<BitSet> bitSetClass) {
    final int bitLen = input.readInt(true);
    final BitSet bitSet = new BitSet(bitLen);

    int wordNum = bitLen / Long.SIZE;
    if (bitLen % Long.SIZE > 0) {
      ++wordNum;
    }

    long[] words = input.readLongs(wordNum);
    for (int i = 0; i < bitLen; ++i) {
      if ((words[i / Long.SIZE] & (1L << (i % Long.SIZE))) != 0L) {
        bitSet.set(i, true);
      } else {
        bitSet.set(i, false);
      }
    }

    return bitSet;
  }
}
