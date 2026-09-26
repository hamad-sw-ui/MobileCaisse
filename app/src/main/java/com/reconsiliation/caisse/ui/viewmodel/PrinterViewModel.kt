package com.reconsiliation.caisse.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reconsiliation.caisse.data.repository.MainRepository
import com.reconsiliation.caisse.data.repository.SalesRepository
import com.reconsiliation.caisse.permissions.PermissionPolicy
import com.reconsiliation.caisse.platform.PrinterManager
import com.reconsiliation.caisse.ui.permissions.hasPermission
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

    fun hasBluetoothAccess(): Boolean {
        val permission = PermissionPolicy.bluetoothConnectPermission(Build.VERSION.SDK_INT)
            ?: return true
        return getApplication<Application>().hasPermission(permission)
    }

    fun printTicket(venteId: Long, printerAddress: String) = viewModelScope.launch(Dispatchers.IO) {
        if (!hasBluetoothAccess()) return@launch
        val venteWithItems = salesRepository.getVenteWithItems(venteId) ?: return@launch
        val boutique = repository.getBoutiqueOnce() ?: return@launch
        printerManager.printReceipt(boutique, venteWithItems.vente, venteWithItems.items, printerAddress)
    }

    fun testPrint(address: String) = viewModelScope.launch(Dispatchers.IO) {
        if (!hasBluetoothAccess()) return@launch
        printerManager.testConnection(address)
    }

    fun getPairedPrinters(): List<BluetoothDevice> {
        if (!hasBluetoothAccess()) return emptyList()
        val btManager = getApplication<Application>().getSystemService(android.content.Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.toList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
