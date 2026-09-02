package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.entity.CategoryEntity
import com.reconsiliation.caisse.data.repository.MainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: MainRepository
) : ViewModel() {

    fun getCategories(type: String): Flow<List<CategoryEntity>> = repository.getCategories(type)

    fun addCategory(name: String, type: String) = viewModelScope.launch {
        repository.addCategory(CategoryEntity(name = name, type = type))
    }

    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch {
        repository.deleteCategory(category)
    }
}
