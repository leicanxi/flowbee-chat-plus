package me.rerere.rikkahub.data.api

import me.rerere.rikkahub.data.flowbee.FlowBeeDefaults
import me.rerere.rikkahub.data.model.SponsorResponse
import me.rerere.rikkahub.utils.JsonInstant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

interface SponsorAPI {
    @GET("/api/sponsors")
    suspend fun getSponsors(): SponsorResponse

    companion object {
        fun create(httpClient: OkHttpClient): SponsorAPI {
            return Retrofit.Builder()
                .client(httpClient)
                .baseUrl(FlowBeeDefaults.SITE_URL)
                .addConverterFactory(JsonInstant.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SponsorAPI::class.java)
        }
    }
}
