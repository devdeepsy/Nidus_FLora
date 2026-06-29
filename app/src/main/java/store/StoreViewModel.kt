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
    // Fallback = loaded from hardcoded data, not Firestore
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
            try {
                val picks = StoreRepository.fetchSeasonalPicks()
                val categories = StoreRepository.fetchCategories()
                _storeState.value = StoreState.Success(picks, categories)
            } catch (e: Exception) {
                // Should not reach here since repo catches internally,
                // but just in case:
                _storeState.value = StoreState.Fallback(
                    StoreRepository.getSeasonalPicks(),
                    StoreRepository.getCategories()
                )
            }
        }
    }
}