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

package org.projectforge.framework.persistence.api

import jakarta.persistence.EntityManager
import mu.KotlinLogging
import org.apache.commons.lang3.Validate
import org.hibernate.search.mapper.orm.Search
import org.projectforge.framework.ToStringUtil
import org.projectforge.framework.access.AccessException
import org.projectforge.framework.access.OperationType
import org.projectforge.framework.persistence.user.entities.PFUserDO


private val log = KotlinLogging.logger {}

/**
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
object BaseDaoSupport {
    class ResultObject<O : ExtendedBaseDO<Int>>(
        var dbObjBackup: O? = null,
        var wantsReindexAllDependentObjects: Boolean = false,
        var modStatus: EntityCopyStatus? = null
    )

    @JvmStatic
    fun <O : ExtendedBaseDO<Int>> internalSave(baseDao: BaseDao<O>, obj: O): Int? {
        preInternalSave(baseDao, obj)
        baseDao.persistenceService.runInTransaction { context ->
            internalSave(context.em, baseDao, obj)
            null
        }
        postInternalSave(baseDao, obj)
        return obj.id
    }

    private fun <O : ExtendedBaseDO<Int>> internalSave(em: EntityManager, baseDao: BaseDao<O>, obj: O) {
        // BaseDaoJpaAdapter.prepareInsert(em, obj)
        em.persist(obj)
        if (baseDao.logDatabaseActions) {
            log.info("New ${baseDao.doClass.simpleName} added (${obj.id}): $obj")
        }
        baseDao.prepareHibernateSearch(obj, OperationType.INSERT)
        em.merge(obj)
        try {
            em.flush()
        } catch (ex: Exception) {
            // Exception stack trace:
            // org.postgresql.util.PSQLException: FEHLER: ungültige Byte-Sequenz für Kodierung »UTF8«: 0x00
            log.error("${ex.message} while saving object: ${ToStringUtil.toJsonString(obj)}", ex)
            throw ex
        }

        // HistoryBaseDaoAdapter.inserted(emgr, obj)
    }

    private fun <O : ExtendedBaseDO<Int>> preInternalSave(baseDao: BaseDao<O>, obj: O) {
        Validate.notNull<O>(obj)
        obj.setCreated()
        obj.setLastUpdate()
        baseDao.onSave(obj)
        baseDao.onSaveOrModify(obj)
    }

    private fun <O : ExtendedBaseDO<Int>> postInternalSave(baseDao: BaseDao<O>, obj: O) {
        baseDao.afterSaveOrModify(obj)
        baseDao.afterSave(obj)
    }

    @JvmStatic
    fun <O : ExtendedBaseDO<Int>> internalUpdate(
        baseDao: BaseDao<O>,
        obj: O,
        checkAccess: Boolean
    ): EntityCopyStatus? {
        preInternalUpdate(baseDao, obj, checkAccess)
        val res = ResultObject<O>()
        baseDao.persistenceService.runInTransaction { context ->
            internalUpdate(context.em, baseDao, obj, checkAccess, res)
        }
        postInternalUpdate(baseDao, obj, res)
        return res.modStatus
    }

    private fun <O : ExtendedBaseDO<Int>> preInternalUpdate(baseDao: BaseDao<O>, obj: O, checkAccess: Boolean) {
        baseDao.beforeSaveOrModify(obj)
        baseDao.onSaveOrModify(obj)
        if (checkAccess) {
            baseDao.accessChecker.checkRestrictedOrDemoUser()
        }
    }

    private fun <O : ExtendedBaseDO<Int>> internalUpdate(
        em: EntityManager,
        baseDao: BaseDao<O>,
        obj: O,
        checkAccess: Boolean,
        res: ResultObject<O>
    ) {
        val dbObj = em.find(baseDao.doClass, obj.id)
        if (checkAccess) {
            baseDao.checkLoggedInUserUpdateAccess(obj, dbObj)
        }
        baseDao.onChange(obj, dbObj)
        if (baseDao.supportAfterUpdate) {
            res.dbObjBackup = baseDao.getBackupObject(dbObj)
        } else {
            res.dbObjBackup = null
        }
        res.wantsReindexAllDependentObjects = baseDao.wantsReindexAllDependentObjects(obj, dbObj)
        val result = baseDao.copyValues(obj, dbObj)
        res.modStatus = result
        if (result != EntityCopyStatus.NONE) {
            log.error("*********** To fix: BaseDaoJpaAdapter.prepareUpdate(emgr, dbObj) and history")
            //BaseDaoJpaAdapter.prepareUpdate(emgr, dbObj)
            dbObj.setLastUpdate()
            // } else {
            //   log.info("No modifications detected (no update needed): " + dbObj.toString());
            baseDao.prepareHibernateSearch(obj, OperationType.UPDATE)
            em.merge(dbObj)
            try {
                em.flush()
            } catch (ex: Exception) {
                // Exception stack trace:
                // org.postgresql.util.PSQLException: FEHLER: ungültige Byte-Sequenz für Kodierung »UTF8«: 0x00
                log.error("${ex.message} while updating object: ${ToStringUtil.toJsonString(obj)}", ex)
                throw ex
            }
            if (baseDao.logDatabaseActions) {
                log.info("${baseDao.doClass.simpleName} updated: $dbObj")
            }
            flushSearchSession(em)
        }
        /*
        res.modStatus = HistoryBaseDaoAdapter.wrapHistoryUpdate(em, dbObj) {
          val result = baseDao.copyValues(obj, dbObj)
          if (result != ModificationStatus.NONE) {
            BaseDaoJpaAdapter.prepareUpdate(emgr, dbObj)
            dbObj.setLastUpdate()
            // } else {
            //   log.info("No modifications detected (no update needed): " + dbObj.toString());
            baseDao.prepareHibernateSearch(obj, OperationType.UPDATE)
            em.merge(dbObj)
            try {
              em.flush()
            } catch (ex: Exception) {
              // Exception stack trace:
              // org.postgresql.util.PSQLException: FEHLER: ungültige Byte-Sequenz für Kodierung »UTF8«: 0x00
              log.error("${ex.message} while updating object: ${ToStringUtil.toJsonString(obj)}", ex)
              throw ex
            }
            if (baseDao.logDatabaseActions) {
              log.info("${baseDao.clazz.simpleName} updated: $dbObj")
            }
            baseDao.flushSearchSession(em)
          }
          result
        }*/
    }

    private fun <O : ExtendedBaseDO<Int>> postInternalUpdate(baseDao: BaseDao<O>, obj: O, res: ResultObject<O>) {
        baseDao.afterSaveOrModify(obj)
        if (baseDao.supportAfterUpdate) {
            baseDao.afterUpdate(obj, res.dbObjBackup, res.modStatus != EntityCopyStatus.NONE)
            baseDao.afterUpdate(obj, res.dbObjBackup)
        } else {
            baseDao.afterUpdate(obj, null, res.modStatus != EntityCopyStatus.NONE)
            baseDao.afterUpdate(obj, null)
        }
        if (res.wantsReindexAllDependentObjects) {
            baseDao.reindexDependentObjects(obj)
        }
    }


    @JvmStatic
    fun <O : ExtendedBaseDO<Int>> internalMarkAsDeleted(baseDao: BaseDao<O>, obj: O) {
        /*if (!HistoryBaseDaoAdapter.isHistorizable(obj)) {
          log.error(
            "Object is not historizable. Therefore, marking as deleted is not supported. Please use delete instead."
          )
          throw InternalErrorException("exception.internalError")
        }*/
        baseDao.onDelete(obj)
        baseDao.persistenceService.runInTransaction { context ->
            val em = context.em
            val dbObj = em.find(baseDao.doClass, obj.id)
            baseDao.onSaveOrModify(obj)

            /*
            HistoryBaseDaoAdapter.wrapHistoryUpdate(emgr, dbObj) {
              BaseDaoJpaAdapter.beforeUpdateCopyMarkDelete(dbObj, obj)*/
            log.error("****** TODO: History stuff")
            baseDao.copyValues(obj, dbObj) // If user has made additional changes.
            dbObj.deleted = true
            dbObj.setLastUpdate()
            obj.deleted = true                     // For callee having same object.
            obj.lastUpdate = dbObj.lastUpdate // For callee having same object.
            em.merge(dbObj) //
            em.flush()/*
              baseDao.flushSearchSession(em)
              null
            }*/
            if (baseDao.logDatabaseActions) {
                log.info("${baseDao.doClass.simpleName} marked as deleted: $dbObj")
            }
        }
        baseDao.afterSaveOrModify(obj)
        baseDao.afterDelete(obj)
    }

    @JvmStatic
    fun <O : ExtendedBaseDO<Int>> internalUndelete(baseDao: BaseDao<O>, obj: O) {
        baseDao.onSaveOrModify(obj)
        baseDao.persistenceService.runInTransaction { context ->
            val em = context.em
            val dbObj = em.find(baseDao.doClass, obj.id)
            log.error("****** TODO: History stuff")
            /* HistoryBaseDaoAdapter.wrapHistoryUpdate(emgr, dbObj) {
              BaseDaoJpaAdapter.beforeUpdateCopyMarkUnDelete(dbObj, obj)*/
            baseDao.copyValues(obj, dbObj) // If user has made additional changes.
            dbObj.deleted = false
            dbObj.setLastUpdate()
            obj.deleted = false                   // For callee having same object.
            obj.lastUpdate = dbObj.lastUpdate // For callee having same object.
            em.merge(dbObj)
            em.flush()
            //baseDao.flushSearchSession(em)
            if (baseDao.logDatabaseActions) {
                log.info("${baseDao.doClass.simpleName} undeleted: $dbObj")
            }
            dbObj
        }

        baseDao.afterSaveOrModify(obj)
        baseDao.afterUndelete(obj)
    }

    @JvmStatic
    fun <O : ExtendedBaseDO<Int>> internalForceDelete(baseDao: BaseDao<O>, obj: O) {
        /*if (!HistoryBaseDaoAdapter.isHistorizable(obj)) {
          log.error(
            "Object is not historizable. Therefore use normal delete instead."
          )
          throw InternalErrorException("exception.internalError")
        }*/
        if (!baseDao.isForceDeletionSupport) {
            val msg = "Force deletion not supported by '${baseDao.doClass.name}'. Use markAsDeleted instead for: $obj"
            log.error(msg)
            throw RuntimeException(msg)
        }
        val id = obj.id
        if (id == null) {
            val msg = "Could not destroy object unless id is not given: $obj"
            log.error(msg)
            throw RuntimeException(msg)
        }
        throw RuntimeException("TODO: History stuff")
        /*
        baseDao.onDelete(obj)
        val userGroupCache = UserGroupCache.getInstance()
        baseDao.persistenceService.runInTransaction { em ->
            val dbObj = em.find(baseDao.dOClass, id)
            em.remove(dbObj)
            val masterClass: Class<out HistoryMasterBaseDO<*, *>?> =
              PfHistoryMasterDO::class.java //HistoryServiceImpl.getHistoryMasterClass()
            emgr.selectAttached(
              masterClass,
              "select h from ${masterClass.name} h where h.entityName = :entityName and h.entityId = :entityId",
              "entityName", baseDao.clazz.name, "entityId", id.toLong()
            ).forEach { historyEntry ->
              em.remove(historyEntry)
              val displayHistoryEntry = if (historyEntry != null) {
                ToStringUtil.toJsonString(DisplayHistoryEntry(userGroupCache, historyEntry))
              } else {
                "???"
              }
              log.info(
                "${baseDao.clazz.simpleName}:$id (forced) deletion of history entry: $displayHistoryEntry"
              )
            }
            if (baseDao.logDatabaseActions) {
                log.info("${baseDao.dOClass.simpleName} (forced) deleted: $dbObj")
            }
        }
        baseDao.afterDelete(obj)*/
    }

    /**
     * Bulk update.
     */
    @JvmStatic
    fun <O : ExtendedBaseDO<Int>> internalSaveOrUpdate(baseDao: BaseDao<O>, col: Collection<O>) {
        baseDao.persistenceService.runInTransaction { context ->
            val em = context.em
            for (obj in col) {
                if (obj.id != null) {
                    preInternalUpdate(baseDao, obj, false)
                    val res = ResultObject<O>()
                    internalUpdate(em, baseDao, obj, false, res)
                    postInternalUpdate<O>(baseDao, obj, res)
                } else {
                    preInternalSave(baseDao, obj)
                    internalSave(em, baseDao, obj)
                    postInternalSave(baseDao, obj)
                }
            }
        }
    }

    /**
     * Bulk update.
     * @param col Entries to save or update without check access.
     * @param blockSize The block size of commit blocks.
     */
    @JvmStatic
    fun <O : ExtendedBaseDO<Int>> internalSaveOrUpdate(baseDao: BaseDao<O>, col: Collection<O>, blockSize: Int) {
        val list: MutableList<O> = ArrayList<O>()
        var counter = 0
        for (obj in col) {
            list.add(obj)
            if (++counter >= blockSize) {
                counter = 0
                internalSaveOrUpdate(baseDao, list)
                list.clear()
            }
        }
        internalSaveOrUpdate(baseDao, list)
    }

    @JvmStatic
    @JvmOverloads
    fun returnFalseOrThrowException(
        throwException: Boolean,
        user: PFUserDO? = null,
        operationType: OperationType? = null,
        msg: String = "access.exception.noAccess",
    ): Boolean {
        if (throwException) {
            val ex = AccessException(user, msg, operationType)
            ex.operationType = operationType
            throw ex
        }
        return false
    }

    private fun flushSearchSession(em: EntityManager?) {
        if (LUCENE_FLUSH_ALWAYS) {
            val searchSession = Search.session(em)
            // Flushing the index changes asynchonously
            searchSession.indexingPlan().execute()
        }
    }

    private const val LUCENE_FLUSH_ALWAYS = false
}
