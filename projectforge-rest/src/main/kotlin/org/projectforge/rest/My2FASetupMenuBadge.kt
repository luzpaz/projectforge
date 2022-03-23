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

package org.projectforge.rest

import org.projectforge.SystemStatus
import org.projectforge.business.user.UserAuthenticationsService
import org.projectforge.framework.cache.AbstractCache
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.menu.builder.MenuCreator
import org.projectforge.menu.builder.MenuItemDefId
import org.projectforge.sms.SmsSenderConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

/**
 * Menu badge is 1 or empty, depends on whether the user has setup all 2FAs or not.
 * The state of each user will be cached for performance reasons.
 */
@Service
class My2FASetupMenuBadge : AbstractCache(TICKS_PER_HOUR) {
  @Autowired
  private lateinit var authenticationsService: UserAuthenticationsService

  @Autowired
  private lateinit var menuCreator: MenuCreator

  @Autowired
  private lateinit var smsSenderConfig: SmsSenderConfig

  private var smsConfigured: Boolean = false

  /**
   * State by userId. True: setup finished, false: badge counter is 1.
   */
  private val stateMap = mutableMapOf<Int, Boolean>()

  @PostConstruct
  private fun postConstruct() {
    menuCreator.findById(MenuItemDefId.MY_2FA_SETUP)!!.badgeCounter = { badgeCounter }
    smsConfigured = smsSenderConfig.isSmsConfigured() || SystemStatus.isDevelopmentMode()
  }

  /**
   * The badgeCounter for the logged-in user (will be cached).
   */
  val badgeCounter: Int?
    get() {
      ThreadLocalUserContext.getUser()?.let { user ->
        synchronized(stateMap) {
          var state = stateMap[user.id]
          if (state == null) {
            state = !authenticationsService.getAuthenticatorToken()
              .isNullOrBlank() && (!smsConfigured || !user.mobilePhone.isNullOrBlank())
            stateMap[user.id] = state
          }
          return if (state) {
            null // State is OK (everything is configured)
          } else {
            1 // User has to configure 2FA.
          }
        }
      }
      return null // Shouldn't occur (ThreadLocalUser should always be given).
    }

  /**
   * Refresh the state for a single user. Will be called by [My2FASetupPageRest] for having an up-to-date state
   * after any modifications.
   */
  fun refreshUserBadgeCounter() {
    ThreadLocalUserContext.getUserId()?.let {
      synchronized(stateMap) {
        stateMap.remove(it)
      }
    }
  }

  override fun refresh() {
    synchronized(stateMap) {
      stateMap.clear()
    }
  }
}
