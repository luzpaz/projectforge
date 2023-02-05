/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2023 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.rest.sipgate

import org.projectforge.business.address.AddressDO
import org.projectforge.framework.utils.NumberHelper
import kotlin.reflect.KMutableProperty

/**
 * Fragen sipgate:
 *   - Warum werden Felder, wie firstName, family, notizen etc. nicht übertragen?
 *   - Warum fehlt der Typ bei Adresse?
 *   - Hilfreich wäre eine Reference-ID (String oder Zahl) zum Verknüpfen von Sipgate und Fremdsystemadressen.
 * @author K. Reinhard (k.reinhard@micromata.de)
 */
object SipgateContactSyncService {
  class SyncResult {
    var addressDOOutdated = false // true, if the address has to be updated in ProjectForge.
    var contactOutdated = false   // true, if the contact has to be updated in Sipgate.
  }

  internal var countryPrefixForTestcases: String? = null

  internal fun from(address: AddressDO): SipgateContact {
    val contact = SipgateContact()
    // contact.id
    contact.name = getName(address)
    contact.family = address.name
    contact.given = address.firstName
    // var picture: String? = null
    address.email?.let { contact.email = it }
    address.privateEmail?.let { contact.privateEmail = it }
    val numbers = mutableListOf<SipgateNumber>()
    address.businessPhone?.let { numbers.add(SipgateNumber(it).setWork()) }
    address.mobilePhone?.let { numbers.add(SipgateNumber(it).setCell()) }
    address.privatePhone?.let { numbers.add(SipgateNumber(it).setHome()) }
    address.privateMobilePhone?.let { numbers.add(SipgateNumber(it).setOther()) }
    address.fax?.let { numbers.add(SipgateNumber(it).setFaxWork()) }
    contact.numbers = numbers
    /* Ignore addresses (synchronize will be pain, because not type of addresses will be given by Sipgate.
        val addresses = mutableListOf<SipgateAddress>()
        createAddress(
          addressText = address.addressText,
          addressText2 = address.addressText2,
          zipCode = address.zipCode,
          city = address.city,
          state = address.state,
          country = address.country,
        )?.let { addresses.add(it) }
        createAddress(
          addressText = address.privateAddressText,
          addressText2 = address.privateAddressText2,
          zipCode = address.privateZipCode,
          city = address.privateCity,
          state = address.privateState,
          country = address.privateCountry,
        )?.let { addresses.add(it) }
        if (addresses.isNotEmpty()) {
          contact.addresses = addresses.toTypedArray()
        }
    */

    contact.organization = address.organization
    contact.division = address.division
    contact.scope = SipgateContact.Scope.SHARED
    return contact
  }

  internal fun from(contact: SipgateContact): AddressDO {
    val address = extractName(contact.name)
    // contact.id
    // var picture: String? = null
    address.email = contact.email
    address.privateEmail = contact.privateEmail

    address.businessPhone = contact.work
    address.mobilePhone = contact.cell
    address.privatePhone = contact.home
    address.privateMobilePhone = contact.other
    address.fax = contact.faxWork

    /* Ignore addresses (synchronize will be pain, because not type of addresses will be given by Sipgate.
    val addresses = mutableListOf<SipgateAddress>()
    createAddress(
      addressText = address.addressText,
      addressText2 = address.addressText2,
      zipCode = address.zipCode,
      city = address.city,
      state = address.state,
      country = address.country,
    )?.let { addresses.add(it) }
    createAddress(
      addressText = address.privateAddressText,
      addressText2 = address.privateAddressText2,
      zipCode = address.privateZipCode,
      city = address.privateCity,
      state = address.privateState,
      country = address.privateCountry,
    )?.let { addresses.add(it) }
     */

    address.organization = contact.organization
    address.division = contact.division
    return address
  }

  fun sync() {
    // TODO:
    // Contacts ohne numbers löschen
  }

  /**
   * @param contact given by Sipgate (will be modified, if to be modified).
   * @param address given by ProjectForge (will be modified, if to be modified).
   * @return result with info whether the objects have to be updated or not.
   */
  internal fun sync(
    contact: SipgateContact,
    address: AddressDO,
    syncInfo: SipgateContactSyncDO.SyncInfo?
  ): SyncResult {
    val result = SyncResult()
    if (contact.name != getName(address)) {
      if (syncInfo != null && syncInfo.fieldsInfo["name"] != SipgateContactSyncDO.SyncInfo.hash(contact.name)) {
        // address to be updated
        val adr = extractName(contact.name)
        address.name = adr.name
        address.firstName = adr.firstName
        result.addressDOOutdated = true
      } else {
        contact.name = getName(address)
        result.contactOutdated = true
      }
    }
    sync(contact, SipgateContact::organization, address, AddressDO::organization, syncInfo, result)
    sync(contact, SipgateContact::division, address, AddressDO::division, syncInfo, result)

    sync(contact, SipgateContact::email, address, AddressDO::email, syncInfo, result)
    sync(contact, SipgateContact::privateEmail, address, AddressDO::privateEmail, syncInfo, result)

    sync(contact, SipgateContact::work, address, AddressDO::businessPhone, syncInfo, result)
    sync(contact, SipgateContact::home, address, AddressDO::privatePhone, syncInfo, result)
    sync(contact, SipgateContact::cell, address, AddressDO::mobilePhone, syncInfo, result)
    sync(contact, SipgateContact::other, address, AddressDO::privateMobilePhone, syncInfo, result)
    sync(contact, SipgateContact::faxWork, address, AddressDO::fax, syncInfo, result)
    return result
  }

  internal fun sync(
    contact: SipgateContact,
    contactField: KMutableProperty<*>,
    address: AddressDO,
    addressField: KMutableProperty<*>,
    syncInfo: SipgateContactSyncDO.SyncInfo?,
    result: SyncResult,
  ) {
    val contactValue = contactField.getter.call(contact) as String?
    val addressValue = addressField.getter.call(address)
    if (contactValue != addressValue) {
      if (syncInfo != null && syncInfo.fieldsInfo[addressField.name] != SipgateContactSyncDO.SyncInfo.hash(contactValue)) {
        // remote contact was modified, so local address is outdated.
        addressField.setter.call(address, contactValue)
        result.addressDOOutdated = true
      } else {
        // local address was modified, so remote contact is outdated.
        contactField.setter.call(contact, addressValue)
        result.contactOutdated = true
      }
    }
  }

  internal fun getName(address: AddressDO): String {
    val sb = StringBuilder()
    /*address.title?.let {
      sb.append(it.trim()).append(" ")
    }*/
    address.firstName?.let {
      sb.append(it.trim()).append(" ")
    }
    address.name?.let {
      sb.append(it.trim()).append(" ")
    }
    return sb.toString().trim()
  }

  internal fun extractName(name: String?): AddressDO {
    val address = AddressDO()
    if (name.isNullOrBlank()) {
      return address
    }
    val names = name.split(" ")
    address.name = names.last().trim()
    address.firstName = names.take(names.size - 1).joinToString(" ")
    return address
  }

  /**
   * Tries to find the contact with the best match (
   */
  fun findBestMatch(contacts: List<SipgateContact>, address: AddressDO): SipgateContact? {
    val matches =
      contacts.filter { it.name?.trim()?.lowercase() == getName(address).lowercase() }
    if (matches.isEmpty()) {
      return null
    }
    if (matches.size == 1) {
      return matches.first()
    }
    return matches.maxBy { matchScore(it, address) }
  }

  internal fun matchScore(contact: SipgateContact, address: AddressDO): Int {
    if (contact.name?.trim()?.lowercase() != getName(address).lowercase()) {
      return -1
    }
    var counter = 1
    val numbers = arrayOf(
      extractNumber(address.businessPhone),
      extractNumber(address.mobilePhone),
      extractNumber(address.privateMobilePhone),
      extractNumber(address.privatePhone),
      extractNumber(address.fax),
    )
    contact.numbers?.forEach { number ->
      val extractedNumber = extractNumber(number.number)
      numbers.forEach { if (it != null && extractedNumber == it) ++counter }
    }
    contact.emails?.forEach { email ->
      val str = email.email?.trim()?.lowercase()
      if (str != null && str == address.email?.trim()?.lowercase() || str == address.privateEmail?.trim()
          ?.lowercase()
      ) {
        ++counter
      }
    }
    contact.addresses?.forEach { adr ->
      val str = adr.postalCode?.trim()?.lowercase()
      if (str != null && str == address.zipCode?.trim() || str == address.privateZipCode) {
        ++counter
      }
    }
    if (address.division != null && contact.division?.trim()?.lowercase() == address.division?.trim()?.lowercase()) {
      ++counter
    }
    if (address.organization != null && contact.organization?.trim()?.lowercase() == address.organization?.trim()
        ?.lowercase()
    ) {
      ++counter
    }
    return counter
  }

  private fun extractNumber(number: String?): String? {
    number ?: return null
    if (countryPrefixForTestcases != null) {
      return NumberHelper.extractPhonenumber(number, countryPrefixForTestcases)
    }
    return NumberHelper.extractPhonenumber(number)
  }
}
