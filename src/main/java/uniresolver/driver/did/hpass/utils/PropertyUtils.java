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

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uniresolver.ResolutionException;

import java.util.HashMap;
import java.util.Map;

import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.*;

public class PropertyUtils {
    private MessageUtils messageUtils;

    public PropertyUtils(MessageUtils messageUtils) {
        this.messageUtils = messageUtils;
    }

    private static Logger log = LoggerFactory.getLogger(PropertyUtils.class);

    public static Map<String, Object> getPropertiesFromEnvironment() {
        Map<String, Object> properties = new HashMap<>();
        Dotenv dotenv;

        try {
            dotenv = Dotenv.configure().ignoreIfMissing().systemProperties().load();
        } catch (Exception ex) {
            String message = "Error reading environment variables, message:" + ex.getMessage();
            log.error(message);
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }

        String[] keys = {
                UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL,
                UNIRESOLVER_DRIVER_DID_REGISTRY_URL,
                UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED,
                UNIRESOLVER_DRIVER_AUTH_ENABLED,
            UNIRESOLVER_DRIVER_USER,
                UNIRESOLVER_DRIVER_PASSWORD,
                UNIRESOLVER_DRIVER_AUTH_LOGIN_URL
        };
        for (String key : keys) {
            String envValue = dotenv.get(key);
            if (envValue != null && !envValue.isEmpty()) {
                properties.put(key, envValue);
            }
        }

        log.info("Loaded properties from environment");
        log.debug("Environment properties: {}", properties);

        return properties;
    }

    public void validateProperties(Map<String, Object> properties) throws IllegalArgumentException {
        // set default
        properties.putIfAbsent(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED, "false");
        properties.putIfAbsent(UNIRESOLVER_DRIVER_AUTH_ENABLED, "true");
        properties.putIfAbsent(UNIRESOLVER_DRIVER_AUTH_LOGIN_URL, "https://dev1.wh-hpass.dev.watson-health.ibm.com/api/v1/hpass/users/login");

        String message = null;
        switch (properties.get(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED).toString()) {
            case "true":
                if (properties.get(UNIRESOLVER_DRIVER_DID_REGISTRY_URL) == null) {
                    message = this.messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL", UNIRESOLVER_DRIVER_DID_REGISTRY_URL);
                    log.error(message);
                    throw new IllegalArgumentException(message);
                }
                break;
            default:
                if (properties.get(UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL) == null) {
                    message = this.messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL", UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL);
                    log.error(message);
                    throw new IllegalArgumentException(message);
                }
                properties.put(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED, "false");
        }
    }

    public String getPropertyByKey(Map<String, Object> properties, String key) throws ResolutionException {
        String value;
        try {
            value = (String) properties.get(key);
        } catch (Exception e) {
            String message = this.messageUtils.formatMessage("ILLEGAL_ARGUMENT", key, e.getMessage());
            log.error(message);
            throw new ResolutionException(message, e);
        }

        if (value == null) {
            String message = this.messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL", key);
            log.error(message);
            throw new ResolutionException(message);
        }

        return value;
    }
}
