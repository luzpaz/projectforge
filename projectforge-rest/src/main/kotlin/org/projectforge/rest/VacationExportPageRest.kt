/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2023 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.rest

import mu.KotlinLogging
import org.projectforge.business.fibu.EmployeeDO
import org.projectforge.business.fibu.api.EmployeeService
import org.projectforge.business.user.UserGroupCache
import org.projectforge.business.vacation.model.VacationStatus
import org.projectforge.business.vacation.service.VacationExcelExporter
import org.projectforge.business.vacation.service.VacationService
import org.projectforge.framework.time.PFDay
import org.projectforge.model.rest.RestPaths
import org.projectforge.rest.config.Rest
import org.projectforge.rest.config.RestUtils
import org.projectforge.rest.core.AbstractDynamicPageRest
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.dto.*
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("${Rest.URL}/vacationExport")
class VacationExportPageRest : AbstractDynamicPageRest() {
  class Data {
    var startDate: LocalDate? = null
    var numberOfMonths: Int? = null
    var groups: List<Group>? = null
    var employees: List<Employee>? = null
  }

  @Autowired
  private lateinit var employeeService: EmployeeService

  @Autowired
  private lateinit var userGroupCache: UserGroupCache

  @Autowired
  private lateinit var vacationService: VacationService

  @GetMapping("dynamic")
  fun getForm(): FormLayoutData {
    val layout = UILayout("vacation.export.title")
    layout.add(UIInput(Data::startDate.name, dataType = UIDataType.DATE))
      .add(UIInput(Data::numberOfMonths.name, dataType = UIDataType.INT))
      .add(
        UISelect<Int>(
          Data::employees.name, multi = true,
          label = "fibu.employees",
          autoCompletion = AutoCompletion.getAutoCompletion4Employees(true),
        )
      )
      .add(
        UISelect<Int>(
          Data::groups.name, multi = true,
          label = "groups",
          autoCompletion = AutoCompletion.getAutoCompletion4Groups(),
        )
      )
    layout.addAction(
      UIButton.createDownloadButton(
        id = "excelExport",
        title = "exportAsXls",
        responseAction = ResponseAction(
          RestResolver.getRestUrl(
            this.javaClass,
            "exportExcel"
          ), targetType = TargetType.DOWNLOAD
        ),
        default = true
      )
    )
    layout.watchFields.addAll(
      arrayOf(
        Data::startDate.name,
        Data::startDate.name,
        Data::groups.name,
        Data::employees.name
      )
    )
    val data = Data()
    return FormLayoutData(data, layout, null)
  }

  /**
   * Will be called, if the user wants to change his/her observeStatus.
   */
  @PostMapping(RestPaths.WATCH_FIELDS)
  @Override
  fun watchFields(request: HttpServletRequest, @Valid @RequestBody postData: PostData<Data>) {
    request.session.setAttribute(SESSION_ATTRIBUTE_DATA, postData.data)
  }

  @GetMapping("exportExcel")
  fun exportExcel(request: HttpServletRequest): ResponseEntity<*> {
    val data = request.session.getAttribute(SESSION_ATTRIBUTE_DATA) as? Data
    val employees = mutableSetOf<EmployeeDO>()
    data?.employees?.forEach { employee ->
      if (employees.none { it.id == employee.id }) {
        val employeeDO = employeeService.getById(employee.id)
        employees.add(employeeDO)
      }
    }
    data?.groups?.forEach { group ->
      userGroupCache.getGroup(group.id)?.assignedUsers?.forEach { user ->
        employeeService.getEmployeeByUserId(user.id)?.let { employeeDO ->
          employees.add(employeeDO)
        }
      }
    }
    var vacations = emptyList<VacationService.VacationsByEmployee>()
    val startDate = data?.startDate ?: LocalDate.now()
    val periodBegin = PFDay.from(startDate).beginOfYear
    val periodEnd = periodBegin.plusYears(1).endOfYear
    if (employees.isNotEmpty()) {
      vacations = vacationService.getVacationOfEmployees(
        employees,
        periodBegin.date,
        periodEnd.date,
        withSpecial = true,
        trimVacations = false,
        VacationStatus.APPROVED,
        VacationStatus.IN_PROGRESS,
      )
    }
    val excel = VacationExcelExporter.export(startDate, vacations, data?.numberOfMonths ?: 3)
    return RestUtils.downloadFile("hurzel.xlsx", excel)
  }

  companion object {
    private const val SESSION_ATTRIBUTE_DATA = "ValidateExportPageRest:data"
  }
}
