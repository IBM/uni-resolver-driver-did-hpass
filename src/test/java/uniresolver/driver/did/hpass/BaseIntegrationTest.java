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

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ResourceBundle;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import uniresolver.driver.did.hpass.utils.MessageUtils;

public abstract class BaseIntegrationTest {

  public static MockWebServer mockRegistryServer;
  public static MockWebServer mockHpassServer;
  public static MockWebServer mockAppIdServer;
  public static ResourceBundle messageBundle;
  public static MessageUtils messageUtils;
  protected static ObjectMapper objectMapper;
  protected static String VALID_ENVIRONMENT_COLLECTION;
  protected static String VALID_ENVIRONMENT_COLLECTION_TYPE2;
  protected static String VALID_ENVIRONMENT_COLLECTION_METHOD_SET;
  protected static String INVALID_ENVIRONMENT_COLLECTION;

  protected static String validEnvironmentCollection = null;
  protected static String validEnvironmentCollectionType2 = null;
  protected static String validEnvironmentCollectionMethodSet = null;
  protected static String invalidEnvironmentCollection = null;

  protected static String VALID_HEALTH_AUTHORITY;
  protected static String INVALID_HEALTH_AUTHORITY_WRONG_KEY;
  protected static String INVALID_HEALTH_AUTHORITY_MISSING_MANDATORY_KEY;
  protected static String VALID_DID;
  protected static String VALID_APPID_RESPONSE;
  protected static String INVALID_APPID_RESPONSE_NO_ACCESS_TOKEN;

  @BeforeAll
  static void setUp() throws IOException {

    messageBundle = ResourceBundle.getBundle("Messages");
    messageUtils = new MessageUtils(messageBundle);

    VALID_ENVIRONMENT_COLLECTION = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/valid_environment_collection.json"))
        .readAllBytes());

    VALID_ENVIRONMENT_COLLECTION_TYPE2 = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/valid_environment_collection_type2.json"))
        .readAllBytes());

    VALID_ENVIRONMENT_COLLECTION_METHOD_SET = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/valid_environment_collection_method_set.json"))
        .readAllBytes());

    INVALID_ENVIRONMENT_COLLECTION = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/invalid_environment_collection.json"))
        .readAllBytes());

    INVALID_HEALTH_AUTHORITY_WRONG_KEY = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/invalid_health_authority_wrong_key.json"))
        .readAllBytes());

    INVALID_HEALTH_AUTHORITY_MISSING_MANDATORY_KEY = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/invalid_health_authority_missing_mandatory_key.json"))
        .readAllBytes());

    VALID_HEALTH_AUTHORITY = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/valid_health_authority.json"))
        .readAllBytes());

    VALID_DID = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/valid_did_document.json"))
        .readAllBytes());

    VALID_APPID_RESPONSE = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/valid_appid_response.json"))
        .readAllBytes());

    INVALID_APPID_RESPONSE_NO_ACCESS_TOKEN = new String(requireNonNull(HpassDriverWithRegistryTest.class
        .getClassLoader()
        .getResourceAsStream("stubs/invalid_appid_response_no_accesstoken.json"))
        .readAllBytes());
  }

  protected static JsonNode createEnvironmentCollectionJson(String environment, String baseHpassUrl) {
    JsonNode environmentCollectionJson = createJsonNode(environment);

    if (environmentCollectionJson.hasNonNull("payload")) {
      environmentCollectionJson.get("payload").get("environments")
          .forEach(obj -> {
            ((ArrayNode) obj.get("metadata").get("urls")).removeAll().add(baseHpassUrl + "/api/v1/health-authorities/" + "$1");
          });
    }

    return environmentCollectionJson;
  }

  protected static JsonNode createJsonNode(String jsonString) {
    JsonNode jsonNode = null;
    try {
      jsonNode = objectMapper.readTree(jsonString);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return jsonNode;
  }

  @BeforeEach
  void setUpServers() throws IOException {
    mockRegistryServer = new MockWebServer();
    mockRegistryServer.start();
    mockHpassServer = new MockWebServer();
    mockHpassServer.start();
    mockAppIdServer = new MockWebServer();
    mockAppIdServer.start();
    objectMapper = new ObjectMapper();

    String baseHpassUrl = String.format("http://%s:%s", mockHpassServer.getHostName(), mockHpassServer.getPort());

    validEnvironmentCollection = createEnvironmentCollectionJson(VALID_ENVIRONMENT_COLLECTION, baseHpassUrl).toString();
    validEnvironmentCollectionType2 = createEnvironmentCollectionJson(VALID_ENVIRONMENT_COLLECTION_TYPE2, baseHpassUrl).toString();
    validEnvironmentCollectionMethodSet = createEnvironmentCollectionJson(VALID_ENVIRONMENT_COLLECTION_METHOD_SET, baseHpassUrl).toString();
    invalidEnvironmentCollection = createEnvironmentCollectionJson(INVALID_ENVIRONMENT_COLLECTION, baseHpassUrl).toString();
  }

  @AfterEach
  void tearDown() throws Exception {
    mockRegistryServer.shutdown();
    mockHpassServer.shutdown();
    mockAppIdServer.shutdown();
  }
}
