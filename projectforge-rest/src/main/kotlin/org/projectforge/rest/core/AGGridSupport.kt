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

package org.projectforge.rest.core

import mu.KotlinLogging
import org.projectforge.framework.persistence.api.MagicFilter
import org.projectforge.framework.persistence.api.QUERY_FILTER_MAX_ROWS
import org.projectforge.rest.multiselect.AbstractMultiSelectedPage
import org.projectforge.rest.multiselect.MultiSelectionSupport
import org.projectforge.ui.UIAgGrid
import org.projectforge.ui.UIAlert
import org.projectforge.ui.UIColor
import org.projectforge.ui.UILayout
import javax.servlet.http.HttpServletRequest

private val log = KotlinLogging.logger {}

/**
 * Supports multi selection and updates of list pages.
 */
object AGGridSupport {
  /**
   * Creates UIGridTable and adds it to the given layout. Will also handle flag layout.hideSearchFilter of
   * multi-selection mode.
   */
  fun prepareUIGrid4ListPage(
    request: HttpServletRequest,
    layout: UILayout,
    magicFilter: MagicFilter,
    pagesRest: AbstractPagesRest<*, *, *>,
    pageAfterMultiSelect: Class<out AbstractDynamicPageRest>? = null,
  ): UIAgGrid {
    val table = UIAgGrid.createUIResultSetTable()
    magicFilter.maxRows = QUERY_FILTER_MAX_ROWS // Fix it from previous.
    table.enablePagination()
    magicFilter.paginationPageSize?.let { table.paginationPageSize = it }
    layout.add(table)
    if (MultiSelectionSupport.isMultiSelection(request, magicFilter)) {
      layout.hideSearchFilter = true
      if (pageAfterMultiSelect != null) {
        table.urlAfterMultiSelect =
          RestResolver.getRestUrl(pageAfterMultiSelect, AbstractMultiSelectedPage.URL_PATH_SELECTED)
      }
      layout
        .add(
          UIAlert(
            message = "multiselection.aggrid.selection.info.message",
            title = "multiselection.aggrid.selection.info.title",
            color = UIColor.INFO,
            markdown = true,
          )
        )
    } else {
      table.withSingleRowClick()
      table.withRowClickRedirectUrl("${PagesResolver.getEditPageUrl(pagesRest::class.java, absolute = true)}/id")
    }
    return table
  }
}
