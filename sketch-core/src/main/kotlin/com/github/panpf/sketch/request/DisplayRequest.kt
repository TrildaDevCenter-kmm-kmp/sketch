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
package com.github.panpf.sketch.request

import android.content.Context
import android.graphics.Bitmap.Config
import android.graphics.ColorSpace
import android.graphics.drawable.Drawable
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.lifecycle.Lifecycle
import com.github.panpf.sketch.ComponentRegistry
import com.github.panpf.sketch.cache.CachePolicy
import com.github.panpf.sketch.decode.BitmapConfig
import com.github.panpf.sketch.http.HttpHeaders
import com.github.panpf.sketch.resize.Precision
import com.github.panpf.sketch.resize.PrecisionDecider
import com.github.panpf.sketch.resize.Scale
import com.github.panpf.sketch.resize.ScaleDecider
import com.github.panpf.sketch.resize.SizeResolver
import com.github.panpf.sketch.stateimage.ErrorStateImage
import com.github.panpf.sketch.stateimage.StateImage
import com.github.panpf.sketch.target.DisplayTarget
import com.github.panpf.sketch.target.ImageViewDisplayTarget
import com.github.panpf.sketch.target.Target
import com.github.panpf.sketch.transform.Transformation
import com.github.panpf.sketch.transition.Transition.Factory
import com.github.panpf.sketch.util.Size
import java.lang.ref.WeakReference

/**
 * Build and set the [DisplayRequest]
 */
fun DisplayRequest(
    context: Context,
    uriString: String?,
    configBlock: (DisplayRequest.Builder.() -> Unit)? = null
): DisplayRequest = DisplayRequest.Builder(context, uriString).apply {
    configBlock?.invoke(this)
}.build()

/**
 * Build and set the [DisplayRequest], target is an ImageView
 */
fun DisplayRequest(
    imageView: ImageView,
    uriString: String?,
    configBlock: (DisplayRequest.Builder.() -> Unit)? = null
): DisplayRequest = DisplayRequest.Builder(imageView.context, uriString).apply {
    target(imageView)
    configBlock?.invoke(this)
}.build()


/**
 * Display the image request, and finally get a Drawable.
 *
 * [Target] can only be [DisplayTarget], [ImageResult] can only be [DisplayResult]
 */
@Stable
interface DisplayRequest : ImageRequest {

    override fun newBuilder(
        configBlock: (ImageRequest.Builder.() -> Unit)?
    ): Builder = Builder(this).apply {
        configBlock?.invoke(this)
    }

    override fun newRequest(
        configBlock: (ImageRequest.Builder.() -> Unit)?
    ): ImageRequest = Builder(this).apply {
        configBlock?.invoke(this)
    }.build()

    /**
     * Create a new [DisplayRequest.Builder] based on the current [DisplayRequest].
     *
     * You can extend it with a trailing lambda function [configBlock]
     */
    fun newDisplayBuilder(
        configBlock: (Builder.() -> Unit)? = null
    ): Builder = Builder(this).apply {
        configBlock?.invoke(this)
    }

    /**
     * Create a new [DisplayRequest] based on the current [DisplayRequest].
     *
     * You can extend it with a trailing lambda function [configBlock]
     */
    fun newDisplayRequest(
        configBlock: (Builder.() -> Unit)? = null
    ): DisplayRequest = Builder(this).apply {
        configBlock?.invoke(this)
    }.build()

    class Builder : ImageRequest.Builder {

        constructor(context: Context, uriString: String?) : super(context, uriString)

        constructor(request: DisplayRequest) : super(request)

        /**
         * Set the [Listener]
         */
        fun listener(
            listener: Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error>?
        ): Builder = apply {
            @Suppress("UNCHECKED_CAST")
            super.listener(listener as Listener<ImageRequest, ImageResult.Success, ImageResult.Error>?)
        }

        /**
         * Convenience function to create and set the [Listener].
         */
        inline fun listener(
            crossinline onStart: (request: DisplayRequest) -> Unit = {},
            crossinline onCancel: (request: DisplayRequest) -> Unit = {},
            crossinline onError: (request: DisplayRequest, result: DisplayResult.Error) -> Unit = { _, _ -> },
            crossinline onSuccess: (request: DisplayRequest, result: DisplayResult.Success) -> Unit = { _, _ -> }
        ): Builder = listener(object :
            Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error> {
            override fun onStart(request: DisplayRequest) = onStart(request)
            override fun onCancel(request: DisplayRequest) = onCancel(request)
            override fun onError(
                request: DisplayRequest, result: DisplayResult.Error
            ) = onError(request, result)

            override fun onSuccess(
                request: DisplayRequest, result: DisplayResult.Success
            ) = onSuccess(request, result)
        })

        /**
         * Add the [Listener] to set
         */
        fun addListener(
            listener: Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error>
        ): Builder = apply {
            @Suppress("UNCHECKED_CAST")
            super.addListener(listener as Listener<ImageRequest, ImageResult.Success, ImageResult.Error>)
        }

        /**
         * Add the [Listener] to set
         */
        inline fun addListener(
            crossinline onStart: (request: DisplayRequest) -> Unit = {},
            crossinline onCancel: (request: DisplayRequest) -> Unit = {},
            crossinline onError: (request: DisplayRequest, result: DisplayResult.Error) -> Unit = { _, _ -> },
            crossinline onSuccess: (request: DisplayRequest, result: DisplayResult.Success) -> Unit = { _, _ -> }
        ): Builder = addListener(object :
            Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error> {
            override fun onStart(request: DisplayRequest) = onStart(request)
            override fun onCancel(request: DisplayRequest) = onCancel(request)
            override fun onError(
                request: DisplayRequest, result: DisplayResult.Error
            ) = onError(request, result)

            override fun onSuccess(
                request: DisplayRequest, result: DisplayResult.Success
            ) = onSuccess(request, result)
        })

        /**
         * Remove the [Listener] from set
         */
        fun removeListener(
            listener: Listener<DisplayRequest, DisplayResult.Success, DisplayResult.Error>
        ): Builder = apply {
            @Suppress("UNCHECKED_CAST")
            super.removeListener(listener as Listener<ImageRequest, ImageResult.Success, ImageResult.Error>)
        }

        /**
         * Set the [ProgressListener]
         */
        fun progressListener(
            progressListener: ProgressListener<DisplayRequest>?
        ): Builder = apply {
            @Suppress("UNCHECKED_CAST")
            super.progressListener(progressListener as ProgressListener<ImageRequest>?)
        }

        /**
         * Add the [ProgressListener] to set
         */
        fun addProgressListener(
            progressListener: ProgressListener<DisplayRequest>
        ): Builder = apply {
            @Suppress("UNCHECKED_CAST")
            super.addProgressListener(progressListener as ProgressListener<ImageRequest>)
        }

        /**
         * Remove the [ProgressListener] from set
         */
        fun removeProgressListener(
            progressListener: ProgressListener<DisplayRequest>
        ): Builder = apply {
            @Suppress("UNCHECKED_CAST")
            super.removeProgressListener(progressListener as ProgressListener<ImageRequest>)
        }

        /**
         * Set the [Target]. Can only be an implementation of [DisplayTarget]
         */
        fun target(target: DisplayTarget?): Builder = apply {
            super.target(target)
        }

        /**
         * Convenience function to create and set the [DisplayTarget].
         */
        inline fun target(
            crossinline onStart: (placeholder: Drawable?) -> Unit = {},
            crossinline onError: (error: Drawable?) -> Unit = {},
            crossinline onSuccess: (result: Drawable) -> Unit = {}
        ) = target(object : DisplayTarget {
            override val supportDisplayCount: Boolean = false
            override fun onStart(placeholder: Drawable?) = onStart(placeholder)
            override fun onError(error: Drawable?) = onError(error)
            override fun onSuccess(result: Drawable) = onSuccess(result)
        })

        /**
         * Set the target to the ImageView
         */
        fun target(imageView: ImageView): Builder = apply {
            super.target(ImageViewDisplayTarget(imageView))
        }

        override fun lifecycle(lifecycle: Lifecycle?): Builder = apply {
            super.lifecycle(lifecycle)
        }

        override fun lifecycle(lifecycleResolver: LifecycleResolver?): Builder = apply {
            super.lifecycle(lifecycleResolver)
        }

        override fun build(): DisplayRequest {
            return super.build() as DisplayRequest
        }


        override fun depth(depth: Depth?, depthFrom: String?): Builder = apply {
            super.depth(depth, depthFrom)
        }

        override fun parameters(parameters: Parameters?): Builder = apply {
            super.parameters(parameters)
        }

        override fun setParameter(key: String, value: Any?, cacheKey: String?): Builder = apply {
            super.setParameter(key, value, cacheKey)
        }

        override fun removeParameter(key: String): Builder = apply {
            super.removeParameter(key)
        }

        override fun httpHeaders(httpHeaders: HttpHeaders?): Builder = apply {
            super.httpHeaders(httpHeaders)
        }

        override fun addHttpHeader(name: String, value: String): Builder = apply {
            super.addHttpHeader(name, value)
        }

        override fun setHttpHeader(name: String, value: String): Builder = apply {
            super.setHttpHeader(name, value)
        }

        override fun removeHttpHeader(name: String): Builder = apply {
            super.removeHttpHeader(name)
        }

        override fun downloadCachePolicy(cachePolicy: CachePolicy?): Builder = apply {
            super.downloadCachePolicy(cachePolicy)
        }

        override fun bitmapConfig(bitmapConfig: BitmapConfig?): Builder = apply {
            super.bitmapConfig(bitmapConfig)
        }

        override fun bitmapConfig(bitmapConfig: Config): Builder = apply {
            super.bitmapConfig(bitmapConfig)
        }

        @RequiresApi(VERSION_CODES.O)
        override fun colorSpace(colorSpace: ColorSpace?): Builder = apply {
            if (VERSION.SDK_INT >= VERSION_CODES.O) {
                super.colorSpace(colorSpace)
            }
        }

        @Suppress("OverridingDeprecatedMember", "DeprecatedCallableAddReplaceWith")
        @Deprecated("From Android N (API 24), this is ignored.  The output will always be high quality.")
        override fun preferQualityOverSpeed(inPreferQualityOverSpeed: Boolean?): Builder = apply {
            @Suppress("DEPRECATION")
            super.preferQualityOverSpeed(inPreferQualityOverSpeed)
        }

        override fun resize(
            size: SizeResolver?,
            precision: PrecisionDecider?,
            scale: ScaleDecider?
        ): Builder = apply {
            super.resize(size, precision, scale)
        }

        override fun resize(
            size: Size,
            precision: Precision?,
            scale: Scale?
        ): Builder = apply {
            super.resize(size, precision, scale)
        }

        override fun resize(
            width: Int,
            height: Int,
            precision: Precision?,
            scale: Scale?
        ): Builder = apply {
            super.resize(width, height, precision, scale)
        }

        override fun resizeSize(sizeResolver: SizeResolver?): Builder = apply {
            super.resizeSize(sizeResolver)
        }

        override fun resizeSize(size: Size): Builder = apply {
            super.resizeSize(size)
        }

        override fun resizeSize(width: Int, height: Int): Builder = apply {
            super.resizeSize(width, height)
        }

        override fun resizePrecision(precisionDecider: PrecisionDecider?): Builder = apply {
            super.resizePrecision(precisionDecider)
        }

        override fun resizePrecision(precision: Precision): Builder = apply {
            super.resizePrecision(precision)
        }

        override fun resizeScale(scaleDecider: ScaleDecider?): Builder = apply {
            super.resizeScale(scaleDecider)
        }

        override fun resizeScale(scale: Scale): Builder = apply {
            super.resizeScale(scale)
        }

        override fun transformations(transformations: List<Transformation>?): Builder = apply {
            super.transformations(transformations)
        }

        override fun transformations(vararg transformations: Transformation): Builder = apply {
            super.transformations(*transformations)
        }

        override fun addTransformations(transformations: List<Transformation>): Builder = apply {
            super.addTransformations(transformations)
        }

        override fun addTransformations(vararg transformations: Transformation): Builder = apply {
            super.addTransformations(*transformations)
        }

        override fun removeTransformations(transformations: List<Transformation>): Builder = apply {
            super.removeTransformations(transformations)
        }

        override fun removeTransformations(vararg transformations: Transformation): Builder =
            apply {
                super.removeTransformations(*transformations)
            }

        override fun disallowReuseBitmap(disabled: Boolean?): Builder = apply {
            super.disallowReuseBitmap(disabled)
        }

        override fun ignoreExifOrientation(ignore: Boolean?): Builder = apply {
            super.ignoreExifOrientation(ignore)
        }

        override fun resultCachePolicy(cachePolicy: CachePolicy?): Builder = apply {
            super.resultCachePolicy(cachePolicy)
        }

        override fun placeholder(stateImage: StateImage?): Builder = apply {
            super.placeholder(stateImage)
        }

        override fun placeholder(drawable: Drawable): Builder = apply {
            super.placeholder(drawable)
        }

        override fun placeholder(drawableResId: Int): Builder = apply {
            super.placeholder(drawableResId)
        }

        override fun uriEmpty(stateImage: StateImage?): Builder = apply {
            super.uriEmpty(stateImage)
        }

        override fun uriEmpty(drawable: Drawable): Builder = apply {
            super.uriEmpty(drawable)
        }

        override fun uriEmpty(drawableResId: Int): Builder = apply {
            super.uriEmpty(drawableResId)
        }

        override fun error(
            defaultStateImage: StateImage?, configBlock: (ErrorStateImage.Builder.() -> Unit)?
        ): Builder = apply {
            super.error(defaultStateImage, configBlock)
        }

        override fun error(
            defaultDrawable: Drawable, configBlock: (ErrorStateImage.Builder.() -> Unit)?
        ): Builder = apply {
            super.error(defaultDrawable, configBlock)
        }

        override fun error(
            defaultDrawableResId: Int, configBlock: (ErrorStateImage.Builder.() -> Unit)?
        ): Builder = apply {
            super.error(defaultDrawableResId, configBlock)
        }

        override fun error(
            configBlock: (ErrorStateImage.Builder.() -> Unit)?
        ): Builder = apply {
            super.error(configBlock)
        }

        override fun transitionFactory(transitionFactory: Factory?): Builder = apply {
            super.transitionFactory(transitionFactory)
        }

        override fun crossfade(
            durationMillis: Int,
            fadeStart: Boolean,
            preferExactIntrinsicSize: Boolean,
            alwaysUse: Boolean,
        ): Builder = apply {
            super.crossfade(durationMillis, fadeStart, preferExactIntrinsicSize, alwaysUse)
        }

        override fun disallowAnimatedImage(disabled: Boolean?): Builder = apply {
            super.disallowAnimatedImage(disabled)
        }

        override fun resizeApplyToDrawable(resizeApplyToDrawable: Boolean?): Builder = apply {
            super.resizeApplyToDrawable(resizeApplyToDrawable)
        }

        override fun memoryCachePolicy(cachePolicy: CachePolicy?): Builder = apply {
            super.memoryCachePolicy(cachePolicy)
        }


        override fun merge(options: ImageOptions?): Builder = apply {
            super.merge(options)
        }

        override fun default(options: ImageOptions?): Builder = apply {
            super.default(options)
        }


        override fun components(components: ComponentRegistry?): Builder = apply {
            super.components(components)
        }

        override fun components(configBlock: ComponentRegistry.Builder.() -> Unit): Builder =
            apply {
                super.components(configBlock)
            }
    }

    data class DisplayRequestImpl internal constructor(
        override val context: Context,
        override val uriString: String,
        override val listener: Listener<ImageRequest, ImageResult.Success, ImageResult.Error>?,
        override val progressListener: ProgressListener<ImageRequest>?,
        override val target: Target?,
        override val lifecycleResolver: LifecycleResolver,
        override val definedOptions: ImageOptions,
        override val defaultOptions: ImageOptions?,
        override val depth: Depth,
        override val parameters: Parameters?,
        override val httpHeaders: HttpHeaders?,
        override val downloadCachePolicy: CachePolicy,
        override val bitmapConfig: BitmapConfig?,
        override val colorSpace: ColorSpace?,
        @Deprecated("From Android N (API 24), this is ignored. The output will always be high quality.")
        @Suppress("OverridingDeprecatedMember")
        override val preferQualityOverSpeed: Boolean,
        override val resizeSizeResolver: SizeResolver,
        override val resizePrecisionDecider: PrecisionDecider,
        override val resizeScaleDecider: ScaleDecider,
        override val transformations: List<Transformation>?,
        override val disallowReuseBitmap: Boolean,
        override val ignoreExifOrientation: Boolean,
        override val resultCachePolicy: CachePolicy,
        override val placeholder: StateImage?,
        override val uriEmpty: StateImage?,
        override val error: ErrorStateImage?,
        override val transitionFactory: Factory?,
        override val disallowAnimatedImage: Boolean,
        override val resizeApplyToDrawable: Boolean,
        override val memoryCachePolicy: CachePolicy,
        override val componentRegistry: ComponentRegistry?,
    ) : DisplayRequest
}