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

package org.projectforge.framework.persistence.jpa

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.projectforge.Constants

class PersistenceStatsTest {
    @Test
    fun tetsStatsToString() {
        assertStats("[transactions=[created=0,active=0],readonlies=[created=0,active=0],duration=00:00.000]")
        assertStats(
            "[transactions=[created=2,active=4],readonlies=[created=1,active=3],duration=00:00.000]",
            createdReadonlies = 1,
            createdTransactions = 2,
            activeReadonlies = 3,
            activeTransactions = 4,
            activeReadonliesSinceLastSave = 3,
            activeTransactionsSinceLastSave = 4,
        )
        assertStats(
            "[transactions=[created=2,active=4,sinceLastSave=6],readonlies=[created=1,active=3,sinceLastSave=5],duration=00:00.000]",
            createdReadonlies = 1,
            createdTransactions = 2,
            activeReadonlies = 3,
            activeTransactions = 4,
            activeReadonliesSinceLastSave = 5,
            activeTransactionsSinceLastSave = 6,
        )
    }

    @Test
    fun testFormatMillis() {
        Assertions.assertEquals(
            "1:07:08.123",
            PersistenceStats.formatMillis(Constants.MILLIS_PER_HOUR + 7 * Constants.MILLIS_PER_MINUTE + 8 * Constants.MILLIS_PER_SECOND + 123)
        )
        Assertions.assertEquals(
            "5:07:08.023",
            PersistenceStats.formatMillis(5 * Constants.MILLIS_PER_HOUR + 7 * Constants.MILLIS_PER_MINUTE + 8 * Constants.MILLIS_PER_SECOND + 23)
        )
        Assertions.assertEquals(
            "128:07:08.123",
            PersistenceStats.formatMillis(128 * Constants.MILLIS_PER_HOUR + 7 * Constants.MILLIS_PER_MINUTE + 8 * Constants.MILLIS_PER_SECOND + 123)
        )
        Assertions.assertEquals(
            "07:08.123",
            PersistenceStats.formatMillis(7 * Constants.MILLIS_PER_MINUTE + 8 * Constants.MILLIS_PER_SECOND + 123)
        )
        Assertions.assertEquals(
            "00:08.123",
            PersistenceStats.formatMillis(8 * Constants.MILLIS_PER_SECOND + 123)
        )
        Assertions.assertEquals(
            "00:00.023",
            PersistenceStats.formatMillis( 23)
        )
    }

    private fun assertStats(
        expected: String,
        createdReadonlies: Int = 0,
        createdTransactions: Int = 0,
        activeReadonlies: Int = 0,
        activeTransactions: Int = 0,
        activeReadonliesSinceLastSave: Int = 0,
        activeTransactionsSinceLastSave: Int = 0,
    ) {
        val stats = PersistenceStats.create(
            createdReadonlies = createdReadonlies,
            createdTransactions = createdTransactions,
            activeReadonlies = activeReadonlies,
            activeTransactions = activeTransactions,
            activeReadonliesSinceLastSave = activeReadonliesSinceLastSave,
            activeTransactionsSinceLastSave = activeTransactionsSinceLastSave,
        )
        Assertions.assertEquals(expected, stats.toString())
    }
}
