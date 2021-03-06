/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fabric3.monitor.impl.writer;

import org.fabric3.monitor.spi.buffer.ResizableByteBuffer;

/**
 * Writes a long value in a character representation to a ByteBuffer without creating objects on the heap.
 */
public final class LongWriter {
    private static final byte[] LONG_MIN = "-9223372036854775808".getBytes();

    final static char[] DIGIT_TENS = {'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1', '1', '1', '1', '1', '1', '1', '2', '2', '2', '2',
                                      '2', '2', '2', '2', '2', '2', '3', '3', '3', '3', '3', '3', '3', '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4',
                                      '4', '4', '5', '5', '5', '5', '5', '5', '5', '5', '5', '5', '6', '6', '6', '6', '6', '6', '6', '6', '6', '6', '7', '7',
                                      '7', '7', '7', '7', '7', '7', '7', '7', '8', '8', '8', '8', '8', '8', '8', '8', '8', '8', '9', '9', '9', '9', '9', '9',
                                      '9', '9', '9', '9',};

    final static char[] DIGIT_ONES = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3',
                                      '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7',
                                      '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1',
                                      '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5',
                                      '6', '7', '8', '9',};

    /**
     * All possible chars for representing a number as a String
     */
    final static char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
                                  'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private LongWriter() {
    }

    public static int write(long value, ResizableByteBuffer buffer) {
        if (value == Long.MIN_VALUE) {
            buffer.put(LONG_MIN, 0, LONG_MIN.length);
            return LONG_MIN.length;
        } else if (value == 0L) {
            buffer.put((byte) '0');
            return 1;
        } else {
            return writeLongChars(value, buffer);
        }
    }

    private static int writeLongChars(long value, ResizableByteBuffer buffer) {
        int start = buffer.position();
        int size = (value < 0) ? stringSize(-value) + 1 : stringSize(value);
        int index = size + start;
        long q;
        int r;
        int charPos = index;
        char sign = 0;

        if (value < 0) {
            sign = '-';
            value = -value;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (value > Integer.MAX_VALUE) {
            q = value / 100;
            // really: r = i - (q * 100);
            r = (int) (value - ((q << 6) + (q << 5) + (q << 2)));
            value = q;
            buffer.put(--charPos, (byte) DIGIT_ONES[r]);
            buffer.put(--charPos, (byte) DIGIT_TENS[r]);
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int) value;
        while (i2 >= 65536) {
            q2 = i2 / 100;
            // really: r = i2 - (q * 100);
            r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
            i2 = q2;
            buffer.put(--charPos, (byte) DIGIT_ONES[r]);
            buffer.put(--charPos, (byte) DIGIT_TENS[r]);
        }

        // Use fast mode for smaller numbers
        // assert(i2 <= 65536, i2);
        for (; ; ) {
            q2 = (i2 * 52429) >>> (16 + 3);
            r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
            buffer.put(--charPos, (byte) DIGITS[r]);
            i2 = q2;
            if (i2 == 0) {
                break;
            }
        }
        if (sign != 0) {
            buffer.put(--charPos, (byte) sign);
        }
        buffer.position(index);
        return size;
    }

    private static int stringSize(long value) {
        long p = 10;
        for (int i = 1; i < 19; i++) {
            if (value < p) {
                return i;
            }
            p = 10 * p;
        }
        return 19;
    }

}
