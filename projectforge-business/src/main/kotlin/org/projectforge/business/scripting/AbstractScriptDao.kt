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

package org.projectforge.business.scripting

import mu.KotlinLogging
import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.utils.SQLHelper.ensureUniqueResult

private val log = KotlinLogging.logger {}

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
abstract class AbstractScriptDao : BaseDao<ScriptDO>(ScriptDO::class.java) {
  override fun newInstance(): ScriptDO {
    return ScriptDO()
  }

  /**
   * @param name of script (case insensitive)
   */
  open fun loadByNameOrId(name: String): ScriptDO? {
    name.toIntOrNull()?.let { id ->
      return getById(id)
    }
    val script = ensureUniqueResult(
      em.createNamedQuery(
        ScriptDO.SELECT_BY_NAME,
        ScriptDO::class.java
      )
        .setParameter("name", "%${name.trim().lowercase()}%")
    )
    hasLoggedInUserSelectAccess(script, true)
    return script
  }

  open fun getScriptVariableNames(script: ScriptDO, additionalVariables: Map<String, Any?>): List<String> {
    val scriptExecutor = createScriptExecutor(script, additionalVariables)
    return scriptExecutor.allVariables.keys.filter { it.isNotBlank() }.sortedBy { it.lowercase() }
  }

  fun execute(
    script: ScriptDO,
    parameters: List<ScriptParameter>,
    additionalVariables: Map<String, Any>
  ): ScriptExecutionResult {
    hasLoggedInUserSelectAccess(script, true)
    val executor = createScriptExecutor(script, additionalVariables, parameters)
    return executor.execute()
  }

  protected fun createScriptExecutor(
    script: ScriptDO,
    additionalVariables: Map<String, Any?>,
    scriptParameters: List<ScriptParameter>? = null,
  ): ScriptExecutor {
    val scriptExecutor = ScriptExecutor.createScriptExecutor(script)
    scriptExecutor.init(script, this, additionalVariables, scriptParameters)
    return scriptExecutor
  }
}
