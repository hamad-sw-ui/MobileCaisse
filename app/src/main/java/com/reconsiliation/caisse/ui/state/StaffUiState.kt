package com.reconsiliation.caisse.ui.state

import com.reconsiliation.caisse.data.local.entity.StaffEntity

data class StaffUiState(
    val activeStaff: List<StaffEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
