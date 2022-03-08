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

import de.micromata.merlin.utils.ReplaceUtils.encodeFilename
import mu.KotlinLogging
import org.projectforge.business.user.ProjectForgeGroup
import org.projectforge.framework.access.OperationType
import org.projectforge.framework.configuration.ConfigXml
import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.persistence.utils.SQLHelper.ensureUniqueResult
import org.projectforge.framework.time.PFDateTime.Companion.now
import org.springframework.stereotype.Repository
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.util.*

private val log = KotlinLogging.logger {}

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
@Repository
open class ScriptDao : BaseDao<ScriptDO>(ScriptDO::class.java) {
  /**
   * Copy old script as script backup if modified.
   *
   * @see org.projectforge.framework.persistence.api.BaseDao.onChange
   */
  override fun onChange(obj: ScriptDO, dbObj: ScriptDO) {
    if (!Arrays.equals(dbObj.script, obj.script)) {
      obj.scriptBackup = dbObj.script
      val suffix = getScriptSuffix(obj)
      val filename = encodeFilename("${getBackupBasefilename(dbObj)}_${obj.name}_${now().isoStringSeconds}.$suffix", true)
      val backupDir = getBackupDir()
      ConfigXml.ensureDir(backupDir)
      val file = File(backupDir, filename)
      try {
        log.info("Writing backup of script to: " + file.absolutePath)
        file.writeText(dbObj.scriptAsString ?: "")
      } catch (ex: IOException) {
        log.error("Error while trying to save backup file of script '" + file.absolutePath + "': " + ex.message, ex)
      }
    }
  }

  fun getBackupFiles(obj: ScriptDO): Array<File>? {
    val baseFilename = getBackupBasefilename(obj)
    val oldBaseFilename = getOldBackupBasefilename(obj)
    val backupDir = getBackupDir()
    if (!backupDir.exists()) {
      return null
    }
    return backupDir.listFiles(FilenameFilter { _: File, name: String -> name.startsWith(baseFilename) || name.startsWith(oldBaseFilename) })
  }

  private fun getBackupDir(): File {
    return File(ConfigXml.getInstance().backupDirectory, "scripts")
  }

  private fun getBackupBasefilename(obj: ScriptDO): String {
    return "script-${obj.id}_"
  }

  private fun getOldBackupBasefilename(obj: ScriptDO): String {
    return encodeFilename("${obj.name}_", true)
  }

  fun getScriptSuffix(obj: ScriptDO): String {
    return if (ScriptExecutor.getScriptType(obj) == ScriptDO.ScriptType.GROOVY) "groovy" else "kts"
  }

  /**
   * User must be member of group controlling or finance.
   *
   * @see org.projectforge.framework.persistence.api.BaseDao.hasDeleteAccess
   */
  override fun hasAccess(
    user: PFUserDO, obj: ScriptDO?, oldObj: ScriptDO?,
    operationType: OperationType,
    throwException: Boolean
  ): Boolean {
    return accessChecker.isUserMemberOfGroup(
      user, throwException, ProjectForgeGroup.CONTROLLING_GROUP,
      ProjectForgeGroup.FINANCE_GROUP
    )
  }

  override fun newInstance(): ScriptDO {
    return ScriptDO()
  }

  open fun execute(
    script: ScriptDO,
    parameters: List<ScriptParameter>,
    additionalVariables: Map<String, Any>
  ): ScriptExecutionResult {
    hasLoggedInUserSelectAccess(script, true)
    val executor = createScriptExecutor(script, additionalVariables, parameters)
    return executor.execute()
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

  private fun createScriptExecutor(
    script: ScriptDO,
    additionalVariables: Map<String, Any?>,
    scriptParameters: List<ScriptParameter>? = null,
  ): ScriptExecutor {
    val scriptExecutor = ScriptExecutor.createScriptExecutor(script)
    scriptExecutor.init(script, this, additionalVariables, scriptParameters)
    return scriptExecutor
  }
}
