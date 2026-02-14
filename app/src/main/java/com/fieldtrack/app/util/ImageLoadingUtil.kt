package com.fieldtrack.app.util

import android.widget.ImageView
import coil.load
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.R

/**
 * Centralized image loading utility to eliminate duplication across adapters.
 * Handles authenticated Supabase storage URLs with consistent caching.
 */
object ImageLoadingUtil {

    /**
     * Load an authenticated image from Supabase storage.
     * 
     * @param imageView Target ImageView
     * @param storagePath Path in Supabase storage (e.g., "reports/image.jpg")
     * @param authToken Bearer token for authentication
     * @param placeholder Optional placeholder resource
     * @param errorDrawable Optional error drawable resource
     */
    fun loadAuthenticatedImage(
        imageView: ImageView,
        storagePath: String,
        authToken: String,
        placeholder: Int? = null,
        errorDrawable: Int? = null
    ) {
        val imageUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/authenticated/$storagePath"
        
        imageView.load(imageUrl) {
            crossfade(true)
            memoryCacheKey(storagePath)
            diskCacheKey(storagePath)
            addHeader("Authorization", "Bearer $authToken")
            placeholder?.let { placeholder(it) }
            errorDrawable?.let { error(it) }
        }
    }
}
