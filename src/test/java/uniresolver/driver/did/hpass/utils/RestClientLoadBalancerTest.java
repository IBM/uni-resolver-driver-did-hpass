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

package uniresolver.driver.did.hpass.utils;

import com.netflix.loadbalancer.Server;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import uniresolver.ResolutionException;
import uniresolver.driver.did.hpass.model.ServerEnvironment;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.ResourceBundle;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uniresolver.driver.did.hpass.constants.RegistryKeys.REGISTRY_METHOD_GET;

public class RestClientLoadBalancerTest {
    public static ResourceBundle messageBundle = ResourceBundle.getBundle("Messages");
    public static MessageUtils messageUtils = new MessageUtils(messageBundle);
    public static HttpClient httpClient = HttpClient.newHttpClient();
    public static MockWebServer mockServer;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @Test
    public void happyMakeRequest() throws Exception {
        Boolean staticServerList = true;
        ArrayList<Server> networkServerList = new ArrayList<>();

        Integer statusCode = 200;
        String body = "someBody";
        mockServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body)
                .setResponseCode(statusCode));

        String urlServer1 = String.format("http://%s:%s/", mockServer.getHostName(), mockServer.getPort());
        String urlServer2 = "https://incorrectServer:200";

        networkServerList.add(new Server(urlServer1, 1));
        networkServerList.add(new Server(urlServer2, 2));

        ServerEnvironment serverEnvironment = new ServerEnvironment(networkServerList, REGISTRY_METHOD_GET, staticServerList);
        RestClientLoadBalancer loadBalancer = new RestClientLoadBalancer(this.httpClient, this.messageUtils, serverEnvironment.getUrlList());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

        String networkID = "identifier1";
        String parameters = "?network_id=" + networkID;

        HttpResponse<String> httpResponse = loadBalancer.makeRequestWithRetry(requestBuilder, parameters);

        Integer actual = httpResponse.statusCode();
        Integer expected = statusCode;

        assertTrue(actual.equals(expected));
        assertTrue(httpResponse.body().equals(body));
    }

    @Test
    public void happyMakeRequestAfterRetries() throws Exception {
        Boolean staticServerList = true;
        ArrayList<Server> networkServerList = new ArrayList<>();

        Integer statusCode = 400;
        String body = "badBody";
        for (int i = 0; i < 5; i++) {
            mockServer.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(body + String.valueOf(i))
                    .setResponseCode(statusCode));
        }

        statusCode = 200;
        body = "goodBody";

        for (int i = 0; i < 5; i++) {
            mockServer.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(body + String.valueOf(i))
                    .setResponseCode(statusCode));
        }

        String urlServer1 = String.format("http://%s:%s/", mockServer.getHostName(), mockServer.getPort());
        String urlServer2 = "https://incorrectServer:200";

        networkServerList.add(new Server(urlServer1, 1));
        networkServerList.add(new Server(urlServer2, 2));

        ServerEnvironment serverEnvironment = new ServerEnvironment(networkServerList, REGISTRY_METHOD_GET, staticServerList);
        RestClientLoadBalancer loadBalancer = new RestClientLoadBalancer(this.httpClient, this.messageUtils, serverEnvironment.getUrlList());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

        String networkID = "identifier1";
        String parameters = "?network_id=" + networkID;

        HttpResponse<String> httpResponse = loadBalancer.makeRequestWithRetry(requestBuilder, parameters);

        Integer actual = httpResponse.statusCode();
        Integer expected = statusCode;

        assertTrue(actual.equals(expected));
        assertTrue(httpResponse.body().equals("goodBody0"));

        System.out.println(loadBalancer.getLoadBalancerStats().getServerStats());
    }

    @Test
    public void failedMakeRequestNoValidServer() {
        Boolean staticServerList = true;
        ArrayList<Server> networkServerList = new ArrayList<>();

        String urlServer1 = "https://incorrectServer1:100";
        String urlServer2 = "https://incorrectServer2:200";

        networkServerList.add(new Server(urlServer1, 1));
        networkServerList.add(new Server(urlServer2, 2));

        ServerEnvironment serverEnvironment = new ServerEnvironment(networkServerList, REGISTRY_METHOD_GET, staticServerList);
        RestClientLoadBalancer loadBalancer = new RestClientLoadBalancer(this.httpClient, this.messageUtils, serverEnvironment.getUrlList());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

        String networkID = "identifier1";
        String parameters = "?network_id=" + networkID;

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            HttpResponse<String> httpResponse = loadBalancer.makeRequestWithRetry(requestBuilder, parameters);
        });
        assertTrue(exception.getMessage().contains("Number of retries on next server exceeded max 10 retries"));
    }

    @Test
    public void failedMakeRequestInvalidServerPlusValidServerWithInvalidResponse() {
        Boolean staticServerList = true;
        ArrayList<Server> networkServerList = new ArrayList<>();

        Integer statusCode = 400;
        String body = "someBody";

        // put at least as many requests into the queue as the retry limit
        for (int i = 0; i < 10; i++) {
            mockServer.enqueue(new MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(body + String.valueOf(i))
                    .setResponseCode(statusCode));
        }

        String urlServer1 = String.format("http://%s:%s/", mockServer.getHostName(), mockServer.getPort());
        String urlServer2 = "https://incorrectServer:200";

        networkServerList.add(new Server(urlServer1, 1));
        networkServerList.add(new Server(urlServer2, 2));

        ServerEnvironment serverEnvironment = new ServerEnvironment(networkServerList, REGISTRY_METHOD_GET, staticServerList);
        RestClientLoadBalancer loadBalancer = new RestClientLoadBalancer(this.httpClient, this.messageUtils, serverEnvironment.getUrlList());
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

        String networkID = "identifier1";
        String parameters = "?network_id=" + networkID;

        ResolutionException exception = assertThrows(ResolutionException.class, () -> {
            HttpResponse<String> httpResponse = loadBalancer.makeRequestWithRetry(requestBuilder, parameters);
        });
        assertTrue(exception.getMessage().contains("Number of retries on next server exceeded max 10 retries"));

    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }
}
