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

package org.projectforge.framework.persistence.jpa.candh

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.projectforge.business.fibu.AuftragDO
import org.projectforge.business.fibu.AuftragsPositionDO
import org.projectforge.business.orga.ContractDO
import org.projectforge.business.orga.ContractStatus
import org.projectforge.framework.persistence.api.BaseDO
import org.projectforge.framework.persistence.api.EntityCopyStatus
import org.projectforge.framework.persistence.user.entities.GroupDO
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.test.AbstractTestBase
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

class CandHTest : AbstractTestBase() {
    @Test
    fun baseTests() {
        val src = ContractDO()
        val dest = ContractDO()
        assertContracts(src, dest, EntityCopyStatus.NONE)
        src.id = 42
        assertContracts(src, dest, EntityCopyStatus.MAJOR)
        src.title = "Title"
        assertContracts(src, dest, EntityCopyStatus.MAJOR)
        src.attachmentsSize = 100
        assertContracts(src, dest, EntityCopyStatus.MINOR)
        src.attachmentsCounter = 2
        assertContracts(src, dest, EntityCopyStatus.MINOR)
        src.number = 42
        assertContracts(src, dest, EntityCopyStatus.MAJOR)
        src.validFrom = LocalDate.of(2024, Month.SEPTEMBER, 9)
        assertContracts(src, dest, EntityCopyStatus.MAJOR)
        src.status = ContractStatus.SIGNED
        assertContracts(src, dest, EntityCopyStatus.MAJOR)
    }

    @Test
    fun collectionTests() {
        val debug = false // For user in debugging-mode
        val src = GroupDO()
        val dest = GroupDO()
        copyValues(src, dest, EntityCopyStatus.NONE, debug).let { context ->
            Assertions.assertEquals(0, context.historyContext!!.entries.size)
        }
        dest.assignedUsers = mutableSetOf()
        copyValues(src, dest, EntityCopyStatus.NONE, debug).let { context ->
            Assertions.assertEquals(0, context.historyContext!!.entries.size)
        }

        val user1 = createUser(1, "user1")
        val user2 = createUser(2, "user2")
        val user3 = createUser(3, "user3")
        dest.assignedUsers = mutableSetOf(user1, user2)
        copyValues(src, dest, EntityCopyStatus.MAJOR, debug).let { context ->
            Assertions.assertEquals(1, context.historyContext!!.entries.size)
        }
        Assertions.assertNull(dest.assignedUsers)

        src.assignedUsers = mutableSetOf(user1, user2)
        copyValues(src, dest, EntityCopyStatus.MAJOR, debug)
        Assertions.assertEquals(2, dest.assignedUsers!!.size)
        Assertions.assertTrue(dest.assignedUsers!!.any { it.id == 1L })
        Assertions.assertTrue(dest.assignedUsers!!.any { it.id == 2L })

        src.assignedUsers!!.add(user3)
        copyValues(src, dest, EntityCopyStatus.MAJOR, debug)
        Assertions.assertEquals(3, dest.assignedUsers!!.size)
        Assertions.assertTrue(dest.assignedUsers!!.any { it.id == 1L })
        Assertions.assertTrue(dest.assignedUsers!!.any { it.id == 2L })
        Assertions.assertTrue(dest.assignedUsers!!.any { it.id == 3L })

        src.assignedUsers!!.remove(user2)
        copyValues(src, dest, EntityCopyStatus.MAJOR, debug)
        Assertions.assertEquals(2, dest.assignedUsers!!.size)
        Assertions.assertTrue(dest.assignedUsers!!.any { it.id == 1L })
        Assertions.assertTrue(dest.assignedUsers!!.any { it.id == 3L })
    }

    @Test
    fun auftragTest() {
        val debug = true // For user in debugging-mode
        val src = AuftragDO()
        val dest = AuftragDO()
        copyValues(src, dest, EntityCopyStatus.NONE, debug)
        val pos1 = AuftragsPositionDO()
        pos1.auftrag = src
        pos1.nettoSumme = BigDecimal.valueOf(2590, 2)
        src.addPosition(pos1)
        copyValues(src, dest, EntityCopyStatus.MAJOR, debug)
        Assertions.assertEquals(1, dest.positionen!!.size)
        Assertions.assertEquals("25.90", dest.positionen!![0].nettoSumme!!.toString())
        val destPos1 = AuftragsPositionDO()
        destPos1.auftrag = dest
        destPos1.nettoSumme = BigDecimal.valueOf(259, 1)
        destPos1.number = 1
        dest.positionen = mutableListOf(destPos1)
        copyValues(src, dest, EntityCopyStatus.NONE, debug)
        Assertions.assertEquals(
            "25.9",
            dest.positionen!![0].nettoSumme!!.toString(),
            "25.90 should be equals to 25.9, scale of BigDecimal isn't checked."
        )

        pos1.nettoSumme = BigDecimal.TEN
        copyValues(src, dest, EntityCopyStatus.MAJOR, debug)
        Assertions.assertEquals("10", dest.positionen!![0].nettoSumme!!.toString())
    }

    private fun createUser(id: Long, username: String): PFUserDO {
        val user = PFUserDO()
        user.id = id
        user.username = username
        return user
    }

    private fun assertContracts(
        src: ContractDO,
        dest: ContractDO,
        expectedStatus: EntityCopyStatus,
    ) {
        val debug = false // For user in debugging-mode
        copyValues(src, dest, expectedStatus, debug)
        Assertions.assertEquals(src.id, dest.id)
        Assertions.assertEquals(src.title, dest.title)
        Assertions.assertEquals(src.number, dest.number)
        Assertions.assertEquals(src.attachmentsSize, dest.attachmentsSize)
        Assertions.assertEquals(src.attachmentsCounter, dest.attachmentsCounter)
        Assertions.assertEquals(src.validFrom, dest.validFrom)
        Assertions.assertEquals(src.status, dest.status)
    }

    private fun <IdType : Serializable> copyValues(
        src: BaseDO<IdType>,
        dest: BaseDO<IdType>,
        expectedStatus: EntityCopyStatus,
        debug: Boolean,
        createHistory: Boolean = true,
    ): CandHContext {
        val resultContext = CandHContext(debug = debug, createHistory = createHistory)
        CandHMaster.copyValues(src, dest, resultContext)
        Assertions.assertEquals(expectedStatus, resultContext.currentCopyStatus)
        CandHContext(debug = debug, createHistory = createHistory).let { context ->
            CandHMaster.copyValues(src, dest, context)
            Assertions.assertEquals(EntityCopyStatus.NONE, context.currentCopyStatus)
        }
        return resultContext
    }
}
