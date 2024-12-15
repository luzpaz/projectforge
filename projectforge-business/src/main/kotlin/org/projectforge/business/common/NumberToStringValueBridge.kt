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

package org.projectforge.business.common

import org.hibernate.search.mapper.pojo.bridge.ValueBridge
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeFromIndexedValueContext
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext

class NumberToStringValueBridge : ValueBridge<Number, String> {
    override fun toIndexedValue(value: Number?, context: ValueBridgeToIndexedValueContext?): String? {
        return value?.toString()
    }

    override fun fromIndexedValue(value: String?, context: ValueBridgeFromIndexedValueContext?): Number? {
        return value?.toLongOrNull() ?: value?.toDoubleOrNull() ?: value?.toIntOrNull()
    }

    override fun toString(): String {
        return "NumberToStringValueBridge"
    }
}