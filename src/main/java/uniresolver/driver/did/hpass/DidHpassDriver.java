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

package uniresolver.driver.did.hpass;

import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_DID_REGISTRY_URL;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_ENVIRONMENTS;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_METADATA;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_METHOD;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_METHOD_GET;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_PAYLOAD;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_TYPE;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_TYPE_REST;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_URLS;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_CONTROLLER;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_CREATED;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_ID;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_JSON_WEB_KEY_2020;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_PAYLOAD;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_PUBLIC_KEY;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_PUBLIC_KEY_JWK;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_P_256;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_TYPE;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_UPDATED;
import static uniresolver.result.ResolveResult.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.loadbalancer.Server;
import foundation.identity.did.DID;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.VerificationMethod;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uniresolver.ResolutionException;
import uniresolver.driver.Driver;
import uniresolver.driver.did.hpass.model.ServerEnvironment;
import uniresolver.driver.did.hpass.service.AppIdClient;
import uniresolver.driver.did.hpass.utils.JSONUtils;
import uniresolver.driver.did.hpass.utils.MessageUtils;
import uniresolver.driver.did.hpass.utils.PropertyUtils;
import uniresolver.driver.did.hpass.utils.RestClientLoadBalancer;
import uniresolver.result.ResolveResult;

public class DidHpassDriver implements Driver {

  private static final Pattern DID_HPASS_PATTERN = Pattern.compile("^did:hpass:([0-9A-Fa-f]{60,65}):([0-9A-Fa-fts]{60,65})$");
  private static final Pattern DID_DATE_TIME_PATTERN = Pattern
      .compile("^\\d{4}\\-(0[1-9]|1[012])\\-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$");
  private static final Logger log = LoggerFactory.getLogger(DidHpassDriver.class);
  private final PropertyUtils propertyUtils;
  private final JSONUtils jsonUtils;
  private final AppIdClient appIdClient;
  private final RestClientLoadBalancer loadBalancerForNetwork;
  private final RestClientLoadBalancer loadBalancerForRegistry;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Map<String, Object> properties;
  private final ResourceBundle messageBundle;
  private final MessageUtils messageUtils;

  public DidHpassDriver() {
    this(PropertyUtils.getPropertiesFromEnvironment());
  }

  public DidHpassDriver(Map<String, Object> properties) {
    this.messageBundle = ResourceBundle.getBundle("Messages");
    this.messageUtils = new MessageUtils(messageBundle);
    this.propertyUtils = new PropertyUtils(this.messageUtils);
    this.propertyUtils.validateProperties(properties);
    this.properties = properties;
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
    this.jsonUtils = new JSONUtils(this.objectMapper, this.messageUtils);
    this.appIdClient = new AppIdClient(this.properties, this.httpClient, this.objectMapper, this.jsonUtils, this.propertyUtils,
        this.messageUtils);
    this.loadBalancerForNetwork = initStaticLoadBalancerForNetwork(this.httpClient, this.messageUtils);
    this.loadBalancerForRegistry = initStaticLoadBalancerForRegistry(this.httpClient, this.messageUtils);
  }

  private RestClientLoadBalancer initStaticLoadBalancerForNetwork(HttpClient httpClient, MessageUtils messageUtils) {
    try {
      if (this.isNetworksRegistryEnabled()) {
        return null;
      }

      ArrayList<Server> networkServerList = new ArrayList<>();
      String[] urlList = this.propertyUtils.getPropertyByKey(this.properties, UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL).split(",");
      for (String url : urlList) {
        URL aURL = getURL(url);
        // workaround: provide fully specified URL as server and use this in load balancer, port needs to be provided but is not used
        networkServerList.add(new Server(url, aURL.getPort()));
      }

      String message = this.messageUtils.formatMessage("INITIALIZE_NETWORK_LOAD_BALANCER", networkServerList);
      log.info(message);

      return new RestClientLoadBalancer(httpClient, messageUtils, networkServerList);
    } catch (Exception e) {
      String message = this.messageUtils.formatMessage("ERROR_INITIALIZE_NETWORK_LOAD_BALANCER", e.getMessage());
      log.error(message);
      throw new RuntimeException(message, e);
    }
  }

  private RestClientLoadBalancer initStaticLoadBalancerForRegistry(HttpClient httpClient, MessageUtils messageUtils) {
    try {
      if (!this.isNetworksRegistryEnabled()) {
        return null;
      }

      ArrayList<Server> networkServerList = new ArrayList<>();
      String[] urlList = this.propertyUtils.getPropertyByKey(this.properties, UNIRESOLVER_DRIVER_DID_REGISTRY_URL).split(",");
      for (String url : urlList) {
        URL aURL = getURL(url);
        // workaround: provide fully specified URL as server and use this in load balancer, port needs to be provided but is not used
        networkServerList.add(new Server(url, aURL.getPort()));
      }

      String message = this.messageUtils.formatMessage("INITIALIZE_REGISTRY_LOAD_BALANCER", networkServerList);
      log.info(message);

      return new RestClientLoadBalancer(httpClient, messageUtils, networkServerList);
    } catch (Exception e) {
      String message = this.messageUtils.formatMessage("ERROR_INITIALIZE_REGISTRY_LOAD_BALANCER", e.getMessage());
      log.error(message);
      throw new RuntimeException(message, e);
    }
  }

  @Override
  public Map<String, Object> properties() throws ResolutionException {
    return this.properties;
  }

  @Override
  public ResolveResult resolve(DID did, Map<String, Object> resolutionOptions) throws ResolutionException {

    checkIfIdentifierIsWellFormed(did.getDidString());

    ServerEnvironment blockchainNetwork = retrieveNetworkServers(did.getDidString());

    JsonNode didBody = fetchDidFromBlockchainNetwork(blockchainNetwork, did.getDidString());

    JsonNode didPayload = getDidPayload(didBody);

    List<VerificationMethod> verificationMethods = getVerificationMethods(didPayload);

    DIDDocument didDocument = DIDDocument.builder()
        .id(URI.create(did.getDidString()))
        .verificationMethods(verificationMethods)
        .build();

    Map<String, Object> methodMetadata = getMethodMetadata(didPayload);

    ResolveResult resolveResult = build(null, didDocument, null, methodMetadata);

    return resolveResult;
  }

  private List<VerificationMethod> getVerificationMethods(JsonNode didDocument) throws ResolutionException {
    JsonNode publicKeys = didDocument.get(DID_PUBLIC_KEY);
    if (publicKeys == null) {
      String message = this.messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL", DID_PUBLIC_KEY);
      log.error(message);
      throw new ResolutionException(message);
    }
    List<VerificationMethod> verificationMethods = new ArrayList<>();

    for (JsonNode publicKey : publicKeys) {

      // mandatory field
      String key = DID_ID;
      JsonNode value = publicKey.get(key);
      if (value == null) {
        String message = this.messageUtils.formatMessage("MANDATORY_KEY_NOT_FOUND", key);
        log.error(message);
        throw new ResolutionException(message);
      }
      URI keyId = URI.create(value.textValue());

      // mandatory field
      key = DID_TYPE;
      value = publicKey.get(key);
      if (value == null) {
        String message = this.messageUtils.formatMessage("MANDATORY_KEY_NOT_FOUND", key);
        log.error(message);
        throw new ResolutionException(message);
      }
      // Workaround: fix on Healthpass side, then assign `String keyType = value.textValue();
      String type = value.textValue();
      String keyType;
      switch (type) {
        case DID_P_256:
          keyType = DID_JSON_WEB_KEY_2020;
          break;
        default:
          keyType = type;
      }

      // optional field
      key = DID_PUBLIC_KEY_JWK;
      Map<String, Object> keyMap = null;
      Object publicKeyJwk = publicKey.get(key);
      if (publicKeyJwk != null) {
        keyMap = this.objectMapper.convertValue(publicKeyJwk, Map.class);
      }

      VerificationMethod verificationMethod = VerificationMethod.builder()
          .id(keyId)
          .type(keyType)
          .publicKeyJwk(keyMap)
          .build();

      // mandatory "controller" field
      key = DID_CONTROLLER;
      Object controller = publicKey.get(key);
      if (controller == null) {
        String message = this.messageUtils.formatMessage("MANDATORY_KEY_NOT_FOUND", key);
        log.error(message);
        throw new ResolutionException(message);
      }
      verificationMethod.setJsonObjectKeyValue(key, controller);
      verificationMethods.add(verificationMethod);
    }
    return verificationMethods;
  }

  private JsonNode getDidPayload(JsonNode didBody) throws ResolutionException {
    String key = DID_PAYLOAD;
    JsonNode didPayload = didBody.get(key);
    if (didPayload == null) {
      String message = this.messageUtils.formatMessage("DID_PAYLOAD_NOT_FOUND");
      log.error(message);
      throw new ResolutionException(message);
    }
    return didPayload;
  }

  private ServerEnvironment retrieveNetworkServers(String identifier) throws ResolutionException {
    Boolean staticServerList;

    // without network registry always return static list of  network servers
    if (!this.isNetworksRegistryEnabled()) {
      staticServerList = Boolean.TRUE;
      ServerEnvironment serverEnvironment = new ServerEnvironment(this.loadBalancerForNetwork.getAllServers(), REGISTRY_METHOD_GET,
          staticServerList);

      return serverEnvironment;
    }

    // get networks servers from registry
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    JsonNode response;
    try {
      String networkID = identifier.substring(0, identifier.lastIndexOf(":"));
      HttpResponse<String> httpResponse = this.loadBalancerForRegistry.makeRequestWithRetry(requestBuilder, networkID);

      if (httpResponse.statusCode() / 100 != 2) {
        String message = this.messageUtils
            .formatMessage("COULD_NOT_RETRIEVE_VALID_HTTP_RESPONSE_FROM_BLOCKCHAIN_NETWORK", httpResponse.uri(), httpResponse.statusCode());
        log.error(message);
        throw new ResolutionException(message);
      }
      response = this.jsonUtils.retrieveBodyAsJsonObject(httpResponse);
    } catch (Exception e) {
      String message = this.messageUtils
          .formatMessage("COULD_NOT_RETRIEVE_SERVERS_FROM_REGISTRY_FOR_IDENTIFIER", identifier, e.getMessage());
      log.error(message);
      throw new ResolutionException(message, e);
    }

    if (response == null || response.size() == 0 || !response.hasNonNull(REGISTRY_PAYLOAD) || !response.get(REGISTRY_PAYLOAD)
        .hasNonNull(REGISTRY_ENVIRONMENTS)) {
      String message = this.messageUtils.formatMessage("COULD_NOT_RETRIEVE_VALID_JSON_RESPONSE_FROM_BLOCKCHAIN_NETWORK", identifier);
      log.error(message);
      throw new ResolutionException(message);
    }

    JsonNode environments = response.get(REGISTRY_PAYLOAD).get(REGISTRY_ENVIRONMENTS);
    ArrayList<Server> urlArray = new ArrayList<>();
    String method = null;

    for (JsonNode environment : environments) {
      String type = environment.get(REGISTRY_TYPE).textValue();
      switch (type) {
        case REGISTRY_TYPE_REST:
          JsonNode urls = environment
              .get(REGISTRY_METADATA)
              .get(REGISTRY_URLS);
          for (JsonNode url : urls) {
            URL aURL = getURL(url.textValue());
            urlArray.add(new Server(url.textValue(), aURL.getPort()));
          }
          method = environment
              .get(REGISTRY_METADATA)
              .get(REGISTRY_METHOD).textValue();
          break;
      }
    }

    if (method == null || urlArray.isEmpty()) {
      String message = this.messageUtils.formatMessage("COULD_NOT_RESOLVE_DID_NETWORK_URL_FROM_REGISTRY_RESPONSE", response);
      log.error(message);
      throw new ResolutionException(message);
    }

    staticServerList = Boolean.FALSE;

    return new ServerEnvironment(urlArray, method, staticServerList);
  }

  private URL getURL(String urlString) throws ResolutionException {
    URL url;
    try {
      url = new URL(urlString);
    } catch (MalformedURLException e) {
      String message = this.messageUtils.formatMessage("ILL_FORMED_URL", e.getMessage());
      log.error(message);
      throw new ResolutionException(message);
    }
    return url;
  }

  private JsonNode fetchDidFromBlockchainNetwork(ServerEnvironment serverEnvironment, String identifier) throws ResolutionException {
    JsonNode response;
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    RestClientLoadBalancer loadBalancer;

    if (serverEnvironment.isStatic()) {
      loadBalancer = this.loadBalancerForNetwork;
    } else {
      loadBalancer = new RestClientLoadBalancer(this.httpClient, this.messageUtils, serverEnvironment.getUrlList());
      String message = this.messageUtils.formatMessage("INITIALIZE_DYNAMIC_NETWORK_LOAD_BALANCER", loadBalancer.getAllServers());
      log.info(message);
    }

    switch (serverEnvironment.getMethod()) {
      case REGISTRY_METHOD_GET:
        requestBuilder.setHeader("Content-Type", "application/json");
        requestBuilder = appIdClient.setAuthenticationHeader(requestBuilder);
        break;
      default:
        String message = this.messageUtils.formatMessage("NO_VALID_HTTP_METHOD_FOUND_IN_REGISTRY_FOR_URL", serverEnvironment.getUrlList());
        log.error(message);
        throw new ResolutionException(message);
    }

    try {
      HttpResponse<String> httpResponse = loadBalancer.makeRequestWithRetry(requestBuilder, identifier);

      if (httpResponse.statusCode() / 100 != 2) {
        String message = this.messageUtils
            .formatMessage("COULD_NOT_RETRIEVE_VALID_HTTP_RESPONSE_FROM_DID_NETWORK", httpResponse.uri(), httpResponse.statusCode());
        log.error(message);
        throw new ResolutionException(message);
      }
      response = this.jsonUtils.retrieveBodyAsJsonObject(httpResponse);
    } catch (Exception e) {
      String message = this.messageUtils.formatMessage("COULD_NOT_FETCH_DID_FROM_NETWORK_FOR_IDENTIFIER", identifier, e.getMessage());
      log.error(message);
      throw new ResolutionException(message, e);
    }
    return response;
  }

  private void checkIfIdentifierIsWellFormed(String identifier) throws ResolutionException {
    if (!DID_HPASS_PATTERN.matcher(identifier).matches()) {
      String message = this.messageUtils.formatMessage("IDENTIFIER_IS_INVALID", identifier);
      log.error(message);
      throw new ResolutionException(message);
    }
  }

  private boolean isValidFormat(String value) {
    return DID_DATE_TIME_PATTERN.matcher(value).matches();
  }

  private Map<String, Object> getMethodMetadata(JsonNode didPayload) {
    Map<String, Object> methodMetadata = new LinkedHashMap<String, Object>();

    // created/updated dateTime be a string formatted as an XML Datetime normalized to UTC 00:00:00 and without sub-second decimal precision. For example: 2020-12-20T19:17:47Z.
    String key = DID_CREATED;
    String created = null;
    JsonNode value = didPayload.get(key);
    if (value != null) {
      created = value.textValue();
    }

    if (isValidFormat(created)) {
      methodMetadata.put(key, created);
    } else {
      String message = this.messageUtils.formatMessage("DATE_IS_INVALID_OR_INCORRECTLY_FORMATTED_NOT_ADDED_TO_METADATA", key, created);
      log.warn(message);
    }

    key = DID_UPDATED;
    String updated = null;
    value = didPayload.get(key);
    if (value != null) {
      updated = value.textValue();
    }

    if (isValidFormat(updated)) {
      methodMetadata.put(key, updated);
    } else {
      String message = this.messageUtils.formatMessage("DATE_IS_INVALID_OR_INCORRECTLY_FORMATTED_NOT_ADDED_TO_METADATA", key, updated);
      log.warn(message);
    }
    return methodMetadata;
  }

  private boolean isNetworksRegistryEnabled() throws ResolutionException {
    return this.propertyUtils.getPropertyByKey(this.properties, UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED).equals("true");
  }
}
