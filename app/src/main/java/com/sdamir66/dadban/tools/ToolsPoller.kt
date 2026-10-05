package com.sdamir66.dadban.tools

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ToolsState(
    val prices: List<TgjuPrice> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val debugInfo: String = "",
    val lastUpdate: Long = 0L
)

object ToolsPoller {

    private const val TAG = "ToolsPoller"

    // ═══ TTL: اگه کمتر از این از آخرین fetch گذشته، refresh بدون force رو skip کن ═══
    private const val CACHE_TTL_MS = 60_000L  // ۱ دقیقه

    // scope مستقل از Composable — هیچ‌وقت کنسل نمی‌شه
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ToolsState())
    val state: StateFlow<ToolsState> = _state.asStateFlow()

    // ═══ Mutex به جای @Volatile Boolean — از race condition جلوگیری می‌کنه ═══
    private val fetchMutex = Mutex()

    /**
     * بارگذاری از cache — sync-مانند
     */
    fun loadFromCache(context: Context) {
        scope.launch {
            val cached = PriceCache.load(context)
            Log.d(TAG, "loadFromCache: ${cached.size}")
            if (cached.isNotEmpty() && _state.value.prices.isEmpty()) {
                _state.update {
                    it.copy(
                        prices = cached,
                        debugInfo = "از کش: ${cached.size} آیتم",
                        lastUpdate = PriceCache.getSavedAt(context)
                    )
                }
            }
        }
    }

    /**
     * fetch از شبکه — با Mutex قفل می‌شه تا race نباشه
     *
     * @param force اگه true، TTL رو نادیده بگیر (برای دکمه ↻)
     */
    fun refresh(context: Context, force: Boolean = false) {
        scope.launch {
            fetchMutex.withLock {
                // ═══ TTL check: فقط برای non-force ═══
                if (!force) {
                    val elapsed = System.currentTimeMillis() - _state.value.lastUpdate
                    if (elapsed < CACHE_TTL_MS && _state.value.prices.isNotEmpty()) {
                        Log.d(TAG, "refresh skip (TTL: ${elapsed / 1000}s)")
                        return@withLock
                    }
                }

                _state.update { it.copy(isLoading = true, error = null) }
                Log.d(TAG, "refresh START (force=$force)")

                try {
                    val result = ToolsRepository.fetchPrices(
                        context,
                        PriceCatalog.ALL_ORDERED
                    )

                    result.fold(
                        onSuccess = { prices ->
                            Log.d(TAG, "refresh OK: ${prices.size}")
                            if (prices.isNotEmpty()) {
                                // ═══ ذخیره به cache به صورت غیرمسدودکننده ═══
                                try {
                                    PriceCache.save(context, prices)
                                } catch (e: Exception) {
                                    Log.e(TAG, "cache save failed", e)
                                }

                                _state.update {
                                    it.copy(
                                        prices = prices,
                                        isLoading = false,
                                        error = null,
                                        debugInfo = "آخرین: ${prices.size} آیتم",
                                        lastUpdate = System.currentTimeMillis()
                                    )
                                }
                            } else {
                                _state.update {
                                    it.copy(
                                        isLoading = false,
                                        debugInfo = "API خالی",
                                        error = if (it.prices.isEmpty())
                                            "قیمتی دریافت نشد" else null
                                    )
                                }
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "refresh FAILED: ${e.message}")
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = e.message,
                                    debugInfo = "خطا در دریافت"
                                )
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "refresh EXCEPTION", e)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message,
                            debugInfo = "خطای غیرمنتظره"
                        )
                    }
                } finally {
                    Log.d(TAG, "refresh END")
                }
            }
        }
    }

    /**
     * ذخیره‌ی state فعلی — نیازی به fetch مجدد نیست
     */
    fun persist(context: Context) {
        val prices = _state.value.prices
        if (prices.isNotEmpty()) {
            scope.launch {
                try {
                    PriceCache.save(context, prices)
                    Log.d(TAG, "persist: ${prices.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "persist failed", e)
                }
            }
        }
    }
}
