package com.reconsiliation.caisse.platform

import com.reconsiliation.caisse.data.local.dao.VenteWithItems
import com.reconsiliation.caisse.data.local.entity.VenteEntity
import com.reconsiliation.caisse.data.local.entity.VenteItemEntity
import com.reconsiliation.caisse.data.repository.FakeContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class ReportManagerTest {

    private lateinit var reportManager: ReportManager
    private lateinit var context: FakeContext

    @Before
    fun setup() {
        context = FakeContext()
        reportManager = ReportManager(context)
    }

    @Test
    fun `generateSalesCsv should produce valid CSV with BOM and headers`() {
        val now = Date()
        val vente = VenteEntity(
            id = 1,
            amount = 100.0,
            description = "Vente Test",
            date = now,
            paymentMethod = "CASH",
            status = "CONFIRMED",
            fees = 5.0,
            discount = 0.0,
            taxAmount = 10.0
        )
        val items = listOf(
            VenteItemEntity(venteId = 1, productId = 1, productName = "Produit 1", quantity = 2.0, unitPrice = 50.0, purchasePrice = 30.0)
        )
        val list = listOf(VenteWithItems(vente, items))

        val file = reportManager.generateSalesCsv(list)
        
        assertNotNull(file)
        assertTrue(file!!.exists())
        
        val content = file.readBytes()
        
        // Check BOM (EF BB BF)
        assertEquals(0xEF.toByte(), content[0])
        assertEquals(0xBB.toByte(), content[1])
        assertEquals(0xBF.toByte(), content[2])
        
        val textContent = String(content, 3, content.size - 3, Charsets.UTF_8)
        val lines = textContent.split("\n").filter { it.isNotBlank() }
        
        // Check Header
        assertTrue(lines[0].startsWith("ID;Date;Description;Montant;Mode"))
        
        // Check Data Line
        assertTrue(lines[1].contains("Vente Test"))
        assertTrue(lines[1].contains("CASH"))
        assertTrue(lines[1].contains("Produit 1(2.0)"))
        
        // Check Calculations
        assertTrue(lines[1].contains("60.0"))
        assertTrue(lines[1].contains("35.0"))
    }

    @Test
    fun `generateSalesCsv with empty list should return file with only header`() {
        val file = reportManager.generateSalesCsv(emptyList())
        assertNotNull(file)
        val content = file!!.readText()
        assertTrue(content.contains("ID;Date;Description"))
        // Header + one empty line from split normally, but if using filter it's 1
        val nonEmptyLines = content.lines().filter { it.isNotBlank() }
        assertEquals(1, nonEmptyLines.size)
    }
}
