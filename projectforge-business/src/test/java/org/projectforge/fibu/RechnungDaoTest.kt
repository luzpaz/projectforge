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

package org.projectforge.fibu

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.projectforge.business.fibu.*
import org.projectforge.common.i18n.UserException
import org.projectforge.framework.access.AccessException
import org.projectforge.framework.persistence.jpa.PfPersistenceContext
import org.projectforge.test.AbstractTestBase
import org.springframework.beans.factory.annotation.Autowired
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate

class RechnungDaoTest : AbstractTestBase() {
    @Autowired
    private lateinit var rechnungDao: RechnungDao

    @Test
    fun testNextNumber() {
            persistenceService.runInTransaction<Any?> { context ->
                var dbNumber = rechnungDao.nextNumber
                logon(TEST_FINANCE_USER)
                val rechnung1 = RechnungDO()
                var number = rechnungDao.getNextNumber(rechnung1, context)
                rechnung1.datum = LocalDate.now()
                rechnung1.faelligkeit = LocalDate.now()
                rechnung1.projekt = initTestDB.addProjekt(null, 1, "foo", context)
                try {
                    rechnungDao.save(rechnung1, context)
                    Assertions.fail("Exception with wrong number should be thrown (no number given).")
                } catch (ex: UserException) {
                    // Expected
                }
                rechnung1.nummer = number
                rechnung1.addPosition(createPosition(1, "50.00", "0", "test"))
                var id: Serializable? = rechnungDao.save(rechnung1, context)
                val rechnung1FromDb = rechnungDao.getById(id, context)
                Assertions.assertEquals(dbNumber++, rechnung1FromDb!!.nummer)

                val rechnung2 = RechnungDO()
                rechnung2.datum = LocalDate.now()
                rechnung2.nummer = number
                try {
                    rechnungDao.save(rechnung2, context)
                    Assertions.fail<Any>("Exception with wrong number should be thrown (does already exists).")
                } catch (ex: UserException) {
                    // Expected.
                }
                number = rechnungDao.getNextNumber(rechnung2, context)
                rechnung2.nummer = number + 1
                rechnung2.faelligkeit = LocalDate.now()
                rechnung2.projekt = initTestDB.addProjekt(null, 1, "foo", context)
                try {
                    rechnungDao.save(rechnung2, context)
                    Assertions.fail<Any>("Exception with wrong number should be thrown (not continuously).")
                } catch (ex: UserException) {
                    // OK
                }
                rechnung2.nummer = number
                rechnung2.addPosition(createPosition(1, "50.00", "0", "test"))
                id = rechnungDao.save(rechnung2, context)
                val rechnung2FromDb = rechnungDao.getById(id, context)
                Assertions.assertEquals(dbNumber++, rechnung2FromDb!!.nummer)

                val rechnung3 = RechnungDO()
                rechnung3.datum = LocalDate.now()
                rechnung3.typ = RechnungTyp.GUTSCHRIFTSANZEIGE_DURCH_KUNDEN
                rechnung3.addPosition(createPosition(1, "50.00", "0", "test"))
                rechnung3.faelligkeit = LocalDate.now()
                rechnung3.projekt = initTestDB.addProjekt(null, 1, "foo", context)
                id = rechnungDao.save(rechnung3, context)
                val rechnung3FromDb = rechnungDao.getById(id, context)
                Assertions.assertNull(rechnung3FromDb!!.nummer)
            }
        }

    @Test
    fun checkAccess() {
        lateinit var rechnung: RechnungDO
        lateinit var id: Serializable
        persistenceService.runInTransaction<Any?> { context ->
            logon(TEST_FINANCE_USER)
            rechnung = RechnungDO()
            val number = rechnungDao.getNextNumber(rechnung, context)
            rechnung.datum = LocalDate.now()
            rechnung.faelligkeit = LocalDate.now()
            rechnung.projekt = initTestDB.addProjekt(null, 1, "foo", context)
            rechnung.nummer = number

            rechnung.addPosition(createPosition(2, "100.50", "0.19", "test"))
            rechnung.addPosition(createPosition(1, "50.00", "0", "test"))
            Assertions.assertEquals("289.19", rechnung.grossSum.setScale(2).toString())
            id = rechnungDao.save(rechnung, context)
            rechnung = rechnungDao.getById(id, context)!!
        }
        persistenceService.runInTransaction { context ->
            logon(TEST_CONTROLLING_USER)
            rechnungDao.getById(id, context)
            checkNoWriteAccess(id, rechnung, "Controlling", context)

            logon(TEST_USER)
            checkNoAccess(id, rechnung, "Other", context)

            logon(TEST_PROJECT_MANAGER_USER)
            checkNoAccess(id, rechnung, "Project manager", context)

            logon(TEST_ADMIN_USER)
            checkNoAccess(id, rechnung, "Admin ", context)
            null
        }
    }

    private fun checkNoAccess(id: Serializable, rechnung: RechnungDO, who: String, context: PfPersistenceContext) {
        try {
            val filter = RechnungFilter()
            rechnungDao.getList(filter, context)
            Assertions.fail<Any>("AccessException expected: $who users should not have select list access to invoices.")
        } catch (ex: AccessException) {
            // OK
        }
        try {
            rechnungDao.getById(id, context)
            Assertions.fail<Any>("AccessException expected: $who users should not have select access to invoices.")
        } catch (ex: AccessException) {
            // OK
        }
        checkNoHistoryAccess(id, rechnung, who)
        checkNoWriteAccess(id, rechnung, who, context)
    }

    private fun checkNoHistoryAccess(id: Serializable, rechnung: RechnungDO, who: String) {
        Assertions.assertFalse(
            rechnungDao.hasLoggedInUserHistoryAccess(false),
            "$who users should not have select access to history of invoices."
        )
        try {
            rechnungDao.hasLoggedInUserHistoryAccess(true)
            Assertions.fail<Any>("AccessException expected: $who users should not have select access to history of invoices.")
        } catch (ex: AccessException) {
            // OK
        }
        Assertions.assertFalse(
            rechnungDao.hasLoggedInUserHistoryAccess(rechnung, false),
            "$who users should not have select access to history of invoices."
        )
        try {
            rechnungDao.hasLoggedInUserHistoryAccess(rechnung, true)
            Assertions.fail<Any>("AccessException expected: $who users should not have select access to history of invoices.")
        } catch (ex: AccessException) {
            // OK
        }
    }

    private fun checkNoWriteAccess(id: Serializable, rechnung: RechnungDO, who: String, context: PfPersistenceContext) {
        try {
            val re = RechnungDO()
            val number = rechnungDao.getNextNumber(re, context)
            re.datum = LocalDate.now()
            re.nummer = number
            rechnungDao.save(re, context)
            Assertions.fail<Any>("AccessException expected: $who users should not have save access to invoices.")
        } catch (ex: AccessException) {
            // OK
        }
        try {
            rechnung.bemerkung = who
            rechnungDao.update(rechnung, context)
            Assertions.fail<Any>("AccessException expected: $who users should not have update access to invoices.")
        } catch (ex: AccessException) {
            // OK
        }
    }

    private fun createPosition(
        menge: Int, einzelNetto: String, vat: String,
        text: String
    ): RechnungsPositionDO {
        val pos = RechnungsPositionDO()
        pos.menge = BigDecimal(menge)
        pos.einzelNetto = BigDecimal(einzelNetto)
        pos.vat = BigDecimal(vat)
        pos.text = text
        return pos
    }
}
