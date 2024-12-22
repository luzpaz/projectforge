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
import org.projectforge.business.address.vcard.VCardVersion
import org.projectforge.rest.AddressPagesRest
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
open class CardDavConfig {
    @Value("\${projectforge.carddav.server.enable:true}")
    var enable: Boolean = true
        private set

    /**
     * If set, the user with this name is used for debugging by writing requests and responses to log files.
     * @see CardDavServerDebugWriter
     */
    @Value("\${projectforge.carddav.server.debugUser:}")
    internal var debugUser: String = ""
        private set

    /**
     * Supported: "3.0" or "4.0".
     */
    @Value("\${projectforge.carddav.server.vcardVersion:3.0}")
    private var vcardVersionNumber: String = "3.0"

    @PostConstruct
    private fun postConstruct() {
        CardDavServerDebugWriter.debugUser = debugUser
        AddressPagesRest.carddavServerEnabled = enable
    }

    val vcardVersion: VCardVersion
        get() = VCardVersion.from(vcardVersionNumber)
}
