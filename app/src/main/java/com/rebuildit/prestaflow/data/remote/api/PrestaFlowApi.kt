package com.rebuildit.prestaflow.data.remote.api

import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.data.remote.dto.AuthResponseDto
import com.rebuildit.prestaflow.data.remote.dto.OrderListDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap

interface PrestaFlowApi {

    @POST("connector/login")
    suspend fun login(@Body request: AuthRequestDto): AuthResponseDto

    @GET("orders")
    suspend fun getOrders(@QueryMap filters: Map<String, @JvmSuppressWildcards String>): OrderListDto
}
