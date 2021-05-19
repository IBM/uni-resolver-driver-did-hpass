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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uniresolver.ResolutionException;

import java.net.http.HttpResponse;

public class JSONUtils {
    private ObjectMapper objectMapper;
    private MessageUtils messageUtils;


    public JSONUtils(ObjectMapper objectMapper,
                     MessageUtils messageUtils) {
        this.objectMapper = objectMapper;
        this.messageUtils = messageUtils;
    }
    private static Logger log = LoggerFactory.getLogger(JSONUtils.class);

    public JsonNode retrieveBodyAsJsonObject(HttpResponse<String> response) throws ResolutionException {
        String responseString = response.body();
        log.debug("HTTP request result: {}", responseString);

        JsonNode responseJson = null;
        try {
            responseJson = this.objectMapper.readTree(responseString);
        } catch (JsonProcessingException e) {
            String message = this.messageUtils.formatMessage("COULD_NOT_EXTRACT_JSON_OBJECT_FROM_HTTP_RESPONSE", e.getMessage());
            log.error(message);
            throw new ResolutionException(message, e);
        }
        return responseJson;
    }
}
