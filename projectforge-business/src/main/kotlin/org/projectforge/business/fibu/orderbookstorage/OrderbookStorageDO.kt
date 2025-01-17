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

import jakarta.persistence.*
import java.time.LocalDate

/**
 * SELECT date, octet_length(serialized_orderbook) AS byte_count FROM t_fibu_orderbook_storage;
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
@Entity
@Table(
    name = "t_fibu_orderbook_storage",
    uniqueConstraints = [UniqueConstraint(columnNames = ["date"])],
)
@NamedQueries(
    NamedQuery(name = OrderbookStorageDO.FIND_META_BY_DATE, query = "select date as date,incrementalBasedOn as incrementalBasedOn from OrderbookStorageDO where date=:date"),
    NamedQuery(name = OrderbookStorageDO.SELECT_ALL_METAS, query = "select date as date,incrementalBasedOn as incrementalBasedOn from OrderbookStorageDO"),
)
internal class OrderbookStorageDO {
    @get:Id
    @get:Column
    var date: LocalDate? = null

    /**
     * Serialized order book.
     * All orders are serialized as json objects and zipped.
     */
    @get:Column(name = "serialized_orderbook", columnDefinition = "BLOB")
    @get:Basic(fetch = FetchType.LAZY) // Lazy isn't reliable for byte arrays.
    var serializedOrderBook: ByteArray? = null

    /**
     * If given, this entry contains only the orders which were modified after this date.
     */
    @get:Column(name = "incremental_based_on")
    var incrementalBasedOn: LocalDate? = null

    @get:Transient
    val incremental: Boolean
        get() = incrementalBasedOn != null

    companion object {
        internal const val FIND_META_BY_DATE = "OrderStorageDO_FindMetaByDate"
        internal const val SELECT_ALL_METAS = "OrderStorageDO_SelectAllMetas"
    }
}
