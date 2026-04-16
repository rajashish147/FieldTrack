package com.fieldtrack.app.data.remote.api

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface StorageApi {

    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadFile(
        @Path("bucket") bucket: String,
        @Path("path", encoded = true) path: String,
        @Header("Content-Type") contentType: String,
        @Body file: RequestBody
    ): Response<Map<String, String>>
}
