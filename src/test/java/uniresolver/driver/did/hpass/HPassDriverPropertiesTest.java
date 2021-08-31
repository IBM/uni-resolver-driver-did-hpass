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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED;
import static uniresolver.driver.did.hpass.constants.EnvironmentVariables.UNIRESOLVER_DRIVER_DID_REGISTRY_URL;

import java.util.HashMap;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import uniresolver.ResolutionException;
import uniresolver.driver.did.hpass.utils.MessageUtils;

public class HPassDriverPropertiesTest {

  public static ResourceBundle messageBundle = ResourceBundle.getBundle("Messages");
  public static MessageUtils messageUtils = new MessageUtils(messageBundle);

  @Test
  void happyEnvReadProperties() throws ResolutionException {
    DidHpassDriver didHpassDriver = new DidHpassDriver();

    boolean propertiesOk = didHpassDriver.properties().get(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED).equals("true")
        && didHpassDriver.properties().containsKey(UNIRESOLVER_DRIVER_DID_REGISTRY_URL);

    if (didHpassDriver.properties().get(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED).equals("false")
        && didHpassDriver.properties().containsKey(UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL)) {
      propertiesOk = true;
    }

    if (!didHpassDriver.properties().containsKey(UNIRESOLVER_DRIVER_DID_REGISTRY_ENABLED)
        && didHpassDriver.properties().containsKey(UNIRESOLVER_DRIVER_DID_HEALTH_NODE_URL)) {
      propertiesOk = true;
    }

    assertTrue(propertiesOk);
  }

  @Test
  void failReadProperties() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      new DidHpassDriver(new HashMap<>());
    });
    String expected = messageUtils.formatMessage("VALUE_FOR_KEY_IS_NULL", "");
    expected = expected.substring(0, expected.lastIndexOf(":"));
    assertTrue(exception.getMessage().contains(expected));
  }
}
