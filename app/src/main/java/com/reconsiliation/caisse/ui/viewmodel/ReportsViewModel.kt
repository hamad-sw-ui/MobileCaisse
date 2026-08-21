package com.reconsiliation.caisse.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.local.dao.CategoryReport
import com.reconsiliation.caisse.data.local.dao.DailySales
import com.reconsiliation.caisse.data.local.dao.VenteWithItems
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.platform.ReportManager
import com.reconsiliation.caisse.platform.ShareManager
import com.reconsiliation.caisse.ui.state.ReportsUiState
import com.reconsiliation.caisse.utils.FormatUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val mainRepository: MainRepository,
    private val reportManager: ReportManager,
    private val shareManager: ShareManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportsUiState>(ReportsUiState.Idle)
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _currentRange = MutableStateFlow("MONTH")
    
    private val rangeDates = _currentRange.map { rangeType ->
        val calendar = Calendar.getInstance()
        val end = calendar.time

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (rangeType == "YEAR") {
            calendar.set(Calendar.DAY_OF_YEAR, 1)
        } else {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = calendar.time
        rangeType to (start to end)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val categorySales: StateFlow<List<CategoryReport>> = rangeDates.flatMapLatest { (_, dates) ->
        salesRepository.getSalesByCategory(dates.first, dates.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailySales: StateFlow<List<DailySales>> = rangeDates.flatMapLatest { (rangeType, dates) ->
        if (rangeType == "YEAR") {
            salesRepository.getMonthlySales(dates.first, dates.second)
        } else {
            salesRepository.getDailySales(dates.first, dates.second)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val hourlySales: StateFlow<List<DailySales>> = rangeDates.flatMapLatest { (_, dates) ->
        salesRepository.getHourlySales(dates.first, dates.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenseBreakdown: StateFlow<List<CategoryTotal>> = rangeDates.flatMapLatest { (_, dates) ->
        mainRepository.getExpensesByCategory(dates.first, dates.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _historyDate = MutableStateFlow<Date?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMonthVentes: StateFlow<List<VenteEntity>> = _historyDate.filterNotNull().flatMapLatest { date ->
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.time
        
        calendar.add(Calendar.MONTH, 1)
        val end = calendar.time
        salesRepository.getVentesByDateRange(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadCurrentStats(rangeType: String = "MONTH") {
        _currentRange.value = rangeType
    }

    fun loadVentesForMonth(date: Date) {
        _historyDate.value = date
    }

    fun exportSalesData(ventes: List<VenteWithItems>? = null) {
        viewModelScope.launch {
            _uiState.value = ReportsUiState.Processing
            try {
                val dataToExport = ventes ?: salesRepository.allVentesWithItems.first()
                val file = reportManager.generateSalesCsv(dataToExport)
                if (file != null) {
                    shareManager.shareFile(file, "text/csv", "Partager le rapport")
                    _uiState.value = ReportsUiState.Success
                } else {
                    _uiState.value = ReportsUiState.Error("Erreur lors de la création du fichier CSV")
                }
            } catch (e: Exception) {
                _uiState.value = ReportsUiState.Error(e.message ?: "Erreur d'exportation")
            }
        }
    }

    fun shareDailySummary() {
        viewModelScope.launch {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = calendar.time
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val end = calendar.time

            val b = mainRepository.boutique.first()
            val cash = salesRepository.getTotalCash(start, end).first() ?: 0.0
            val momo = salesRepository.getTotalMomo(start, end).first() ?: 0.0
            val expenses = mainRepository.getTotalExpenses(start, end).first() ?: 0.0
            val profit = salesRepository.getProfitFlow(start, end).first()

            val text = FormatUtil.generateDailySummary(
                boutiqueName = b?.name ?: "Boutique",
                date = Date(),
                totalSales = cash + momo,
                cashSales = cash,
                momoSales = momo,
                debtsCreated = 0.0,
                expenses = expenses,
                netProfit = profit,
                currency = b?.currency ?: "FCFA"
            )
            shareManager.shareText(text, "Partager le résumé")
        }
    }

    fun clearUiState() {
        _uiState.value = ReportsUiState.Idle
    }
}
