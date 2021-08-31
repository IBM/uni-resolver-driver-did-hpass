/**
 * Copyright 2021 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package uniresolver.driver.did.hpass.service;

import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_AUTH_ENABLED;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_AUTH_LOGIN_URL;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_PASSWORD;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_USER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uniresolver.ResolutionException;
import uniresolver.driver.did.hpass.utils.JSONUtils;
import uniresolver.driver.did.hpass.utils.MessageUtils;
import uniresolver.driver.did.hpass.utils.PropertyUtils;

public class AppIdClient {

  public static final String ACCESS_TOKEN = "access_token";
  public static final Integer TIME_BUFFER_MINUTES = 5;
  private static final Logger log = LoggerFactory.getLogger(AppIdClient.class);
  private final PropertyUtils propertyUtils;
  private final JSONUtils jsonUtils;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Map<String, Object> properties;
  private final MessageUtils messageUtils;
  private String authJWT = null;

  public AppIdClient(Map<String, Object> properties,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      JSONUtils jsonUtils,
      PropertyUtils propertyUtils,
      MessageUtils messageUtils) {
    this.properties = properties;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.jsonUtils = jsonUtils;
    this.propertyUtils = propertyUtils;
    this.messageUtils = messageUtils;
  }

  public String authenticate() throws ResolutionException {
    HttpRequest request;

    // check cached JWT token
    if (authJWT != null) {
      String tokenOnly = authJWT.substring(0, authJWT.lastIndexOf('.') + 1);
      try {
        Date expirationTime = ((Claims) Jwts.parserBuilder().build().parse(tokenOnly).getBody()).getExpiration();
        LocalDateTime currentDateTimePlusBuffer = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusMinutes(TIME_BUFFER_MINUTES);
        LocalDateTime expirationDateTime = new java.sql.Timestamp(expirationTime.getTime()).toLocalDateTime();
        if (expirationDateTime.compareTo(currentDateTimePlusBuffer) > 0) {
          return authJWT;
        }
      } catch (ExpiredJwtException | MalformedJwtException | SignatureException e) {
        String message = this.messageUtils.formatMessage("EXPIRED_OR_INVALID_JWT_TOKEN", e.getMessage());
        log.info(message);
      }
    }
    // request new JWT token
    Map<String, String> requestBodyMap = Map.of(
        "email", this.propertyUtils.getPropertyByKey(this.properties, UNIRESOLVER_DRIVER_USER),
        "password", this.propertyUtils.getPropertyByKey(this.properties, UNIRESOLVER_DRIVER_PASSWORD));

    String requestBody = null;
    try {
      requestBody = this.objectMapper.writeValueAsString(requestBodyMap);
    } catch (JsonProcessingException e) {
      String message = this.messageUtils.formatMessage("COULD_NOT_WRITE_JSON_TO_STRING", e.getMessage());
      log.error(message);
      throw new ResolutionException(message, e);
    }

    request = HttpRequest.newBuilder()
        .uri(URI.create(this.propertyUtils.getPropertyByKey(this.properties, UNIRESOLVER_DRIVER_AUTH_LOGIN_URL)))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    JsonNode response;
    try {
      HttpResponse<String> httpResponse = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (httpResponse.statusCode() / 100 != 2) {
        String message = this.messageUtils
            .formatMessage("COULD_NOT_RETRIEVE_VALID_HTTP_RESPONSE", httpResponse.uri(), httpResponse.statusCode());
        log.error(message);
        throw new ResolutionException(message);
      }

      response = this.jsonUtils.retrieveBodyAsJsonObject(httpResponse);
    } catch (InterruptedException | IOException e) {
      String message = this.messageUtils.formatMessage("COULD_NOT_GET_RESPONSE", e.getMessage());
      log.error(message);
      throw new ResolutionException(message, e);
    }
    if (response == null || response.size() == 0 || !response.hasNonNull(ACCESS_TOKEN)) {
      String message = this.messageUtils.formatMessage("COULD_NOT_GET_RESPONSE", "invalid login data");
      log.error(message);
      throw new ResolutionException(message);
    }
    authJWT = response.get(ACCESS_TOKEN).asText();
    return authJWT;
  }

  public HttpRequest.Builder setAuthenticationHeader(HttpRequest.Builder builder) throws ResolutionException {
    if (this.propertyUtils.getPropertyByKey(this.properties, UNIRESOLVER_DRIVER_AUTH_ENABLED).equals("false")) {
      return builder;
    }

    return builder.setHeader("Authorization", "Bearer " + this.authenticate());
  }
}
