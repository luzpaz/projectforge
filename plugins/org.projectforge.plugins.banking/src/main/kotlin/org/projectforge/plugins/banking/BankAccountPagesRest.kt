/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2022 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.plugins.banking

import org.projectforge.business.group.service.GroupService
import org.projectforge.business.user.service.UserService
import org.projectforge.framework.persistence.api.MagicFilter
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.core.AbstractPagesRest
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.dto.BankAccount
import org.projectforge.rest.dto.Group
import org.projectforge.rest.dto.User
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("${Rest.URL}/bankAccount")
class BankAccountPagesRest : AbstractDTOPagesRest<BankAccountDO, BankAccount, BankAccountDao>(
  BankAccountDao::class.java,
  "plugins.banking.account.title",
  cloneSupport = AbstractPagesRest.CloneSupport.CLONE
) {
  @Autowired
  private lateinit var groupService: GroupService

  @Autowired
  private lateinit var userService: UserService

  override fun newBaseDTO(request: HttpServletRequest?): BankAccount {
    val account = super.newBaseDTO(request)
    User.getUser(ThreadLocalUserContext.getUserId())?.let {
      account.fullAccessUsers = listOf(it)
    }
    return account
  }

  override fun transformFromDB(obj: BankAccountDO, editMode: Boolean): BankAccount {
    val bankAccount = BankAccount()
    bankAccount.copyFrom(obj)
    // Group names needed by React client (for ReactSelect):
    Group.restoreDisplayNames(bankAccount.fullAccessGroups, groupService)
    Group.restoreDisplayNames(bankAccount.readonlyAccessGroups, groupService)
    Group.restoreDisplayNames(bankAccount.minimalAccessGroups, groupService)

    // Usernames needed by React client (for ReactSelect):
    User.restoreDisplayNames(bankAccount.fullAccessUsers, userService)
    User.restoreDisplayNames(bankAccount.readonlyAccessUsers, userService)
    User.restoreDisplayNames(bankAccount.minimalAccessUsers, userService)

    return bankAccount
  }

  override fun transformForDB(dto: BankAccount): BankAccountDO {
    val bankAccountDO = BankAccountDO()
    dto.copyTo(bankAccountDO)
    return bankAccountDO
  }

  /**
   * LAYOUT List page
   */
  override fun createListLayout(
    request: HttpServletRequest,
    layout: UILayout,
    magicFilter: MagicFilter,
    userAccess: UILayout.UserAccess
  ) {
    agGridSupport.prepareUIGrid4ListPage(
      request,
      layout,
      magicFilter,
      this,
      userAccess = userAccess,
    )
      .add(lc, BankAccountDO::name, BankAccountDO::bank, BankAccountDO::iban, BankAccountDO::description)

    /*layout.add(
      MenuItem(
        "skillmatrix.export",
        i18nKey = "exportAsXls",
        url = "${SkillMatrixServicesRest.REST_EXCEL_EXPORT_PATH}",
        type = MenuItemTargetType.DOWNLOAD
      )
    )*/
  }

  /**
   * LAYOUT Edit page
   */
  override fun createEditLayout(dto: BankAccount, userAccess: UILayout.UserAccess): UILayout {
    val layout = super.createEditLayout(dto, userAccess)
    dto.id?.let { id ->
      layout.add(
        UIDropArea(
          "plugins.banking.account.importDropArea",
          tooltip = "plugins.banking.account.importDropArea.tooltip",
          uploadUrl = RestResolver.getRestUrl(BankingServicesRest::class.java, "import/$id"),
        )
      )
    }
    layout.add(
      lc,
      BankAccountDO::name,
      BankAccountDO::bank,
      BankAccountDO::iban,
      BankAccountDO::bic,
      BankAccountDO::description,
    )
      .add(
        UIFieldset(UILength(md = 12, lg = 12), title = "access.title.heading")
          .add(
            UIRow()
              .add(
                UIFieldset(6, title = "access.users")
                  .add(UISelect.createUserSelect(lc, "fullAccessUsers", true, "plugins.banking.account.fullAccess"))
                  .add(
                    UISelect.createUserSelect(
                      lc,
                      "readonlyAccessUsers",
                      true,
                      "plugins.banking.account.readonlyAccess"
                    )
                  )
              )
              .add(
                UIFieldset(6, title = "access.groups")
                  .add(UISelect.createGroupSelect(lc, "fullAccessGroups", true, "plugins.banking.account.fullAccess"))
                  .add(
                    UISelect.createGroupSelect(
                      lc,
                      "readonlyAccessGroups",
                      true,
                      "plugins.banking.account.readonlyAccess"
                    )
                  )
              )
          )
      )
      .add(lc, BankAccountDO::importSettings)
    return LayoutUtils.processEditPage(layout, dto, this)
  }
}
