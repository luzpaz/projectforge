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

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ProjektFormatterTest {
    @Test
    fun getProjektKundeAsString() {
        val projekt = ProjektDO().also {
            it.kunde = KundeDO().also { it.name = "ACME" }
            it.name = "ProjectForge"
        }
        val kunde = KundeDO().also { it.name = "Micromata" }
        Assertions.assertEquals(
            "KundeText; Micromata; ACME - ProjectForge",
            ProjektFormatter.formatProjektKundeAsStringWithoutCache(
                projekt,
                kunde,
                "KundeText"
            )
        )
        Assertions.assertEquals(
            "KundeText; Micromata",
            ProjektFormatter.formatProjektKundeAsStringWithoutCache(
                null,
                kunde,
                "KundeText"
            )
        )
        projekt.kunde?.name = "Micromata"
        Assertions.assertEquals(
            "KundeText; Micromata - ProjectForge",
            ProjektFormatter.formatProjektKundeAsStringWithoutCache(
                projekt,
                kunde,
                "KundeText"
            )
        )
        Assertions.assertEquals(
            "Micromata - ProjectForge",
            ProjektFormatter.formatProjektKundeAsStringWithoutCache(
                projekt,
                kunde,
                "Micromata"
            )
        )
        Assertions.assertEquals(
            "Micromata - ProjectForge",
            ProjektFormatter.formatProjektKundeAsStringWithoutCache(projekt)
        )
    }
}
