/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.rest.dto

import org.projectforge.common.StringHelper
import org.projectforge.framework.persistence.user.entities.PFUserDO

class User(id: Int? = null,
           displayName: String? = null,
           var username: String? = null,
           var firstname: String? = null,
           var lastname: String? = null,
           var description: String? = null,
           var email: String? = null,
           var deactivated: Boolean = false
) : BaseDTODisplayObject<PFUserDO>(id = id, displayName = displayName) {
    override fun copyFromMinimal(src: PFUserDO) {
        super.copyFromMinimal(src)
        this.username = src.username
    }

    companion object {
        /**
         * Converts csv list of group ids to list of user (only with id and displayName = "???", no other content).
         */
        fun toUserList(str: String?): MutableList<User>? {
            if (str.isNullOrBlank()) return null
            val users = mutableListOf<User>()
            StringHelper.splitToInts(str, ",", false).forEach { users.add(User(it, "???")) }
            return users
        }

        /**
         * Converts user list to ints (of format supported by [toUserList]).
         */
        fun toIntList(users: List<User>?): String? {
            return users?.joinToString { "${it.id}" }
        }
    }
}
