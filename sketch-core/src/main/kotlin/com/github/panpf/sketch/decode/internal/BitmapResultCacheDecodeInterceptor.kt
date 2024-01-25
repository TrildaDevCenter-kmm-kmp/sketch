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
package com.github.panpf.sketch.decode.internal

import android.graphics.Bitmap.CompressFormat.PNG
import androidx.annotation.WorkerThread
import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.cache.DiskCache
import com.github.panpf.sketch.cache.isReadOrWrite
import com.github.panpf.sketch.datasource.DataFrom.RESULT_CACHE
import com.github.panpf.sketch.datasource.DiskCacheDataSource
import com.github.panpf.sketch.decode.BitmapDecodeException
import com.github.panpf.sketch.decode.BitmapDecodeInterceptor
import com.github.panpf.sketch.decode.BitmapDecodeResult
import com.github.panpf.sketch.decode.ImageInfo
import com.github.panpf.sketch.request.internal.RequestContext
import com.github.panpf.sketch.util.Size
import com.github.panpf.sketch.util.ifOrNull
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject

class BitmapResultCacheDecodeInterceptor : BitmapDecodeInterceptor {

    companion object {
        const val MODULE = "BitmapResultCacheDecodeInterceptor"
    }

    override val key: String? = null
    override val sortWeight: Int = 80

    @WorkerThread
    override suspend fun intercept(chain: BitmapDecodeInterceptor.Chain): Result<BitmapDecodeResult> {
        val sketch = chain.sketch
        val requestContext = chain.requestContext
        val resultCache = sketch.resultCache
        val resultCachePolicy = requestContext.request.resultCachePolicy

        return if (resultCachePolicy.isReadOrWrite) {
            resultCache.lockResultCache(requestContext) {
                ifOrNull(resultCachePolicy.readEnabled) {
                    readCache(sketch, requestContext)
                        ?.let { Result.success(it) }
                } ?: chain.proceed().apply {
                    val result = this.getOrNull()
                    if (result != null && resultCachePolicy.writeEnabled) {
                        writeCache(sketch, requestContext, result = result)
                    }
                }
            }
        } else {
            chain.proceed()
        }
    }

    private suspend fun <R> DiskCache.lockResultCache(
        requestContext: RequestContext,
        block: suspend () -> R
    ): R {
        val lock: Mutex = editLock(requestContext.resultCacheLockKey)
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    @WorkerThread
    private fun readCache(
        sketch: Sketch,
        requestContext: RequestContext,
    ): BitmapDecodeResult? {
        val resultCache = sketch.resultCache
        val bitmapDataDiskCacheSnapshot = resultCache[requestContext.resultCacheDataKey]
        val bitmapMetaDiskCacheSnapshot = resultCache[requestContext.resultCacheMetaKey]
        if (bitmapDataDiskCacheSnapshot == null || bitmapMetaDiskCacheSnapshot == null) {
            kotlin.runCatching { bitmapDataDiskCacheSnapshot?.remove() }
            kotlin.runCatching { bitmapMetaDiskCacheSnapshot?.remove() }
            return null
        }

        return try {
            val metaDataJSONObject = bitmapMetaDiskCacheSnapshot.newInputStream()
                .use { it.bufferedReader().readText() }
                .let { JSONObject(it) }
            val imageInfo = ImageInfo(
                width = metaDataJSONObject.getInt("width"),
                height = metaDataJSONObject.getInt("height"),
                mimeType = metaDataJSONObject.getString("mimeType"),
                exifOrientation = metaDataJSONObject.getInt("exifOrientation"),
            )
            val transformedList =
                metaDataJSONObject.optJSONArray("transformedList")?.let { jsonArray ->
                    (0 until jsonArray.length()).map { index ->
                        jsonArray[index].toString()
                    }
                }
            val extras = metaDataJSONObject.optJSONObject("extras")?.let {
                val extras = mutableMapOf<String, String>()
                it.keys().forEach { key ->
                    extras[key] = it.getString(key)
                }
                extras.toMap()
            }

            val dataSource = DiskCacheDataSource(
                sketch, requestContext.request, RESULT_CACHE, bitmapDataDiskCacheSnapshot
            )
            val cacheImageInfo = dataSource.readImageInfoWithBitmapFactory(true)
            val decodeOptions = requestContext.request
                // TODO Use imageInfo.mimeType here
                .newDecodeConfigByQualityParams(cacheImageInfo.mimeType)
                .toBitmapOptions()
            sketch.bitmapPool.setInBitmap(
                options = decodeOptions,
                imageSize = Size(cacheImageInfo.width, cacheImageInfo.height),
                imageMimeType = ImageFormat.PNG.mimeType,
                disallowReuseBitmap = requestContext.request.disallowReuseBitmap,
                caller = "BitmapResultCacheDecodeInterceptor:readCache"
            )
            sketch.logger.d(MODULE) {
                "read. inBitmap=${decodeOptions.inBitmap?.logString}. '${requestContext.key}'"
            }
            try {
                dataSource.decodeBitmap(decodeOptions)
            } catch (throwable: IllegalArgumentException) {
                val inBitmap = decodeOptions.inBitmap
                if (inBitmap != null && isInBitmapError(throwable)) {
                    val message = "Bitmap decode error. Because inBitmap. '${requestContext.key}'"
                    sketch.logger.e(MODULE, throwable, message)

                    sketch.bitmapPool.freeBitmap(
                        bitmap = inBitmap,
                        disallowReuseBitmap = requestContext.request.disallowReuseBitmap,
                        caller = "decode:error"
                    )
                    sketch.logger.d(MODULE) {
                        "read. freeBitmap. inBitmap error. bitmap=${decodeOptions.inBitmap?.logString}. '${requestContext.key}'"
                    }

                    decodeOptions.inBitmap = null
                    try {
                        dataSource.decodeBitmap(decodeOptions)
                    } catch (throwable2: Throwable) {
                        throw BitmapDecodeException("Bitmap decode error2: $throwable", throwable2)
                    }
                } else {
                    throw BitmapDecodeException("Bitmap decode error: $throwable", throwable)
                }
            }?.let { bitmap ->
                sketch.logger.d(MODULE) {
                    "read. successful. ${bitmap.logString}. ${imageInfo}. '${requestContext.key}'"
                }
                BitmapDecodeResult(
                    bitmap = bitmap,
                    imageInfo = imageInfo,
                    dataFrom = RESULT_CACHE,
                    transformedList = transformedList,
                    extras = extras
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmapDataDiskCacheSnapshot.remove()
            bitmapMetaDiskCacheSnapshot.remove()
            null
        }
    }

    @WorkerThread
    private fun writeCache(
        sketch: Sketch,
        requestContext: RequestContext,
        result: BitmapDecodeResult,
    ): Boolean {
        val transformedList = result.transformedList
        if (transformedList.isNullOrEmpty()) {
            return false
        }

        val resultCache = sketch.resultCache
        // TODO Use metadata
        val bitmapDataEditor = resultCache.edit(requestContext.resultCacheDataKey)
        val metaDataEditor = resultCache.edit(requestContext.resultCacheMetaKey)
        if (bitmapDataEditor == null || metaDataEditor == null) {
            kotlin.runCatching { bitmapDataEditor?.abort() }
            kotlin.runCatching { metaDataEditor?.abort() }
            return false
        }
        return try {
            bitmapDataEditor.newOutputStream().buffered().use {
                result.bitmap.compress(PNG, 100, it)
            }
            bitmapDataEditor.commit()

            metaDataEditor.newOutputStream().bufferedWriter().use { writer ->
                val metaJSONObject = JSONObject().apply {
                    put("width", result.imageInfo.width)
                    put("height", result.imageInfo.height)
                    put("mimeType", result.imageInfo.mimeType)
                    put("exifOrientation", result.imageInfo.exifOrientation)
                    put("transformedList", transformedList.let { list ->
                        JSONArray().apply {
                            list.forEach { transformed ->
                                put(transformed)
                            }
                        }
                    })
                    result.extras?.entries?.takeIf { it.isNotEmpty() }?.let { entries ->
                        put("extras", JSONObject().apply {
                            entries.forEach { entry ->
                                put(entry.key, entry.value)
                            }
                        })
                    }
                }
                writer.write(metaJSONObject.toString())
            }
            metaDataEditor.commit()
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            bitmapDataEditor.abort()
            metaDataEditor.abort()
            resultCache.remove(requestContext.resultCacheDataKey)
            resultCache.remove(requestContext.resultCacheMetaKey)
            false
        }
    }

    override fun toString(): String = "BitmapResultCacheDecodeInterceptor(sortWeight=$sortWeight)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

val RequestContext.resultCacheDataKey: String
    get() = "${cacheKey}_result_data"

val RequestContext.resultCacheMetaKey: String
    get() = "${cacheKey}_result_meta"

val RequestContext.resultCacheLockKey: String
    get() = "${cacheKey}_result"