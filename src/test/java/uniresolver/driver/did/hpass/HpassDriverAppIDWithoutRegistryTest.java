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

import com.fasterxml.jackson.databind.JsonNode;
import foundation.identity.did.DID;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.parser.ParserException;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uniresolver.ResolutionException;
import uniresolver.result.ResolveResult;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.*;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_CREATED;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_UPDATED;

public class HpassDriverAppIDWithoutRegistryTest extends BaseIntegrationTest {
  private DidHpassDriver didHpassDriver;
  private Map<String, Object> properties;
  private String bodyHpass;
  private String bodyAppId;

  @BeforeEach
  void init() {
    String baseHpassUrl = String.format("http://%s:%s/", mockHpassServer.getHostName(), mockHpassServer.getPort());
    String baseAppIdUrl = String.format("http://%s:%s/", mockAppIdServer.getHostName(), mockAppIdServer.getPort());

    this.properties = new HashMap<>();

    this.properties.put(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED, "false");
    this.properties.put(UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL, baseHpassUrl);
    this.properties.put(UNIRESOLVER_DRIVER_AUTH_LOGIN_URL, baseAppIdUrl);
    this.properties.put(UNIRESOLVER_DRIVER_AUTH_ENABLED, "true");
    this.properties.put(UNIRESOLVER_DRIVER_USER, "testmail.mock");
    this.properties.put(UNIRESOLVER_DRIVER_PASSWORD, "password.mock");

    didHpassDriver = new DidHpassDriver(properties);
  }

  @Test
  void happyResolveDID() throws ResolutionException, ParserException {
    Integer statusCodeRegistry = 500;
    mockRegistryServer.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .setResponseCode(statusCodeRegistry));

    Integer statusCodeHpass = 200;
    bodyHpass = VALID_HEALTH_AUTHORITY;
    mockHpassServer.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(bodyHpass)
        .setResponseCode(statusCodeHpass));

    Integer statusCodeAppId = 200;
    bodyAppId = VALID_APPID_RESPONSE;
    mockAppIdServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(bodyAppId)
            .setResponseCode(statusCodeAppId));

    String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";

    JsonNode datesJson = createJsonNode(VALID_HEALTH_AUTHORITY);
    String created = datesJson.get("payload").get("created").textValue();
    String updated = datesJson.get("payload").get("updated").textValue();
    Map<String, Object> methodMetadata = new LinkedHashMap<>();
    methodMetadata.put(DID_CREATED, created);
    methodMetadata.put(DID_UPDATED, updated);

    DIDDocument did = new DIDDocument().fromJson(VALID_DID);
    ResolveResult expected = ResolveResult.build(null, did, null, methodMetadata);

    ResolveResult result = didHpassDriver.resolve(DID.fromString(id), null);

    assertEquals(expected.toJson(), result.toJson());
  }

  @Test
  void FailedAuthenticateGetPassword() {
    String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
    this.properties.put(UNIRESOLVER_DRIVER_PASSWORD, null);

    String expected = messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL", UNIRESOLVER_DRIVER_PASSWORD);

    ResolutionException exception = assertThrows(ResolutionException.class, () -> {
      didHpassDriver.resolve(DID.fromString(id), null);
    });
    assertTrue(exception.getMessage().contains(expected));
  }

  @Test
  void FailedAuthenticateGetUsername() {
    String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
    this.properties.put(UNIRESOLVER_DRIVER_USER, null);

    String expected = messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL", UNIRESOLVER_DRIVER_USER);

    ResolutionException exception = assertThrows(ResolutionException.class, () -> {
      didHpassDriver.resolve(DID.fromString(id), null);
    });
    assertTrue(exception.getMessage().contains(expected));
  }

  @Test
  void FailedAuthenticateBadHttpStatusCode() {
    Integer statusCodeAppId = 500;
    mockAppIdServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setResponseCode(statusCodeAppId));

    String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";

    String url = (String) this.properties.get(UNIRESOLVER_DRIVER_AUTH_LOGIN_URL);
    String expected = messageUtils.formatMessage("COULD_NOT_RETRIEVE_VALID_HTTP_RESPONSE", url, statusCodeAppId);

    ResolutionException exception = assertThrows(ResolutionException.class, () -> {
      didHpassDriver.resolve(DID.fromString(id), null);
    });
    assertTrue(exception.getMessage().contains(expected));
  }

  @Test
  void FailedAuthenticateBadHttpResponse() {
    Integer statusCodeAppId = 200;
    mockAppIdServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setResponseCode(statusCodeAppId));

    String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";

    String expected = messageUtils.formatMessage("COULD_NOT_GET_RESPONSE", "invalid login data");

    ResolutionException exception = assertThrows(ResolutionException.class, () -> {
      didHpassDriver.resolve(DID.fromString(id), null);
    });
    assertTrue(exception.getMessage().contains(expected));
  }

  @Test
  void FailedAuthenticateNoAccessToken() {
    Integer statusCodeAppId = 200;
    bodyAppId = INVALID_APPID_RESPONSE_NO_ACCESS_TOKEN;
    mockAppIdServer.enqueue(new MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setBody(bodyAppId)
            .setResponseCode(statusCodeAppId));

    String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";

    String expected = messageUtils.formatMessage("COULD_NOT_GET_RESPONSE", "invalid login data");

    ResolutionException exception = assertThrows(ResolutionException.class, () -> {
      didHpassDriver.resolve(DID.fromString(id), null);
    });
    assertTrue(exception.getMessage().contains(expected));
  }
}
