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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.*;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_CREATED;
import static uniresolver.driver.did.hpass.constants.ResolverKeys.DID_UPDATED;

public class HpassDriverWithoutRegistryTest extends BaseIntegrationTest {
  private DidHpassDriver didHpassDriver;
  private Map<String, Object> properties;

  @BeforeEach
  void init() throws IOException {
    String baseHpassUrl = String.format("http://%s:%s/dids/$1", mockHpassServer.getHostName(), mockHpassServer.getPort());

    this.properties = new HashMap<>();

    this.properties.put(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED, "false");
    this.properties.put(UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL, baseHpassUrl);
    this.properties.put(UNIRESOLVER_DRIVER_AUTH_ENABLED, "false");

    didHpassDriver = new DidHpassDriver(properties);
  }

  @Test
  void happyResolveDID() throws ResolutionException, ParserException {
    Integer statusCodeRegistry = 500;
    mockRegistryServer.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .setResponseCode(statusCodeRegistry));

    Integer statusCodeHpass = 200;
    String bodyHpass = VALID_HEALTH_AUTHORITY;
    mockHpassServer.enqueue(new MockResponse()
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(bodyHpass)
        .setResponseCode(statusCodeHpass));

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

}
