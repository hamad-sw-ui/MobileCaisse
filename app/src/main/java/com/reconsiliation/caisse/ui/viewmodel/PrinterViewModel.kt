package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.platform.PrinterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrinterViewModel @Inject constructor(
    application: Application,
    private val repository: MainRepository,
    private val salesRepository: SalesRepository,
    private val printerManager: PrinterManager
) : AndroidViewModel(application) {

    fun printTicket(venteId: Long, printerAddress: String) = viewModelScope.launch(Dispatchers.IO) {
        val venteWithItems = salesRepository.getVenteWithItems(venteId) ?: return@launch
        val boutique = repository.getBoutiqueOnce() ?: return@launch
        printerManager.printReceipt(boutique, venteWithItems.vente, venteWithItems.items, printerAddress)
    }

    fun testPrint(address: String) = viewModelScope.launch(Dispatchers.IO) {
        printerManager.testConnection(address)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<android.bluetooth.BluetoothDevice> {
        val btManager = getApplication<Application>().getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val adapter = btManager.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
