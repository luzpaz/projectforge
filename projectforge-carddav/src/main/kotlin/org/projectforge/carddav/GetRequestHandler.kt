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

import mu.KotlinLogging
import org.projectforge.rest.utils.ResponseUtils
import org.springframework.http.HttpStatus

private val log = KotlinLogging.logger {}

internal object GetRequestHandler {
    /**
     * Handles /carddav/users/admin/addressbooks/ProjectForge-129.vcf, method=GET
     * @param writerContext The writer context.
     */
    fun handleGetCall(writerContext: WriterContext) {
        val requestWrapper = writerContext.requestWrapper
        val response = writerContext.response
        val contactList = writerContext.contactList ?: emptyList()
        log.debug { "handleGetCall:  ${requestWrapper.request.method}: '${requestWrapper.requestURI}' body=[${requestWrapper.body}]" }
        val requestedPath = requestWrapper.requestURI
        val contactId = CardDavUtils.extractContactId(requestedPath)
        val contact = contactList.find { it.id == contactId }
        val vcardData = contact?.vcardData
        if (vcardData == null) {
            ResponseUtils.setValues(response, status = HttpStatus.NOT_FOUND)
            return
        }
        val content = vcardData
        // For vCard data, use text/vcard (for version 3.0 or earlier) or text/vcard;charset=utf-8 for newer versions like 4.0.
        // A unique identifier for this version of the resource. This allows clients to detect changes efficiently.
        response.addHeader("ETag", contact.etag)
        response.addHeader("Last-Modified", contact.lastModifiedAsHttpDate)
        ResponseUtils.setValues(response, HttpStatus.OK, contentType = "text/vcard", content = content)
        CardDavServerTestUtils.writeRequestResponseLogInTestMode(requestWrapper, response, content)
        log.debug { "handleGetCall: response=${ResponseUtils.asJson(response)}, content=[${CardDavServerTestUtils.sanitizeContent(content)}]" }
    }
}
