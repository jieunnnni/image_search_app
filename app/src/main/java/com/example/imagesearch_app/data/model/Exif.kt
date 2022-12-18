package com.example.imagesearch_app.data.model

import com.google.gson.annotations.SerializedName

data class Exif(
    @SerializedName("aperture")
    val aperture: String? = null,
    @SerializedName("exposure_time")
    val exposureTime: String? = null,
    @SerializedName("focal_length")
    val focalLength: String? = null,
    @SerializedName("iso")
    val iso: Int? = null,
    @SerializedName("make")
    val make: String? = null,
    @SerializedName("model")
    val model: String? = null
)