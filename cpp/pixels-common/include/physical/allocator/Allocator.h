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

/*
 * @author liyu
 * @create 2023-05-21
 */
#ifndef DUCKDB_ALLOCATOR_H
#define DUCKDB_ALLOCATOR_H

#include <iostream>
#include <memory>
#include <physical/natives/ByteBuffer.h>

class Allocator
{
public:
    virtual void reset() = 0;

    virtual std::shared_ptr <ByteBuffer> allocate(int size) = 0;
};
#endif // DUCKDB_ALLOCATOR_H
