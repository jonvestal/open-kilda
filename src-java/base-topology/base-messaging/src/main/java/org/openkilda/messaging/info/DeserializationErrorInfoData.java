/* Copyright 2020 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.messaging.info;

import org.openkilda.bluegreen.kafka.DeserializationError;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class DeserializationErrorInfoData extends ErrorInfoData implements DeserializationError {

    public DeserializationErrorInfoData(String errorMessage, String errorDescription) {
        super(errorMessage, errorDescription);
    }

    @Override
    public String getDeserializationErrorMessage() {
        return String.format("Couldn't deserialize kafka message. Error message: %s, Error description: %s",
                getErrorMessage(), getErrorDescription());
    }
}
