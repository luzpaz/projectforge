/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.framework.configuration.entities

import de.micromata.genome.db.jpa.xmldump.api.JpaXmlPersist
import org.hibernate.search.annotations.Field
import org.hibernate.search.annotations.Indexed
import org.projectforge.framework.configuration.Configuration
import org.projectforge.framework.configuration.ConfigurationType
import org.projectforge.framework.persistence.api.AUserRightId
import org.projectforge.framework.persistence.entities.DefaultBaseDO
import java.math.BigDecimal
import java.util.*
import javax.persistence.*

/**
 * For configuration entries persisted in the data base. Please access the configuration parameters via
 * [Configuration]
 *
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
@Entity
@Indexed
@Table(name = "T_CONFIGURATION", uniqueConstraints = [UniqueConstraint(columnNames = ["parameter", "tenant_id"])], indexes = [Index(name = "idx_fk_t_configuration_tenant_id", columnList = "tenant_id")])
@JpaXmlPersist(beforePersistListener = [ConfigurationXmlBeforePersistListener::class])
@AUserRightId("ADMIN_CORE")
@org.hibernate.annotations.NamedQueries(
        org.hibernate.annotations.NamedQuery(name = ConfigurationDO.FIND_BY_PARAMETER, query = "from ConfigurationDO where parameter = :parameter"))
class ConfigurationDO : DefaultBaseDO {

    /**
     * If true this parameter is valid for all tenants (in multi-tenancy environments), otherwise this parameter is valid
     * only for the tenant this parameter is assigned to.
     */
    @get:Column(name = "is_global", columnDefinition = "boolean default false")
    var global: Boolean = false

    /**
     * Key under which the configuration value is stored in the database.
     */
    @Field
    @get:Column(length = 255, nullable = false)
    var parameter: String? = null

    /**
     * If entry is not from type STRING then a RuntimeException will be thrown.
     */
    @Field
    @get:Column(length = PARAM_LENGTH)
    var stringValue: String? = null
        get() {
            if(field != null){
                checkType(ConfigurationType.STRING)
            }
            return field
        }

        set(stringValue) {
            if(field != null){
                checkType(ConfigurationType.STRING)
            }
            field = stringValue
        }

    @get:Column
    var intValue: Int? = null
        get() {
            if(field != null){
                checkType(ConfigurationType.INTEGER)
            }
            return field
        }

        set(stringValue) {
            if(field != null){
                checkType(ConfigurationType.INTEGER)
            }
            field = stringValue
        }

    @get:Column(scale = 5, precision = 18)
    var floatValue: BigDecimal? = null
        get() {
            if(field != null){
                checkType(ConfigurationType.FLOAT)
            }
            return field
        }

        set(stringValue) {
            if(field != null){
                checkType(ConfigurationType.FLOAT)
            }
            field = stringValue
        }

    @get:Enumerated(EnumType.STRING)
    @get:Column(length = 20, nullable = false)
    var configurationType: ConfigurationType? = null
        set(type) {
            if (field == null) {
                field = type
            } else if (field == type) {
                // Do nothing.
            } else if (type == ConfigurationType.STRING && field!!.isIn(ConfigurationType.TEXT, ConfigurationType.BOOLEAN,
                            ConfigurationType.TIME_ZONE)) {
                // Do nothing.
            } else if (type == ConfigurationType.INTEGER && field == ConfigurationType.TASK) {
                // Do nothing.
            } else if (type == ConfigurationType.INTEGER && field == ConfigurationType.CALENDAR) {
                // Do nothing.
            } else if (type == ConfigurationType.FLOAT && field == ConfigurationType.PERCENT) {
                // Do nothing.
            } else {
                throw UnsupportedOperationException("Configuration object of type '"
                        + field
                        + "' cannot be changed to type '"
                        + type
                        + "'!")
            }
        }

    /**
     * @return The full i18n key including the i18n prefix "administration.configuration.param.".
     */
    val i18nKey: String
        @Transient
        get() = "administration.configuration.param." + this.parameter!!

    /**
     * @return The full i18n key including the i18n prefix "administration.configuration.param." and the suffix
     * ".description".
     */
    val descriptionI18nKey: String
        @Transient
        get() = "administration.configuration.param." + this.parameter + ".description"

    var timeZoneId: String?
        @Transient
        get() {
            if (stringValue != null) {
                checkType(ConfigurationType.STRING)
            }
            return stringValue
        }
        set(id) {
            if (stringValue != null) {
                checkType(ConfigurationType.STRING)
            }
            if (id != null) {
                TimeZone.getTimeZone(id) ?: throw UnsupportedOperationException("Unsupported time zone: $id")
            }
            this.stringValue = id
        }

    var timeZone: TimeZone?
        @Transient
        get() {
            if (stringValue != null) {
                checkType(ConfigurationType.STRING)
            } else {
                return null
            }
            return TimeZone.getTimeZone(stringValue)
        }
        set(timeZone) = if (timeZone == null) {
            this.stringValue = null
        } else {
            this.stringValue = timeZone.id
        }

    var taskId: Int?
        @Transient
        get() {
            if (intValue != null) {
                checkType(ConfigurationType.TASK)
            }
            return intValue
        }
        set(taskId) {
            if (taskId != null) {
                checkType(ConfigurationType.TASK)
            }
            intValue = taskId
        }

    var calendarId: Int?
        @Transient
        get() {
            if (intValue != null) {
                checkType(ConfigurationType.CALENDAR)
            }
            return intValue
        }
        set(calendarId) {
            if (calendarId != null) {
                checkType(ConfigurationType.CALENDAR)
            }
            intValue = calendarId
        }

    var booleanValue: Boolean?
        @Transient
        get() = if (this.configurationType == ConfigurationType.BOOLEAN) {
            java.lang.Boolean.TRUE.toString() == stringValue
        } else {
            null
        }
        set(booleanValue) {
            this.stringValue = booleanValue?.toString() ?: java.lang.Boolean.FALSE.toString()
        }

    var value: Any?
        @Transient
        get() = if (this.configurationType!!.isIn(ConfigurationType.STRING, ConfigurationType.TEXT, ConfigurationType.TIME_ZONE)) {
            this.stringValue
        } else if (this.configurationType == ConfigurationType.INTEGER
                || this.configurationType == ConfigurationType.TASK
                || this.configurationType == ConfigurationType.CALENDAR) {
            this.intValue
        } else if (this.configurationType == ConfigurationType.FLOAT || this.configurationType == ConfigurationType.PERCENT) {
            this.floatValue
        } else if (this.configurationType == ConfigurationType.BOOLEAN) {
            this.booleanValue
        } else {
            throw UnsupportedOperationException("Unsupported value type: " + this.configurationType!!)
        }
        set(value) {
            if (value == null) {
                stringValue = null
                intValue = null
                floatValue = null
                return
            }
            if (value is String) {
                stringValue = value
            } else {
                throw UnsupportedOperationException("Unsupported value type: " + value.javaClass.name)
            }
        }

    /**
     * Default constructor
     */
    constructor()

    /**
     * Is used for versions without tenant column
     *
     * @param id
     * @param created
     * @param deleted
     * @param lastUpdate
     * @param configurationType
     * @param floatValue
     * @param intValue
     * @param parameter
     * @param stringValue
     */
    constructor(id: Int?, created: Date, deleted: Boolean, lastUpdate: Date,
                configurationType: ConfigurationType, floatValue: BigDecimal, intValue: Int?, parameter: String,
                stringValue: String) {
        this.id = id
        this.created = created
        this.isDeleted = deleted
        this.lastUpdate = lastUpdate
        this.configurationType = configurationType
        this.floatValue = floatValue
        this.intValue = intValue
        this.parameter = parameter
        this.stringValue = stringValue
    }

    fun internalSetConfigurationType(type: ConfigurationType) {
        this.configurationType = type
        when {
            this.configurationType!!.isIn(ConfigurationType.STRING, ConfigurationType.BOOLEAN, ConfigurationType.TEXT,
                    ConfigurationType.TIME_ZONE) -> {
                this.intValue = null
                this.floatValue = null
            }
            this.configurationType!!.isIn(ConfigurationType.INTEGER, ConfigurationType.TASK) -> {
                this.stringValue = null
                this.floatValue = null
            }
            this.configurationType!!.isIn(ConfigurationType.INTEGER, ConfigurationType.CALENDAR) -> {
                this.stringValue = null
                this.floatValue = null
            }
            this.configurationType!!.isIn(ConfigurationType.FLOAT, ConfigurationType.PERCENT) -> {
                this.stringValue = null
                this.intValue = null
            }
            else -> throw UnsupportedOperationException("Unkown type: $type")
        }
    }

    protected fun checkType(type: ConfigurationType) {
        if (this.configurationType != null) {
            if (this.configurationType == type) {
                return
            } else if (type == ConfigurationType.STRING && this.configurationType!!.isIn(ConfigurationType.TEXT, ConfigurationType.BOOLEAN,
                            ConfigurationType.TIME_ZONE)) {
                return
            } else if (type == ConfigurationType.INTEGER && this.configurationType == ConfigurationType.TASK) {
                return
            } else if (type == ConfigurationType.INTEGER && this.configurationType == ConfigurationType.CALENDAR) {
                return
            } else if (type == ConfigurationType.FLOAT && this.configurationType == ConfigurationType.PERCENT) {
                return
            }
        }
        throw UnsupportedOperationException("Configuration object of type '"
                + this.configurationType
                + "' does not support value of type '"
                + type
                + "'!")
    }

    fun setType(type: ConfigurationType): ConfigurationDO {
        if (this.configurationType == null) {
            this.configurationType = type
        } else if (this.configurationType == type) {
            // Do nothing.
        } else if (type == ConfigurationType.STRING && this.configurationType!!.isIn(ConfigurationType.TEXT, ConfigurationType.BOOLEAN,
                        ConfigurationType.TIME_ZONE)) {
            // Do nothing.
        } else if (type == ConfigurationType.INTEGER && this.configurationType == ConfigurationType.TASK) {
            // Do nothing.
        } else if (type == ConfigurationType.INTEGER && this.configurationType == ConfigurationType.CALENDAR) {
            // Do nothing.
        } else if (type == ConfigurationType.FLOAT && this.configurationType == ConfigurationType.PERCENT) {
            // Do nothing.
        } else {
            throw UnsupportedOperationException("Configuration object of type '"
                    + this.configurationType
                    + "' cannot be changed to type '"
                    + type
                    + "'!")
        }
        return this
    }

    companion object {
        internal const val FIND_BY_PARAMETER = "ConfigurationDO_findByParameter"

        const val PARAM_LENGTH = 4000

        fun getPARAM_LENGTH(): Int {
            return PARAM_LENGTH
        }
    }
}
