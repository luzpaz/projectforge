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

package org.projectforge.carddav

import jakarta.annotation.PostConstruct
import jakarta.servlet.ServletContext
import org.projectforge.rest.config.RestUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
open class CardDavInit {
    @Value("\${projectforge.carddav.basePath:/carddav}")
    private lateinit var cardDavBasePath: String

    @PostConstruct
    private fun initCompanion() {
        CardDavInit.cardDavBasePath = this.cardDavBasePath
    }

    fun init(sc: ServletContext) {
        RestUtils.registerFilter(sc, "CardDavFilter", CardDavFilter::class.java, false, "${getCardDavBasePath()}/*")
    }

    companion object {
        internal var cardDavBasePath: String = "/carddav"

        @JvmStatic
        fun getCardDavBasePath(): String {
            return if (cardDavBasePath == "/") {
                "/users"
            } else {
                "$cardDavBasePath/users"
            }
        }
    }
}
