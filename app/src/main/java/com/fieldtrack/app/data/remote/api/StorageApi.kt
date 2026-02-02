package com.fieldtrack.app.data.remote.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface StorageApi {

    @Multipart
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadFile(
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Part file: MultipartBody.Part
    ): Response<Map<String, String>>
}
