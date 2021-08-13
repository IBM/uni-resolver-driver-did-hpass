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

import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import uniresolver.ResolutionException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static uniresolver.driver.did.hpass.constants.RegistryKeys.URL_PARAMETER_1;

public class RestClientLoadBalancer {
    private final BaseLoadBalancer loadBalancer;
    private final RetryHandler retryHandler;
    private List<Server> serverList;
    private HttpClient httpClient;
    private MessageUtils messageUtils;

    private static Logger log = LoggerFactory.getLogger(RestClientLoadBalancer.class);

    public RestClientLoadBalancer(HttpClient httpClient, MessageUtils messageUtils, List<Server> urlList) {
        this.serverList = urlList;
        this.loadBalancer = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(this.serverList);
        // retry handler does not retry on same server, but on a different server
        this.retryHandler = new DefaultLoadBalancerRetryHandler(0, 10, true);
        this.httpClient = httpClient;
        this.messageUtils = messageUtils;
    }

    public HttpResponse<String> makeRequestWithRetry(HttpRequest.Builder builder, final String parameters) throws Exception {
        HttpResponse<String> httpResponse = null;

        try {
            httpResponse = makeRequest(this.httpClient, builder, parameters, this.messageUtils);
        } catch (Exception e) {
            String message = this.messageUtils.formatMessage("SERVER_REQUEST_UNSUCCESSFUL", e.getMessage());
            log.error(message);
            throw new ResolutionException(message, e);
        }

        return httpResponse;
    }

    private HttpResponse makeRequest(HttpClient httpClient, HttpRequest.Builder builder, final String parameter, MessageUtils messageUtils) throws Exception {
        return LoadBalancerCommand.<HttpResponse>builder()
                .withLoadBalancer(loadBalancer)
                .withRetryHandler(retryHandler)
                .build()
                .submit(new ServerOperation<HttpResponse>() {
                    @Override
                    public Observable<HttpResponse> call(Server server) {
                        // Workaround: server.getHost() provides full URL except parameters
                        String urlString = server.getHost().replace(URL_PARAMETER_1, parameter);
                        builder.uri(URI.create(urlString));
                        HttpRequest request = builder.build();

                        try {
                            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            if (httpResponse.statusCode() / 100 != 2) {
                                throw new ResolutionException("invalid status code");
                            }
                            return Observable.just(httpResponse);
                        } catch (Exception e) {
                            String message = messageUtils.formatMessage("SERVER_REQUEST_UNSUCCESSFUL", server.getHost());
                            log.warn(message);
                            return Observable.error(e);
                        }
                    }
                }).toBlocking().single();
    }

    public LoadBalancerStats getLoadBalancerStats() {
        return loadBalancer.getLoadBalancerStats();
    }

    public List<Server> getAllServers() {
        return loadBalancer.getServerList(true);
    }
}
