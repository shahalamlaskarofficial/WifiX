package com.wifix.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ispDataStore by preferencesDataStore(name = "wifix_isp_plan")

data class IspPlanConfig(
    val ispName: String = "My ISP",
    val subscribedDownloadMbps: Double = 30.0,
    val subscribedUploadMbps: Double = 30.0
)

class IspPlanStore(private val context: Context) {

    private val keyIspName = stringPreferencesKey("isp_name")
    private val keySubDown = doublePreferencesKey("sub_download_mbps")
    private val keySubUp = doublePreferencesKey("sub_upload_mbps")

    val planFlow: Flow<IspPlanConfig> = context.ispDataStore.data.map { prefs ->
        IspPlanConfig(
            ispName = prefs[keyIspName] ?: "My ISP",
            subscribedDownloadMbps = prefs[keySubDown] ?: 30.0,
            subscribedUploadMbps = prefs[keySubUp] ?: 30.0
        )
    }

    suspend fun savePlan(config: IspPlanConfig) {
        context.ispDataStore.edit { prefs ->
            prefs[keyIspName] = config.ispName.trim().ifEmpty { "My ISP" }
            prefs[keySubDown] = config.subscribedDownloadMbps.coerceAtLeast(1.0)
            prefs[keySubUp] = config.subscribedUploadMbps.coerceAtLeast(1.0)
        }
    }
}
