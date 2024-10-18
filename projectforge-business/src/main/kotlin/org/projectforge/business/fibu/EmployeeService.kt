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

package org.projectforge.business.fibu

import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.apache.commons.collections4.CollectionUtils
import org.projectforge.business.timesheet.TimesheetDao
import org.projectforge.business.timesheet.TimesheetFilter
import org.projectforge.business.vacation.service.VacationService
import org.projectforge.framework.persistence.api.BaseDOPersistenceService
import org.projectforge.framework.persistence.api.BaseSearchFilter
import org.projectforge.framework.persistence.api.EntityCopyStatus
import org.projectforge.framework.persistence.history.DisplayHistoryEntry
import org.projectforge.framework.persistence.history.HistoryBaseDaoAdapter
import org.projectforge.framework.persistence.jpa.PfPersistenceService
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.time.PFDateTime.Companion.from
import org.projectforge.framework.time.PFDateTime.Companion.now
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.*

private val log = KotlinLogging.logger {}

/**
 * Standard implementation of the Employee service interface.
 *
 * @author Roger Rene Kommer (r.kommer.extern@micromata.de)
 */
@Service
class EmployeeService {
    @Autowired
    private lateinit var employeeDao: EmployeeDao

    @Autowired
    private lateinit var vacationService: VacationService

    @Autowired
    private lateinit var timesheetDao: TimesheetDao

    @Autowired
    private lateinit var persistenceService: PfPersistenceService

    @Autowired
    private lateinit var baseDOPersistenceService: BaseDOPersistenceService

    @PostConstruct
    private fun postConstruct() {
        employeeDao.employeeService = this
    }

    fun getList(filter: BaseSearchFilter): List<EmployeeDO> {
        return employeeDao.select(filter)
    }

    fun getEmployeeByUserId(userId: Long?): EmployeeDO? {
        return employeeDao.findByUserId(userId)
    }

    fun hasLoggedInUserInsertAccess(): Boolean {
        return employeeDao.hasLoggedInUserInsertAccess()
    }

    fun hasLoggedInUserInsertAccess(obj: EmployeeDO, throwException: Boolean): Boolean {
        return employeeDao.hasLoggedInUserInsertAccess(obj, throwException)
    }

    fun hasLoggedInUserUpdateAccess(obj: EmployeeDO, dbObj: EmployeeDO, throwException: Boolean): Boolean {
        return employeeDao.hasLoggedInUserUpdateAccess(obj, dbObj, throwException)
    }

    fun hasLoggedInUserDeleteAccess(obj: EmployeeDO, dbObj: EmployeeDO, throwException: Boolean): Boolean {
        return employeeDao.hasLoggedInUserDeleteAccess(obj, dbObj, throwException)
    }

    fun hasDeleteAccess(user: PFUserDO, obj: EmployeeDO, dbObj: EmployeeDO, throwException: Boolean): Boolean {
        return employeeDao.hasDeleteAccess(user, obj, dbObj, throwException)
    }

    fun getAutocompletion(property: String, searchString: String): List<String> {
        return employeeDao.getAutocompletion(property, searchString)
    }

    fun getDisplayHistoryEntries(obj: EmployeeDO): List<DisplayHistoryEntry> {
        return employeeDao.selectDisplayHistoryEntries(obj)
    }

    fun isEmployeeActive(employee: EmployeeDO): Boolean {
        employee.austrittsDatum.let { austrittsdatum ->
            if (austrittsdatum == null) {
                return true
            }
            val now = now()
            val date = from(austrittsdatum)
            return now.isBefore(date)
        }
    }

    fun findAllActive(checkAccess: Boolean): List<EmployeeDO> {
        val employeeList: Collection<EmployeeDO> = if (checkAccess) {
            employeeDao.select(EmployeeFilter())
        } else {
            employeeDao.selectAll(checkAccess = false)
        }
        return employeeList.filter { employee -> isEmployeeActive(employee) }
    }

    fun getEmployeeByStaffnumber(staffnumber: String): EmployeeDO? {
        return employeeDao.getEmployeeByStaffnumber(staffnumber)
    }

    fun getAll(checkAccess: Boolean): List<EmployeeDO> {
        return if (checkAccess) employeeDao.select(EmployeeFilter()) else employeeDao.selectAll(checkAccess = false)
    }

    internal fun selectAllValidityPeriodAttrs(
        employee: EmployeeDO,
        type: EmployeeValidityPeriodAttrType? = null,
    ): List<EmployeeValidityPeriodAttrDO> {
        requireNotNull(employee.id) { "Employee id must not be null." }
        val list = if (type != null) {
            persistenceService.executeQuery(
                "from EmployeeValidityPeriodAttrDO a where a.employee.id = :employeeId and a.type = :type order by a.validFrom desc",
                EmployeeValidityPeriodAttrDO::class.java,
                Pair("employeeId", employee.id),
                Pair("type", type)
            )
        } else {
            persistenceService.executeQuery(
                "from EmployeeValidityPeriodAttrDO a where a.employee.id = :employeeId order by a.validFrom desc",
                EmployeeValidityPeriodAttrDO::class.java,
                Pair("employeeId", employee.id),
            )
        }
        return list
    }

    fun getEmployeeStatus(employee: EmployeeDO): EmployeeStatus? {
        val list = selectAllValidityPeriodAttrs(employee, EmployeeValidityPeriodAttrType.STATUS)
        val validEntry = getActiveEntry(list)
        val status = validEntry?.value
        if (status != null) {
            try {
                return EmployeeStatus.safeValueOf(status)
            } catch (e: IllegalArgumentException) {
                log.error { "Oups, unknown status value in validityPeriodAttr: $validEntry" }
            }
        }
        return null
    }

    fun getAnnualLeaveDays(employee: EmployeeDO?): BigDecimal? {
        return getAnnualLeaveDays(employee, LocalDate.now())
    }

    fun getAnnualLeaveDays(employee: EmployeeDO?, validAtDate: LocalDate?): BigDecimal? {
        if (employee == null || validAtDate == null) { // Should only occur in CallAllPagesTest (Wicket).
            return null
        }
        return getActiveEntry(getAnnualLeaveDayEntries(employee), validAtDate)?.value?.toBigDecimal()
    }

    private fun ensure(validAtDate: LocalDate?): LocalDate {
        return validAtDate ?: LocalDate.of(1970, Month.JANUARY, 1)
    }

    internal fun getActiveEntry(
        entries: List<EmployeeValidityPeriodAttrDO>,
        validAtDate: LocalDate? = null,
    ): EmployeeValidityPeriodAttrDO? {
        var found: EmployeeValidityPeriodAttrDO? = null
        // example
        // null (active before 2021-01-01), 2021-01-01, 2023-05-08 (active)
        val useDate = validAtDate ?: LocalDate.now()
        entries.forEach { entry ->
            if (useDate >= ensure(entry.validFrom)) {
                found.let { f ->
                    if (f == null) {
                        found = entry
                    } else if (ensure(f.validFrom) < ensure(entry.validFrom)) {
                        found = entry // entry is newer!
                    }
                }
            }
        }
        return found
    }

    fun getAnnualLeaveDayEntries(employee: EmployeeDO): List<EmployeeValidityPeriodAttrDO> {
        val list = selectAllValidityPeriodAttrs(employee, EmployeeValidityPeriodAttrType.ANNUAL_LEAVE)
        return list
    }

    fun addNewAnnualLeaveDays(
        employee: EmployeeDO,
        validFrom: LocalDate,
        annualLeaveDays: BigDecimal,
        checkAccess: Boolean = true,
    ): EmployeeValidityPeriodAttrDO {
        return insertValidityPeriodAttr(
            employee,
            validFrom,
            annualLeaveDays.toString(),
            EmployeeValidityPeriodAttrType.ANNUAL_LEAVE,
            checkAccess = checkAccess,
        )
    }

    fun addNewStatus(
        employee: EmployeeDO,
        validFrom: LocalDate,
        status: EmployeeStatus,
        checkAccess: Boolean = true,
    ): EmployeeValidityPeriodAttrDO {
        return insertValidityPeriodAttr(
            employee,
            validFrom,
            status.toString(),
            EmployeeValidityPeriodAttrType.STATUS,
            checkAccess = checkAccess,
        )
    }

    private fun insertValidityPeriodAttr(
        employee: EmployeeDO,
        validFrom: LocalDate,
        value: String,
        type: EmployeeValidityPeriodAttrType,
        checkAccess: Boolean,
    ): EmployeeValidityPeriodAttrDO {
        if (checkAccess) {
            employeeDao.checkLoggedInUserUpdateAccess(employee, employee)
        }
        val attr = EmployeeValidityPeriodAttrDO()
        attr.employee = employee
        attr.validFrom = validFrom
        attr.value = value
        attr.type = type
        attr.created = Date()
        attr.lastUpdate = attr.created
        baseDOPersistenceService.insert(attr)
        return attr
    }

    /**
     * @param employee The employee to update the attribute for. Needed for checkAccess.
     * @param attrDO: The attribute to update.
     * @param checkAccess: If true, the logged in user must have update access to the employee.
     */
    fun updateValidityPeriodAttr(
        employee: EmployeeDO,
        attrDO: EmployeeValidityPeriodAttrDO,
        checkAccess: Boolean = true,
    ): EntityCopyStatus {
        if (checkAccess) {
            employeeDao.checkLoggedInUserUpdateAccess(employee, employee)
        }
        return baseDOPersistenceService.update(attrDO)
    }

    private fun removeValidityPeriodAttr(
        employee: EmployeeDO,
        attrId: Long,
        type: EmployeeValidityPeriodAttrType,
    ) {
        val dbEmployee = employeeDao.find(employee.id)!!
        hasLoggedInUserUpdateAccess(dbEmployee, dbEmployee, true)
        val attr = persistenceService.selectById(EmployeeValidityPeriodAttrDO::class.java, attrId)
        if (attr == null || attr.employee?.id != dbEmployee.id || attr.type != type) {
            throw IllegalArgumentException("No such attribute found for employee#${dbEmployee.id} ${dbEmployee.displayName}: $attrId: $attr")
        }
        persistenceService.runInTransaction { context ->
            val em = context.em
            em.persist(attr)
            log.info("New ${EmployeeValidityPeriodAttrDO::class.simpleName} for employee#${dbEmployee.id} ${dbEmployee.displayName} added (${attr.id}): $attr")
            HistoryBaseDaoAdapter.inserted(attr, context)
            em.flush()
        }
    }

    fun getReportOfMonth(year: Int, month: Int?, user: PFUserDO): MonthlyEmployeeReport {
        val monthlyEmployeeReport = MonthlyEmployeeReport(this, vacationService, user, year, month)
        monthlyEmployeeReport.init()
        val filter = TimesheetFilter()
        filter.setDeleted(false)
        filter.startTime = monthlyEmployeeReport.fromDate
        filter.stopTime = monthlyEmployeeReport.toDate
        filter.userId = user.id
        val list = timesheetDao.select(filter)
        val loggedInUser = ThreadLocalUserContext.requiredLoggedInUser
        if (CollectionUtils.isNotEmpty(list)) {
            for (sheet in list) {
                monthlyEmployeeReport.addTimesheet(
                    sheet,
                    timesheetDao.hasUserSelectAccess(loggedInUser, sheet, false)
                )
            }
        }
        monthlyEmployeeReport.calculate()
        return monthlyEmployeeReport
    }
}
