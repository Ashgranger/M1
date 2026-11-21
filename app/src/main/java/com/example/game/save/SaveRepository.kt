package com.example.game.save

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.zoneZeroDataStore by preferencesDataStore(name = "zone_zero_save_v1")

class SaveRepository(private val context: Context) {
    private val keyProfileJson = stringPreferencesKey("player_profile_json")

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(PlayerSaveData::class.java)

    val saveFlow: Flow<PlayerSaveData> = context.zoneZeroDataStore.data.map { prefs ->
        val raw = prefs[keyProfileJson]
        if (raw.isNullOrBlank()) {
            PlayerSaveData()
        } else {
            runCatching { adapter.fromJson(raw) ?: PlayerSaveData() }
                .getOrDefault(PlayerSaveData())
        }
    }

    suspend fun load(): PlayerSaveData = saveFlow.first()

    suspend fun save(data: PlayerSaveData) {
        val json = adapter.toJson(data)
        context.zoneZeroDataStore.edit { prefs ->
            prefs[keyProfileJson] = json
        }
    }

    suspend fun resetAll(): PlayerSaveData {
        val fresh = PlayerSaveData()
        save(fresh)
        return fresh
    }
}
