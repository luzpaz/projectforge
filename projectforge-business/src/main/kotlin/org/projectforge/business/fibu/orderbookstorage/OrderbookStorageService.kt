/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2024 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.business.fibu.orderbookstorage

import com.fasterxml.jackson.core.type.TypeReference
import jakarta.persistence.Tuple
import mu.KotlinLogging
import org.jetbrains.kotlin.ir.types.IdSignatureValues.result
import org.projectforge.business.fibu.AuftragDO
import org.projectforge.business.fibu.AuftragDao
import org.projectforge.framework.json.JsonUtils
import org.projectforge.framework.persistence.database.TupleUtils
import org.projectforge.framework.persistence.jpa.PfPersistenceService
import org.projectforge.framework.time.PFDateTimeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.*
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val log = KotlinLogging.logger {}

@Service
class OrderbookStorageService {
    class Stats(val date: LocalDate, val count: Int, val gzBytes: ByteArray?)

    @Autowired
    private lateinit var auftragDao: AuftragDao

    @Autowired
    private lateinit var orderConverterService: OrderConverterService

    @Autowired
    private lateinit var persistenceService: PfPersistenceService

    /**
     * Stores the current orderbook in the database.
     * If there are no orders, nothing is stored.
     * If today's orderbook is already stored, it will be updated/overwritten.
     * @param incrementalBasedOn if given, this entry contains only the orders which were modified after this date.
     * @param returnGZipBytes if true, the gzipped bytes are returned.
     * @return the date and the number of stored orders.
     */
    @JvmOverloads
    fun storeOrderbook(returnGZipBytes: Boolean = false, incrementalBasedOn: LocalDate? = null): Stats {
        return storeOrderbook(incrementalBasedOn, returnGZipBytes, LocalDate.now())
    }

    /**
     * @param today the date of the orderbook, default is today. This is only for testing purposes.
     */
    internal fun storeOrderbook(
        incrementalBasedOn: LocalDate? = null,
        returnGZipBytes: Boolean = false,
        today: LocalDate,
    ): Stats {
        log.info { "Storing orderbook (incrementalBasedOn=$incrementalBasedOn)..." }
        // First, select all orders that are not deleted:
        val auftragList = auftragDao.select(deleted = false, checkAccess = false)
        val incrementList = if (incrementalBasedOn != null) {
            val basedOn = selectMeta(incrementalBasedOn)
            if (basedOn == null) {
                log.error { "No orderbook found, which based on given date: $incrementalBasedOn. Falling back to full backup." }
                auftragList
            } else {
                val basedOnDate = PFDateTimeUtils.getBeginOfDateAsUtildate(incrementalBasedOn)
                // Filter all orders that were modified at or after the given date:
                auftragList.filter { (it.lastUpdate ?: Date()) >= basedOnDate }
            }
        } else {
            auftragList
        }
        log.info { "Converting ${incrementList.size}/${auftragList.size} orders..." }
        val orderbook = orderConverterService.convertFromAuftragDO(incrementList)
        if (orderbook.isNullOrEmpty()) {
            log.warn { "No orders found to store!!!" }
            return Stats(today, 0, null)
        }
        val count = orderbook.size
        log.info { "Converting ${incrementList.size}/${auftragList.size} orders to json..." }
        val json = JsonUtils.toJson(orderbook, ignoreNullableProps = true)
        log.info { "Zipping ${incrementList.size} orders..." }
        val gzipBytes = gzip(json)
        // Store the orderbook in the database:
        OrderbookStorageDO().also {
            it.date = today
            it.serializedOrderBook = gzipBytes
            it.incrementalBasedOn = incrementalBasedOn
        }.let {
            persistenceService.runInTransaction { context ->
                val entry = selectMeta(today)
                if (entry != null) {
                    entry.serializedOrderBook = it.serializedOrderBook
                    context.em.merge(entry)
                } else {
                    context.em.persist(it)
                }
            }
        }
        log.info { "Storing orderbook done." }
        return Stats(today, count, if (returnGZipBytes) gzipBytes else null)
    }

    fun restoreOrderbook(date: LocalDate): List<AuftragDO>? {
        val orderbook = mutableMapOf<Long, Order>()
        restoreOrderbook(date, orderbook)
        return orderConverterService.convertFromOrder(orderbook.values)
    }

    private fun restoreOrderbook(date: LocalDate, orderbook: MutableMap<Long, Order>) {
        val entry = findEntry(date)
        if (entry == null) {
            log.error { "No orderbook found for date: $date" }
            return
        }
        if (entry.incremental) {
            log.info { "Restoring orderbook for date $date (incremental based on ${entry.incrementalBasedOn})..." }
        } else {
            log.info { "Restoring orderbook for date $date..." }
        }
        entry.incrementalBasedOn?.let { incrementalBasedOn ->
            log.info { "Restoring orderbook from previous backup first: date=$date..." }
            if (date <= incrementalBasedOn) {
                log.error { "Internal error: Incremental based on date is greater than the date of the orderbook: $incrementalBasedOn > $date" }
            }
            // Load orderbook from the incremental based on date first:
            restoreOrderbook(incrementalBasedOn, orderbook)
        }
        val serialized = entry.serializedOrderBook ?: return
        val json = gunzip(serialized)
        JsonUtils.fromJson(json, object : TypeReference<List<Order?>?>() {})?.forEach { order ->
            order?.id?.let { id ->
                orderbook[id] = order
            }
        }
    }

    private fun selectMeta(date: LocalDate): OrderbookStorageDO? {
        return persistenceService.selectNamedSingleResult(
            OrderbookStorageDO.FIND_META_BY_DATE,
            Tuple::class.java,
            "date" to date,
        )?.let {
            OrderbookStorageDO().also { result ->
                result.date = TupleUtils.getLocalDate(it, "date")
                result.incrementalBasedOn = TupleUtils.getLocalDate(it, "incrementalBasedOn")
            }
        }
    }

    private fun findEntry(date: LocalDate): OrderbookStorageDO? {
        return persistenceService.find(OrderbookStorageDO::class.java, date)
    }

    private fun gzip(str: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val gzipStream = object : GZIPOutputStream(byteArrayOutputStream) {
            init {
                def.setLevel(Deflater.BEST_COMPRESSION)
            }
        }
        gzipStream.use { gzipStream ->
            gzipStream.write(str.toByteArray(Charsets.UTF_8))
        }
        return byteArrayOutputStream.toByteArray()
    }

    fun gunzip(compressed: ByteArray): String {
        val byteArrayInputStream = ByteArrayInputStream(compressed)
        GZIPInputStream(byteArrayInputStream).use { gzipStream ->
            return gzipStream.readBytes().toString(Charsets.UTF_8)
        }
    }
}
