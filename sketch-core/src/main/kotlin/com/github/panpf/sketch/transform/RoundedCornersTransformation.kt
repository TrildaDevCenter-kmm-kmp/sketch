/*
 * Copyright (C) 2022 panpf <panpfpanpf@outlook.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.panpf.sketch.transform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff.Mode.SRC_IN
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.Px
import androidx.annotation.WorkerThread
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.decode.internal.getOrCreate
import com.github.panpf.sketch.request.internal.RequestContext
import com.github.panpf.sketch.util.safeConfig

/**
 * A [Transformation] that crops the image to fit the target's dimensions and rounds the corners of
 * the image.
 *
 * Please use it with 'resizePrecision(Precision.EXACTLY)' because if the original image size and resize size are inconsistent,
 * the final fillet will be scaled when displayed, resulting in inconsistency between the size of the fillet and the expectation
 *
 * If you're using Jetpack Compose, use `Modifier.clip(RoundedCornerShape(radius))` instead of this
 * transformation as it's more efficient.
 *
 * @param radiusArray Array of 8 values, 4 pairs of [X,Y] radii. The corners are ordered top-left, top-right, bottom-right, bottom-left
 */
class RoundedCornersTransformation constructor(val radiusArray: FloatArray) : Transformation {

    /**
     * @param topLeft The radius for the top left corner.
     * @param topRight The radius for the top right corner.
     * @param bottomLeft The radius for the bottom left corner.
     * @param bottomRight The radius for the bottom right corner.
     */
    constructor(
        @Px topLeft: Float = 0f,
        @Px topRight: Float = 0f,
        @Px bottomLeft: Float = 0f,
        @Px bottomRight: Float = 0f
    ) : this(
        floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft,
        )
    )

    constructor(@Px allRadius: Float) : this(
        floatArrayOf(
            allRadius, allRadius,
            allRadius, allRadius,
            allRadius, allRadius,
            allRadius, allRadius
        )
    )

    init {
        require(radiusArray.size == 8) {
            "radiusArray size must be 8"
        }
        require(radiusArray.all { it >= 0f }) {
            "All radius must be >= 0"
        }
    }

    override val key: String =
        "RoundedCornersTransformation(${radiusArray.joinToString(separator = ",")})"

    @WorkerThread
    override suspend fun transform(
        sketch: Sketch,
        requestContext: RequestContext,
        input: Bitmap
    ): TransformResult {
        val config = input.safeConfig
        val bitmapPool = sketch.bitmapPool
        val newBitmap = bitmapPool.getOrCreate(
            width = input.width,
            height = input.height,
            config = config,
            disallowReuseBitmap = requestContext.request.disallowReuseBitmap,
            caller = "RoundedCornersTransformation"
        )
        val paint = Paint().apply {
            isAntiAlias = true
            color = -0x10000
        }
        val canvas = Canvas(newBitmap).apply {
            drawARGB(0, 0, 0, 0)
        }
        val path = Path().apply {
            val rect = RectF(0f, 0f, input.width.toFloat(), input.height.toFloat())
            addRoundRect(rect, radiusArray, Path.Direction.CW)
        }
        canvas.drawPath(path, paint)

        paint.xfermode = PorterDuffXfermode(SRC_IN)
        val rect = Rect(0, 0, input.width, input.height)
        canvas.drawBitmap(input, rect, rect, paint)
        return TransformResult(newBitmap, createRoundedCornersTransformed(radiusArray))
    }

    override fun toString(): String = key

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RoundedCornersTransformation
        if (!radiusArray.contentEquals(other.radiusArray)) return false
        return true
    }

    override fun hashCode(): Int {
        return radiusArray.contentHashCode()
    }
}

fun createRoundedCornersTransformed(radiusArray: FloatArray) =
    "RoundedCornersTransformed(${radiusArray.contentToString()})"

fun isRoundedCornersTransformed(transformed: String): Boolean =
    transformed.startsWith("RoundedCornersTransformed(")

fun List<String>.getRoundedCornersTransformed(): String? =
    find { isRoundedCornersTransformed(it) }