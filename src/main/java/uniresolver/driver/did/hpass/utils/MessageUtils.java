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

import java.text.MessageFormat;
import java.util.ResourceBundle;

public class MessageUtils {

  private final ResourceBundle resourceBundle;

  public MessageUtils(ResourceBundle resourceBundle) {
    this.resourceBundle = resourceBundle;
  }

  public String getMessageString(String messageKey) {
    return this.resourceBundle.getString(messageKey);
  }

  public String formatMessage(String messageKey) {
    MessageFormat mf = new MessageFormat(getMessageString(messageKey));
    return mf.format(new Object[0]);
  }

  public String formatMessage(String messageKey,
      Object arg0) {
    MessageFormat mf = new MessageFormat(getMessageString(messageKey));
    Object[] args = new Object[1];
    args[0] = arg0;
    return mf.format(args);
  }

  public String formatMessage(String messageKey,
      Object arg0,
      Object arg1) {
    MessageFormat mf = new MessageFormat(getMessageString(messageKey));
    Object[] args = new Object[2];
    args[0] = arg0;
    args[1] = arg1;
    return mf.format(args);
  }
  // Include implementations of formatMessage() for as many arguments
  // as you need
}
