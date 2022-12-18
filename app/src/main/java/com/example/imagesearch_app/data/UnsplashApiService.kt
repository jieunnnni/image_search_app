package com.example.imagesearch_app.data

import com.example.imagesearch_app.BuildConfig
import com.example.imagesearch_app.BuildConfig.UNSPLASH_ACCESS_KEY
import com.example.imagesearch_app.data.model.PhotoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface UnsplashApiService {

    @GET(
        "photos/random?" +
                "client_id=${BuildConfig.UNSPLASH_ACCESS_KEY}" +
                "&count=30"
    )

    suspend fun getRandomPhotos(
        @Query("query") query: String?
    ): Response<List<PhotoResponse>>
}
