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

import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.rest.utils.ResponseUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

private val log = KotlinLogging.logger {}

internal object PropFindUtils {
    enum class Prop(val str: String) {
        RESOURCETYPE("resourcetype"),
        DISPLAYNAME("displayname"),
        GETETAG("getetag"),
        GETCTAG("cs:getctag"),
        SYNCTOKEN("sync-token"),
        CURRENT_USER_PRINCIPAL("current-user-principal"),
        CURRENT_USER_PRIVILEGE_SET("current-user-privilege-set")
    }

    /**
     * Handles a PROPFIND request for the current user principal.
     * This is the initial call to the CardDAV server for getting the
     * @param requestWrapper The request wrapper.
     * @param response The response.
     * @param user The user.
     * @see CardDavXmlWriter.generatePropFindResponse
     */
    fun handlePropFindCall(requestWrapper: RequestWrapper, response: HttpServletResponse, user: PFUserDO) {
        log.debug { "handleCurrentUserPrincipal: '${requestWrapper.requestURI}' body=[${requestWrapper.body}]" }
        val props = extractProps(requestWrapper.body)
        if (props.isEmpty()) {
            ResponseUtils.setValues(
                response, HttpStatus.BAD_REQUEST, contentType = MediaType.TEXT_PLAIN_VALUE,
                content = "No properties found in PROPFIND request."
            )
        }
        val content = CardDavXmlWriter.generatePropFindResponse(requestWrapper, user, props)
        log.debug { "handleCurrentUserPrincipal: response=[$content]" }
        ResponseUtils.setValues(
            response,
            HttpStatus.MULTI_STATUS,
            contentType = MediaType.APPLICATION_XML_VALUE,
            content = content,
        )
    }

    /**
     * Handle PROPFIND requests: /carddav/users/<username>
     * The client expects information about the address book of the given user.
     * @param userDO The user for which the address book is requested.
     * @param request The HTTP request.
     * @return The response entity.
     */
     fun handlePropfindUserDirectory(
        requestWrapper: RequestWrapper,
        response: HttpServletResponse,
        userDO: PFUserDO
    ) {
        log.debug { "handlePropfindUserDirectory: PROPFIND '${requestWrapper.requestURI}', body=[${requestWrapper.body}]" }
        val content = CardDavXmlWriter.generatePropfindUserDirectory(requestWrapper, userDO)
        log.debug { "handlePropfindUserDirectory: response=[$content]" }
        ResponseUtils.setValues(
            response,
            HttpStatus.MULTI_STATUS,
            contentType = MediaType.APPLICATION_XML_VALUE,
            content = content,
        )
    }

    /**
     * <propfind xmlns="DAV:">
     *   <prop>
     *     <resourcetype/>
     *     <displayname/>
     *     <current-user-principal/>
     *     <current-user-privilege-set/>
     *   </prop>
     * </propfind>
     */
    fun extractProps(xml: String): List<Prop> {
        val props = mutableListOf<Prop>()
        val propfindXml = xml.substringAfter("<propfind").substringBefore("</propfind>")
        val propXml = propfindXml.substringAfter("<prop>").substringBefore("</prop>")
        Prop.entries.forEach { prop ->
            if ("<${prop.str}" in propXml) {
                props.add(prop)
            }
        }
        return props
    }
}
