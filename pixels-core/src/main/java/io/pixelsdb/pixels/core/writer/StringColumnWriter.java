/*
 * Copyright 2017-2019 PixelsDB.
 *
 * This file is part of Pixels.
 *
 * Pixels is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Pixels is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero GNU General Public License for more details.
 *
 * You should have received a copy of the Affero GNU General Public
 * License along with Pixels.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package io.pixelsdb.pixels.core.writer;

import io.pixelsdb.pixels.common.utils.Constants;
import io.pixelsdb.pixels.core.PixelsProto;
import io.pixelsdb.pixels.core.TypeDescription;
import io.pixelsdb.pixels.core.encoding.Dictionary;
import io.pixelsdb.pixels.core.encoding.EncodingLevel;
import io.pixelsdb.pixels.core.encoding.HashTableDictionary;
import io.pixelsdb.pixels.core.encoding.RunLenIntEncoder;
import io.pixelsdb.pixels.core.utils.DynamicIntArray;
import io.pixelsdb.pixels.core.utils.EncodingUtils;
import io.pixelsdb.pixels.core.vector.BinaryColumnVector;
import io.pixelsdb.pixels.core.vector.ColumnVector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * String column writer.
 * <p>
 * The string column chunk consists of seven fields:
 * 1. pixels field (run length encoded pixels after dictionary encoding or un-encoded string values)
 * 2. lengths field (run length encoded raw string length)
 * 3. origins field (distinct string bytes array)
 * 4. starts field (starting offsets indicating starting points of each string in the origins field)
 * 5. orders field (dumped orders array mapping dictionary encoded value to final sorted order)
 * 6. lengths field offset (an integer value indicating offset of the lengths field in the chunk)
 * 7. origins field offset (an integer value indicating offset of the origins field in the chunk)
 * 8. starts field offset (an integer value indicating offset of the starts field in the chunk)
 * 9. orders field offset (an integer value indicating offset of the orders field in the chunk)
 * <p>
 * Pixels field is necessary in all cases.
 * Lengths field only exists when un-encoded.
 * Other fields only exist when dictionary encoding is enabled.
 *
 * @author guodong
 */
public class StringColumnWriter extends BaseColumnWriter
{
    /**
     * current vector holding encoded values of string
     */
    private final int[] curPixelVector;
    private final DynamicIntArray startsArray;  // lengths of each string when un-encoded
    private final Dictionary dictionary;
    private final EncodingUtils encodingUtils;
    private final boolean runlengthEncoding;
    private final boolean dictionaryEncoding;
    private int startOffset = 0; // the start offset for the current string when un-encoded

    public StringColumnWriter(TypeDescription type,  PixelsWriterOption writerOption)
    {
        super(type, writerOption);
        curPixelVector = new int[pixelStride];
        encodingUtils = new EncodingUtils();
        runlengthEncoding = encodingLevel.ge(EncodingLevel.EL2);
        if (runlengthEncoding)
        {
            encoder = new RunLenIntEncoder(false, true);
        }
        dictionaryEncoding = encodingLevel.ge(EncodingLevel.EL1);
        if (dictionaryEncoding)
        {
            dictionary = new HashTableDictionary(Constants.INIT_DICT_SIZE);
            startsArray = null;
        }
        else
        {
            dictionary = null;
            startsArray = new DynamicIntArray();
        }
    }

    @Override
    public int write(ColumnVector vector, int size) throws IOException
    {
        BinaryColumnVector columnVector = (BinaryColumnVector) vector;
        byte[][] values = columnVector.vector;
        int[] vLens = columnVector.lens;
        int[] vOffsets = columnVector.start;
        int curPartLength;
        int curPartOffset = 0;
        int nextPartLength = size;

        if (dictionaryEncoding)
        {
            while ((curPixelIsNullIndex + nextPartLength) >= pixelStride)
            {
                curPartLength = pixelStride - curPixelIsNullIndex;
                writeCurPartWithDict(columnVector, values, vLens, vOffsets, curPartLength, curPartOffset);
                newPixel();
                curPartOffset += curPartLength;
                nextPartLength = size - curPartOffset;
            }

            curPartLength = nextPartLength;
            writeCurPartWithDict(columnVector, values, vLens, vOffsets, curPartLength, curPartOffset);
        }
        else
        {
            // directly add to outputStream if not using dictionary encoding
            while ((curPixelIsNullIndex + nextPartLength) >= pixelStride)
            {
                curPartLength = pixelStride - curPixelIsNullIndex;
                writeCurPartWithoutDict(columnVector, values, vLens, vOffsets, curPartLength, curPartOffset);
                newPixel();
                curPartOffset += curPartLength;
                nextPartLength = size - curPartOffset;
            }

            curPartLength = nextPartLength;
            writeCurPartWithoutDict(columnVector, values, vLens, vOffsets, curPartLength, curPartOffset);
        }
        return outputStream.size();
    }

    private void writeCurPartWithoutDict(BinaryColumnVector columnVector, byte[][] values,
                                         int[] vLens, int[] vOffsets, int curPartLength, int curPartOffset)
    {
        for (int i = 0; i < curPartLength; i++)
        {
            curPixelEleIndex++;
            if (nullsPadding)
            {
                // add starts even if the current value is null, this is for random access
                startsArray.add(startOffset);
            }
            if (columnVector.isNull[curPartOffset + i])
            {
                hasNull = true;
                pixelStatRecorder.increment();
            }
            else
            {
                outputStream.write(values[curPartOffset + i], vOffsets[curPartOffset + i], vLens[curPartOffset + i]);
                startOffset += vLens[curPartOffset + i];
                pixelStatRecorder.updateString(values[curPartOffset + i], vOffsets[curPartOffset + i],
                        vLens[curPartOffset + i], 1);
            }
        }
        System.arraycopy(columnVector.isNull, curPartOffset, isNull, curPixelIsNullIndex, curPartLength);
        curPixelIsNullIndex += curPartLength;
    }

    private void writeCurPartWithDict(BinaryColumnVector columnVector, byte[][] values, int[] vLens, int[] vOffsets,
                                      int curPartLength, int curPartOffset)
    {
        for (int i = 0; i < curPartLength; i++)
        {
            curPixelEleIndex++;
            if (columnVector.isNull[i + curPartOffset])
            {
                hasNull = true;
                pixelStatRecorder.increment();
                if (nullsPadding)
                {
                    // padding 0 for nulls
                    curPixelVector[curPixelVectorIndex++] = 0;
                }
            }
            else
            {
                curPixelVector[curPixelVectorIndex++] = dictionary
                        .add(values[curPartOffset + i], vOffsets[curPartOffset + i], vLens[curPartOffset + i]);
                pixelStatRecorder.updateString(values[curPartOffset + i], vOffsets[curPartOffset + i],
                        vLens[curPartOffset + i], 1);
            }
        }
        System.arraycopy(columnVector.isNull, curPartOffset, isNull, curPixelIsNullIndex, curPartLength);
        curPixelIsNullIndex += curPartLength;
    }

    @Override
    public void newPixel() throws IOException
    {
        if (runlengthEncoding)
        {
            // for encoding level 2 or higher, cascade run length encode on dictionary encoding
            outputStream.write(encoder.encode(curPixelVector, 0, curPixelVectorIndex));
        }
        else if (dictionaryEncoding)
        {
            if (byteOrder.equals(ByteOrder.LITTLE_ENDIAN))
            {
                for (int i = 0; i < curPixelVectorIndex; ++i)
                {
                    encodingUtils.writeIntLE(outputStream, curPixelVector[i]);
                }
            }
            else
            {
                for (int i = 0; i < curPixelVectorIndex; ++i)
                {
                    encodingUtils.writeIntBE(outputStream, curPixelVector[i]);
                }
            }
        }
        // else write nothing to outputStream

        super.newPixel();
    }

    @Override
    public void flush() throws IOException
    {
        // flush out pixels field
        super.flush();
        // flush out other fields
        if (dictionaryEncoding)
        {
            flushDictionary();
        }
        else
        {
            flushStarts();
        }
    }

    @Override
    public PixelsProto.ColumnEncoding.Builder getColumnChunkEncoding()
    {
        if (dictionaryEncoding)
        {
            PixelsProto.ColumnEncoding.Builder builder =
                    PixelsProto.ColumnEncoding.newBuilder()
                    .setKind(PixelsProto.ColumnEncoding.Kind.DICTIONARY)
                    .setDictionarySize(dictionary.size());
            if (runlengthEncoding)
            {
                builder.setCascadeEncoding(PixelsProto.ColumnEncoding.newBuilder()
                        .setKind(PixelsProto.ColumnEncoding.Kind.RUNLENGTH));
            }
            return builder;
        }
        return PixelsProto.ColumnEncoding.newBuilder()
                .setKind(PixelsProto.ColumnEncoding.Kind.NONE);
    }

    @Override
    public void close() throws IOException
    {
        if (dictionaryEncoding)
        {
            dictionary.clear();
        }
        else
        {
            startsArray.clear();
        }
        if (runlengthEncoding)
        {
            encoder.close();
        }
        super.close();
    }

    private void flushStarts() throws IOException
    {
        int lensFieldOffset = outputStream.size();
        startsArray.add(startOffset); // add the last start offset
        if (byteOrder.equals(ByteOrder.LITTLE_ENDIAN))
        {
            for (int i = 0; i < startsArray.size(); i++)
            {
                encodingUtils.writeIntLE(outputStream, startsArray.get(i));
            }
        }
        else
        {
            for (int i = 0; i < startsArray.size(); i++)
            {
                encodingUtils.writeIntBE(outputStream, startsArray.get(i));
            }
        }
        startsArray.clear();

        ByteBuffer offsetBuf = ByteBuffer.allocate(Integer.BYTES);
        offsetBuf.order(byteOrder);
        offsetBuf.putInt(lensFieldOffset);
        outputStream.write(offsetBuf.array());
    }

    private void flushDictionary() throws IOException
    {
        int dictContentOffset;
        int dictStartsOffset;
        int size = dictionary.size();
        int[] starts = new int[size + 1];

        dictContentOffset = outputStream.size();

        // recursively visit the red black tree, and fill origins field, get starts array and orders array
        dictionary.visit(new Dictionary.Visitor()
        {
            private int initStart = 0;
            private int currentId = 0;

            @Override
            public void visit(Dictionary.VisitorContext context)
                    throws IOException
            {
                context.writeBytes(outputStream);
                starts[currentId++] = initStart;
                initStart += context.getLength();
            }
        });

        dictStartsOffset = outputStream.size();
        starts[size] = dictStartsOffset - dictContentOffset;

        // write out run length starts array
        if (runlengthEncoding)
        {
            outputStream.write(encoder.encode(starts));
        }
        else
        {
            if (byteOrder.equals(ByteOrder.LITTLE_ENDIAN))
            {
                for (int start : starts)
                {
                    encodingUtils.writeIntLE(outputStream, start);
                }
            }
            else
            {
                for (int start : starts)
                {
                    encodingUtils.writeIntBE(outputStream, start);
                }
            }
        }

        /*
         * Issue #498:
         * We no longer write the orders array (encoded-id to key-index mapping) to files.
         * Encoded id is exactly the index of the key in the dictionary.
         */

        ByteBuffer offsetsBuf = ByteBuffer.allocate(2 * Integer.BYTES);
        offsetsBuf.order(byteOrder);
        offsetsBuf.putInt(dictContentOffset);
        offsetsBuf.putInt(dictStartsOffset);
        outputStream.write(offsetsBuf.array());
    }

    @Override
    public boolean decideNullsPadding(PixelsWriterOption writerOption)
    {
        if (writerOption.getEncodingLevel().ge(EncodingLevel.EL2))
        {
            return false;
        }
        return writerOption.isNullsPadding();
    }
}
