/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2021 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.framework.i18n

import java.util.*

/**
 * Time left builds human readable localized strings for time to left events, such as: in a few seconds, in a minute, in 19 minutes, in an hour ago, ...
 *
 */
object TimeLeft {
  const val MINUTE = 60
  const val HOUR = 60 * MINUTE
  const val DAY = 24 * HOUR
  const val WEEK = 7 * DAY
  const val MONTH = 30 * DAY
  const val YEAR = 365 * DAY

  /**
   * @param date Date in the future to compare with now. For past dates, a message of [TimeAgo] will be returned
   * @param locale Locale to use for translation.
   * @return Time ago message or an empty string, if no date was given.
   */
  @JvmStatic
  fun getMessage(date: Date?, locale: Locale? = null): String {
    date ?: return ""
    return TimeAgo.translate(getI18nKey(date, true), "timeleft", locale)
  }

  internal fun getI18nKey(date: Date, allowFutureTimes: Boolean): Pair<String, Long> {
    val seconds = (date.time - System.currentTimeMillis()) / 1000
    if (seconds < 0 && allowFutureTimes) {
      return TimeAgo.getI18nKey(date, false)
    }
    return TimeAgo.getUnit(seconds)
  }
}
