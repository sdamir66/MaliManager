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

data class ToolsState(
    val prices: List<TgjuPrice> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val debugInfo: String = "",
    val lastUpdate: Long = 0L
)

object ToolsPoller {

    private const val TAG = "ToolsPoller"

    // scope مستقل از هر Composable — هیچ‌وقت کنسل نمی‌شه
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ToolsState())
    val state: StateFlow<ToolsState> = _state.asStateFlow()

    @Volatile
    private var isFetching = false

    /**
     * بارگذاری از cache — sync و فوری
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
     * fetch از شبکه — امن برای صدا زدن چندباره
     * اگه یه fetch در جریان باشه، درخواست جدید نادیده گرفته می‌شه
     */
    fun refresh(context: Context, force: Boolean = false) {
        if (isFetching && !force) {
            Log.d(TAG, "refresh ignored: already fetching")
            return
        }
        isFetching = true

        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            Log.d(TAG, "refresh START")

            try {
                val result = ToolsRepository.fetchPrices(
                    context,
                    PriceCatalog.ALL_ORDERED
                )
                result.fold(
                    onSuccess = { prices ->
                        Log.d(TAG, "refresh OK: ${prices.size}")
                        if (prices.isNotEmpty()) {
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
                                    error = if (it.prices.isEmpty()) "قیمتی دریافت نشد" else null
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
                isFetching = false
                Log.d(TAG, "refresh END")
            }
        }
    }

    /**
     * auto-save بعد از هر تغییر لیست — جدا از composition
     */
    fun persist(context: Context) {
        val prices = _state.value.prices
        if (prices.isNotEmpty()) {
            scope.launch {
                PriceCache.save(context, prices)
                Log.d(TAG, "persist: ${prices.size}")
            }
        }
    }
}
