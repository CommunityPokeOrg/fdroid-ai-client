package org.communitypoke.fdroidai.data.fdroid

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Streaming
import retrofit2.http.Url

interface FdroidIndexService {

    /** Streams the repo index to disk — index-v2.json is tens of MB. */
    @Streaming
    @GET
    suspend fun downloadIndex(@Url url: String): Response<ResponseBody>

    /** Lightweight freshness probe; used to skip re-downloads when unchanged. */
    @HEAD
    suspend fun headIndex(@Url url: String): Response<Void>
}
