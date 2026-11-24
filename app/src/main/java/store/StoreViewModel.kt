package com.devdeepsy.nidusflora.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class StoreState {
    object Loading : StoreState()
    data class Success(
        val seasonalPicks: List<StoreProduct>,
        val categories: List<StoreCategory>
    ) : StoreState()
    data class Error(val message: String) : StoreState()
    data class Fallback(
        val seasonalPicks: List<StoreProduct>,
        val categories: List<StoreCategory>
    ) : StoreState()
}

class StoreViewModel : ViewModel() {

    private val _storeState = MutableStateFlow<StoreState>(StoreState.Loading)
    val storeState: StateFlow<StoreState> = _storeState

    init {
        loadStore()
    }

    fun loadStore() {
        viewModelScope.launch {
            _storeState.value = StoreState.Loading
            val result = StoreRepository.loadStoreData()
            _storeState.value = if (result.isFallback) {
                StoreState.Fallback(result.seasonalPicks, result.categories)
            } else {
                StoreState.Success(result.seasonalPicks, result.categories)
            }
        }
    }
}