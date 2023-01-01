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

package org.projectforge.rest.dvelop

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import mu.KotlinLogging
import org.projectforge.business.dvelop.DvelopConfiguration
import org.projectforge.business.dvelop.TradingPartner
import org.projectforge.framework.json.JsonUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

private val log = KotlinLogging.logger {}

/**
 * Handles the login to the dvelop server (if configured and in use).
 *
 * @author K. Reinhard (k.reinhard@micromata.de)
 */
@Service
class DvelopClient {
  @Autowired
  internal lateinit var dvelopConfiguration: DvelopConfiguration

  internal var debugConsoleOutForTesting = false

  private val timeoutMillis = 30000
  private val timeoutMillisLong = 30000L

  private var httpClient: HttpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis)
    .responseTimeout(Duration.ofMillis(timeoutMillisLong))
    .doOnConnected { conn ->
      conn.addHandlerLast(ReadTimeoutHandler(timeoutMillisLong, TimeUnit.MILLISECONDS))
        .addHandlerLast(WriteTimeoutHandler(timeoutMillisLong, TimeUnit.MILLISECONDS))
    }

  private lateinit var webClient: WebClient

  class HttpException(val httpStatus: HttpStatus, message: String) : RuntimeException(message)

  class Session(val authSessionId: String, val expires: Date?)

  @PostConstruct
  internal fun postConstruct() {
    val baseUri = dvelopConfiguration.baseUri
    val apiKey = dvelopConfiguration.apiKey
    webClient = WebClient.builder()
      .filters { exchangeFilterFunctions ->
        exchangeFilterFunctions.add(logRequest())
        exchangeFilterFunctions.add(logResponse())
      }
      .baseUrl(baseUri)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .defaultHeader("Authorization", "Bearer $apiKey")
      .defaultUriVariables(Collections.singletonMap("url", "baseUri"))
      .build()
  }

  /**
   * @throws HttpException
   */
  private fun <T> prepareHeadersSpec(headersSpec: RequestHeadersSpec<*>, expectedReturnClass: Class<T>): T {
    val mono = headersSpec
      .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .accept(MediaType.APPLICATION_JSON)
      .acceptCharset(StandardCharsets.UTF_8)
      .ifNoneMatch("*")
      .ifModifiedSince(ZonedDateTime.now())
      //.retrieve()
      //.bodyToMono(String::class.java)
      //.block()
      .exchangeToMono { response ->
        if (response.statusCode().equals(HttpStatus.OK)) {
          println("Success")
          return@exchangeToMono response.bodyToMono(expectedReturnClass) as Mono<T>
        } else {
          log.error { "Error while calling $headersSpec." }
          if (debugConsoleOutForTesting) {
            println("Error: ${response.statusCode()}")
          }
          throw HttpException(response.statusCode(), "Error response: ${response.statusCode()}")
        }
      }
      .block()
    return mono
  }

  fun login(): Session? {
    val uriSpec = webClient.get()
    val headersSpec = uriSpec.uri("/identityprovider/login")
    val map = prepareHeadersSpec(headersSpec, Map::class.java)
    if (debugConsoleOutForTesting) {
      println("login: ${map.entries.joinToString { "key=[${it.key} value=[${it.value}]" }}")
    }
    val authSessionId = map["AuthSessionId"] as String?
    val expiresString = map["Expire"] as String?
    var expires: Date? = null
    if (!expiresString.isNullOrBlank()) {
      val parsed = ZonedDateTime.parse(expiresString, DateTimeFormatter.ISO_DATE_TIME)
      if (parsed != null) {
        expires = Date.from(parsed.toInstant())
      }
    }
    return if (authSessionId == null) {
      null
    } else {
      Session(authSessionId, expires)
    }
  }

  fun getTradingPartnerList() {
    val uriSpec = webClient.get()
    val headersSpec = uriSpec.uri("/alphaflow-tradingpartner/tradingpartnerservice/tradingpartners")
    val response = prepareHeadersSpec(headersSpec, String::class.java)
    if (debugConsoleOutForTesting) {
      println("response: $response")
    }
  }

  fun createTradingPartner(tradingPartner: TradingPartner) {
    val uriSpec = webClient.post()
    if(debugConsoleOutForTesting){
      println("createTradingPartner body: ${JsonUtils.toJson(tradingPartner)}")
    }
    val bodySpec = uriSpec.uri("/alphaflow-tradingpartner/tradingpartnerservice/tradingpartners")
      .bodyValue(tradingPartner)
    val response = prepareHeadersSpec(bodySpec, String::class.java)
    if (debugConsoleOutForTesting) {
      println("response: $response")
    }
  }

  private fun logRequest(): ExchangeFilterFunction {
    return ExchangeFilterFunction.ofRequestProcessor { clientRequest ->
      if (log.isDebugEnabled || debugConsoleOutForTesting) {
        val sb = StringBuilder("Request: \n")
        // append clientRequest method and url
        clientRequest
          .headers()
          .forEach { name: String?, values: List<String?> ->
            sb.append("header '$name': ${values.joinToString()}")
          }
        log.debug { sb.toString() }
        if (debugConsoleOutForTesting) {
          println(sb.toString())
        }
      }
      return@ofRequestProcessor Mono.just(clientRequest)
    }
  }

  private fun logResponse(): ExchangeFilterFunction {
    return ExchangeFilterFunction.ofResponseProcessor { clientRequest ->
      if (log.isDebugEnabled || debugConsoleOutForTesting) {
        val sb = StringBuilder("Response: \n")
        // append clientRequest method and url
        log.debug { sb.toString() }
        if (debugConsoleOutForTesting) {
          println(sb.toString())
        }
      }
      return@ofResponseProcessor Mono.just(clientRequest)
    }
  }
}
