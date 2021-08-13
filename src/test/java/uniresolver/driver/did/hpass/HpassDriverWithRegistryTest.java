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

public class HpassDriverWithRegistryTest extends BaseIntegrationTest {
    private DidHpassDriver didHpassDriver;
    private Map<String, Object> properties;

    private String baseRegistryUrl;

    @BeforeEach
    void init() {
        this.baseRegistryUrl = String.format("http://%s:%s/registries/", mockRegistryServer.getHostName(), mockRegistryServer.getPort());
        String networkUrl = baseRegistryUrl + "$1";

        this.properties = new HashMap<>();

        this.properties.put(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED, "true");
        this.properties.put(UNIRESOLVER_DRIVER_DID_REGISTRY_URL, networkUrl);
        this.properties.put(UNIRESOLVER_DRIVER_AUTH_ENABLED, "false");

        this.didHpassDriver = new DidHpassDriver(properties);
    }

    @Test
    void GoodPathResolve() throws ResolutionException, ParserException {
        Integer statusCodeRegistry = 200;
        String bodyRegistry = validEnvironmentCollection;
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(bodyRegistry)
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
        String updated = datesJson.get("payload").get("created").textValue();
        Map<String, Object> methodMetadata = new LinkedHashMap<>();
        methodMetadata.put(DID_CREATED, created);
        methodMetadata.put(DID_UPDATED, updated);

        DIDDocument did = new DIDDocument().fromJson(VALID_DID);
        ResolveResult expected = ResolveResult.build(null, did, null, methodMetadata);

        ResolveResult result = didHpassDriver.resolve(DID.fromString(id), null);

        assertEquals(expected.toJson(), result.toJson());
    }

    @Test
    void FailedCheckIfIdentifierIsWellFormed() {
        String id = "invalid_test_id";
        String expected = this.messageUtils.formatMessage("IDENTIFIER_IS_INVALID", id);

        ParserException exception = assertThrows(ParserException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertEquals(expected, exception.getMessage());
    }

    @Test
    void FailedRetrieveNetworkServers() {
        Integer statusCode = 500;
        for (Integer i = 0; i < 11; i++){
            mockRegistryServer.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setResponseCode(statusCode));
    }

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String networkID = id.substring(0, id.lastIndexOf(":"));

        String expected = this.messageUtils.formatMessage("COULD_NOT_RETRIEVE_SERVERS_FROM_REGISTRY_FOR_IDENTIFIER",id, "");

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });
        System.out.println(mockRegistryServer.getDispatcher());

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedRetrieveBodyAsJsonObject() {
        Integer statusCode = 200;
        String nonJsonBody = "nonJsonBody";
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(nonJsonBody)
                .setResponseCode(statusCode));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected = this.messageUtils.formatMessage("COULD_NOT_EXTRACT_JSON_OBJECT_FROM_HTTP_RESPONSE", "");
        expected = expected.substring(0, expected.lastIndexOf(":"));

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedRetrieveValidJsonResponseEmpty() {
        Integer statusCode = 200;
        String emptyResponse = "";
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(emptyResponse)
                .setResponseCode(statusCode));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected  = this.messageUtils.formatMessage("COULD_NOT_RETRIEVE_VALID_JSON_RESPONSE_FROM_BLOCKCHAIN_NETWORK", id);

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedRetrieveValidJsonResponseNoPayload() {
        Integer statusCode = 200;
        String body = invalidEnvironmentCollection;
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body)
                .setResponseCode(statusCode));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected  = this.messageUtils.formatMessage("COULD_NOT_RETRIEVE_VALID_JSON_RESPONSE_FROM_BLOCKCHAIN_NETWORK", id);

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedRetrieveURLFromValidJsonResponse() {
        Integer statusCode = 200;
        String body = validEnvironmentCollectionType2;
            mockRegistryServer.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(body)
                    .setResponseCode(statusCode));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected = this.messageUtils.formatMessage("COULD_NOT_RESOLVE_DID_NETWORK_URL_FROM_REGISTRY_RESPONSE", body);

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }


    // Now prepare DID resolution
    @Test
    void FailedFetchDidFromBlockchainNetworkMethodSet() {
        Integer statusCodeRegistry = 200;
        String body = validEnvironmentCollectionMethodSet;
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body)
                .setResponseCode(statusCodeRegistry));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected = this.messageUtils.formatMessage("NO_VALID_HTTP_METHOD_FOUND_IN_REGISTRY_FOR_URL","");
        expected = expected.substring(0, expected.lastIndexOf(":"));

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedFetchDidFromNetwork() {

        Integer statusCodeRegistry = 200;
        String body = validEnvironmentCollection;
        for (Integer i = 0; i < 11; i++) {
            mockRegistryServer.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(body)
                    .setResponseCode(statusCodeRegistry));
        }

        Integer statusCodeHpass = 500;
        for (Integer i = 0; i < 11; i++) {
            mockHpassServer.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setResponseCode(statusCodeHpass));
        }
        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected = this.messageUtils.formatMessage("COULD_NOT_FETCH_DID_FROM_NETWORK_FOR_IDENTIFIER", "",statusCodeHpass);
        expected = expected.substring(0, expected.indexOf(":"));

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedGetDidPayload() {
        Integer statusCodeRegistry = 200;
        String body = validEnvironmentCollection;
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body)
                .setResponseCode(statusCodeRegistry));

        Integer statusCodeHpass = 200;
        mockHpassServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setResponseCode(statusCodeHpass));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected = this.messageUtils.formatMessage("DID_PAYLOAD_NOT_FOUND");

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedGetVerificationMethods() {
        Integer statusCodeRegistry = 200;
        String bodyRegistry = validEnvironmentCollection;
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(bodyRegistry)
                .setResponseCode(statusCodeRegistry));

        Integer statusCodeHpass = 200;
        String bodyHpass = INVALID_HEALTH_AUTHORITY_WRONG_KEY;
        mockHpassServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(bodyHpass)
                .setResponseCode(statusCodeHpass));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected = this.messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL");
        expected = expected.substring(0, expected.lastIndexOf(":"));

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }

    @Test
    void FailedGetVerificationMethodsMandatoryKeyNotFound() {
        Integer statusCodeRegistry = 200;
        String bodyRegistry = validEnvironmentCollection;
        mockRegistryServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(bodyRegistry)
                .setResponseCode(statusCodeRegistry));

        Integer statusCodeHpass = 200;
        String bodyHpass = INVALID_HEALTH_AUTHORITY_MISSING_MANDATORY_KEY;
        mockHpassServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(bodyHpass)
                .setResponseCode(statusCodeHpass));

        String id = "did:hpass:bbbb172ac3622332beacdd2089ddd20a82ae90c32b4b81515237245c9683e6dd:f176010ec01d4eaf04ac49c54b568358e4a17a0d4e5a0f8eb7cfe17797d6bbbb";
        String expected = this.messageUtils.formatMessage("MANDATORY_KEY_NOT_FOUND", "");
        expected = expected.substring(0, expected.lastIndexOf(":"));

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            didHpassDriver.resolve(DID.fromString(id), null);
        });

        assertTrue(exception.getMessage().contains(expected));
    }
}
