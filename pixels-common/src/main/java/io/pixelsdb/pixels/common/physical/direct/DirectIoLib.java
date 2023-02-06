/*
 * Copyright 2023 PixelsDB.
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
package io.pixelsdb.pixels.common.physical.direct;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapping Linux I/O functions to native methods.
 * Partially referenced the implementation of Jaydio (https://github.com/smacke/jaydio),
 * which is implemented by Stephen Macke and licensed under Apache 2.0.
 * We replaced the complex buffer implementation with java direct byte buffer, thus we
 * can directly return the byte buffer to the calling problem with memory copying.
 * <p>
 * Created at: 02/02/2023
 * Author: hank
 */
public class DirectIoLib
{
    private static final Logger logger = LoggerFactory.getLogger(DirectIoLib.class);
    private static boolean compatible;
    /**
     * The soft block size for use with transfer multiples and memory alignment multiples
     */
    public static final int fsBlockSize;
    private static final long fsBlockNotMask;
    /**
     * Whether direct io (i.e., o_direct) is enabled.
     */
    public static final boolean directIoEnabled;
    private static int javaVersion = -1;

    private static Field jnaPointerPeer = null;
    private static Method directByteBufferAddress = null;
    private static Constructor<?> directByteBufferRConstructor = null;

    private static final int O_RDONLY = 00;
    private static final int O_WRONLY = 01;
    private static final int O_RDWR = 02;
    private static final int O_CREAT = 0100;
    private static final int O_TRUNC = 01000;
    private static final int O_DIRECT = 040000;
    private static final int O_SYNC = 04000000;

    static
    {
        fsBlockSize = Integer.parseInt(ConfigFactory.Instance().getProperty("localfs.block.size"));
        fsBlockNotMask = ~((long) fsBlockSize - 1);
        directIoEnabled = Boolean.parseBoolean(ConfigFactory.Instance().getProperty("localfs.enable.direct.io"));
        compatible = false;
        try
        {
            try
            {
                List<Integer> versionNumbers = new ArrayList<Integer>();
                for (String v : System.getProperty("java.version").split("\\.|-"))
                {
                    if (v.matches("\\d+"))
                    {
                        versionNumbers.add(Integer.parseInt(v));
                    }
                }
                if (versionNumbers.get(0) == 1)
                {
                    if (versionNumbers.get(1) >= 8)
                    {
                        javaVersion = versionNumbers.get(1);
                    }
                } else if (versionNumbers.get(0) > 8)
                {
                    javaVersion = versionNumbers.get(0);
                }
                if (javaVersion < 0)
                {
                    throw new Exception(String.format("Java version: %s is not supported", System.getProperty("java.version")));
                }

                if (javaVersion <= 11)
                {
                    // this is from sun.nio.ch.Util.initDBBRConstructor
                    Class<?> cl = Class.forName("java.nio.DirectByteBufferR");
                    directByteBufferRConstructor = cl.getDeclaredConstructor(
                            int.class, long.class, FileDescriptor.class, Runnable.class);
                }
                else
                {
                    /* the creator of DirectByteBufferR is changed after java 11 and is not compatible with java 8.
                     * Therefore, we use DirectByteBuffer to create direct read only buffer.
                     */
                    Class<?> cl = Class.forName("java.nio.DirectByteBuffer");
                    directByteBufferRConstructor = cl.getDeclaredConstructor(
                            long.class, int.class);
                }
                directByteBufferRConstructor.setAccessible(true);

                jnaPointerPeer = Class.forName("com.sun.jna.Pointer").getDeclaredField("peer");
                jnaPointerPeer.setAccessible(true);
                directByteBufferAddress = Class.forName("java.nio.DirectByteBuffer").getDeclaredMethod("address");
                directByteBufferAddress.setAccessible(true);
            } catch (Throwable e)
            {
                logger.error("failed to reflect fields and methods", e);
                throw new InternalError(e);
            }

            if (!Platform.isLinux())
            {
                logger.error("direct io is not supported on OS other than Linux");
            } else
            { // now check to see if we have O_DIRECT...

                final int linuxVersion = 0;
                final int majorRev = 1;
                final int minorRev = 2;

                List<Integer> versionNumbers = new ArrayList<Integer>();
                for (String v : System.getProperty("os.version").split("\\.|-"))
                {
                    if (v.matches("\\d+"))
                    {
                        versionNumbers.add(Integer.parseInt(v));
                    }
                }

                /* From "man 2 open":
                 *
                 * O_DIRECT  support was added under Linux in kernel version 2.4.10.
                 * Older Linux kernels simply ignore this flag.  Some file systems may not implement
                 * the flag and open() will fail with EINVAL if it is used.
                 */

                // test to see whether kernel version >= 2.4.10
                if (versionNumbers.get(linuxVersion) > 2)
                {
                    compatible = true;
                } else if (versionNumbers.get(linuxVersion) == 2)
                {
                    if (versionNumbers.get(majorRev) > 4)
                    {
                        compatible = true;
                    } else if (versionNumbers.get(majorRev) == 4 && versionNumbers.get(minorRev) >= 10)
                    {
                        compatible = true;
                    }
                }

                if (compatible)
                {
                    Native.register(Platform.C_LIBRARY_NAME); // register native methods
                } else
                {
                    logger.error(String.format("O_DIRECT not supported on Linux version: %d.%d.%d", linuxVersion, majorRev, minorRev));
                }
            }
        } catch (Throwable e)
        {
            logger.error("unable to register libc at class load time: " + e.getMessage(), e);
        }
    }

    private DirectIoLib() { }

    // -- native function hooks --
    public static native int close(int fd);

    private static native long pread(int fd, Pointer buf, long count, long offset);

    private static native int open(String pathname, int flags);

    /**
     * Given a pointer-to-pointer <tt>memptr</tt>, sets the dereferenced value to point to the start
     * of an allocated block of <tt>size</tt> bytes, where the starting address is a multiple of
     * <tt>alignment</tt>. It is guaranteed that the block may be freed by calling @{link {@link #free(Pointer)}
     * on the starting address. See "man 3 posix_memalign".
     *
     * @param memptr The pointer-to-pointer which will point to the address of the allocated aligned block
     *
     * @param alignment The alignment multiple of the starting address of the allocated block
     *
     * @param size The number of bytes to allocate
     *
     * @return 0 on success, one of the C error codes on failure.
     */
    private static native int posix_memalign(PointerByReference memptr, long alignment, long size);

    /**
     * @param ptr The pointer to the hunk of memory which needs freeing
     */
    public static native void free(Pointer ptr);

    private static native String strerror(int errnum);

    public static long getAddress(Pointer pointer) throws IllegalAccessException
    {
        return (Long) jnaPointerPeer.get(pointer);
    }

    public static long getAddress(ByteBuffer byteBuffer) throws InvocationTargetException, IllegalAccessException
    {
        if (byteBuffer.isDirect())
        {
            return (long) directByteBufferAddress.invoke(byteBuffer);
        }
        throw new IllegalAccessException("non direct byte buffer does not have absolute address");
    }

    /**
     * Wrap the absolute address as a read only byte buffer, without memory copy.
     * This is derived from sun.nio.ch.Util.newMappedByteBufferR.
     * @param size the capacity of the buffer.
     * @param address the absolute address the buffer starts from.
     * @return the wrapped buffer.
     */
    public static ByteBuffer wrapReadOnlyDirectByteBuffer(int size, long address)
    {
        ByteBuffer buffer;
        try
        {
            if (javaVersion <= 11)
            {
                buffer = (ByteBuffer) directByteBufferRConstructor.newInstance(
                        new Object[]{size, address, null, null});
            } else
            {
                buffer = ((ByteBuffer) directByteBufferRConstructor.newInstance(
                        new Object[]{address, size})).asReadOnlyBuffer();
            }
        } catch (InstantiationException |
                IllegalAccessException |
                InvocationTargetException e)
        {
            throw new InternalError(e);
        }
        return buffer;
    }

    /**
     * Allocate a byte buffer aligned to a multiple of block size.
     * @param size
     * @return
     */
    public static DirectBuffer allocateBuffer(int size) throws IllegalAccessException, InvocationTargetException
    {
        if (directIoEnabled)
        {
            PointerByReference pointerToPointer = new PointerByReference();
            // allocate one additional block for read alignment.
            int allocated = blockEnd(size) + fsBlockSize;
            posix_memalign(pointerToPointer, fsBlockSize, allocated);
            return new DirectBuffer(pointerToPointer.getValue(), size, allocated, true);
        }
        else
        {
            ByteBuffer buffer = ByteBuffer.allocateDirect(size);
            return new DirectBuffer(buffer, size, false);
        }
    }

    /**
     * This method is used to read files that are opened by {@link #open(String, boolean)}..
     * @param fd A file descriptor to pass to native pread
     * @param fileOffset The file offset at which to read
     * @param buffer he buffer into which to record the file read
     * @param length the number of bytes to read from the file
     * @return The number of bytes successfully read from the file
     * @throws IOException
     */
    public static int read(int fd, long fileOffset, DirectBuffer buffer, int length) throws IOException
    {
        if (directIoEnabled)
        {
            // the file will be read from blockStart(fileOffset), and the first fileDelta bytes should be ignored.
            long fileOffsetAligned = blockStart(fileOffset);
            long toRead = blockEnd(fileOffset + length) - blockStart(fileOffset);
            int read = (int) pread(fd, buffer.getPointer(), toRead, fileOffsetAligned);
            buffer.shift(((int) (fileOffset - fileOffsetAligned)));
            return read;
        }
        else
        {
            int read = (int) pread(fd, buffer.getPointer(), length, fileOffset);
            buffer.reset();
            return read;
        }
    }

    /**
     * Use the <tt>open</tt> Linux system call and pass in the <tt>O_DIRECT</tt> flag.
     * Currently, the only other flags passed in are <tt>O_RDONLY</tt> if <tt>readOnly</tt>
     * is <tt>true</tt>, and (if not) <tt>O_RDWR</tt> and <tt>O_CREAT</tt>.
     *
     * @param path The path to the file to open. If file does not exist, and we are opening
     *             with <tt>readOnly</tt>, this will throw an error. Otherwise, if it does
     *             not exist, but we have <tt>readOnly</tt> set to false, create the file.
     * @param readOnly Whether to pass in <tt>O_RDONLY</tt>
     * @return An integer file descriptor for the opened file
     * @throws IOException
     */
    public static int open(String path, boolean readOnly) throws IOException
    {
        int flags = directIoEnabled ? O_DIRECT : 0;
        if (readOnly)
        {
            flags |= O_RDONLY;
        } else
        {
            flags |= O_RDWR | O_CREAT;
        }
        try
        {
            int fd = open(path, flags);
            if (fd < 0)
            {
                throw new IOException("error opening " + path + ", got " + getLastError());
            }
            return fd;
        } catch (Throwable e)
        {
            throw new IOException("error opening " + path + ", got " + getLastError(), e);
        }
    }

    /**
     * Hooks into errno using Native.getLastError(), and parses it with native strerror function.
     *
     * @return An error message corresponding to the last <tt>errno</tt>
     */
    private static String getLastError()
    {
        return strerror(Native.getLastError());
    }


    // -- alignment logic utility methods

    /**
     * Given <tt>value</tt>, find the largest number less than or equal
     * to <tt>value</tt> which is a multiple of the fs block size.
     *
     * @param value
     * @return The largest number less than or equal to <tt>value</tt>
     * which is a multiple of the soft block size
     */
    private static long blockStart(long value)
    {
        return value & fsBlockNotMask;
    }

    /**
     * @see #blockStart(long)
     */
    private static int blockStart(int value)
    {
        return (int) (value & fsBlockNotMask);
    }

    /**
     * Given <tt>value</tt>, find the smallest number greater than or equal
     * to <tt>value</tt> which is a multiple of the fs block size.
     *
     * @param value
     * @return The smallest number greater than or equal to <tt>value</tt>
     * which is a multiple of the soft block size
     */
    private static long blockEnd(long value)
    {
        return (value + fsBlockSize - 1) & fsBlockNotMask;
    }

    /**
     * @see #blockEnd(long)
     */
    private static int blockEnd(int value)
    {
        return (int) ((value + fsBlockSize - 1) & fsBlockNotMask);
    }
}
