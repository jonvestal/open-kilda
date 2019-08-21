/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.api;

import static java.util.Objects.requireNonNull;

import org.openkilda.model.MeterId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;

@Value
public class MeterConfig implements Serializable {
    @JsonProperty("meter_id")
    private final MeterId id;

    @JsonProperty("bandwidth")
    private final long bandwidth;

    @JsonCreator
    public MeterConfig(
            @JsonProperty("meter_id") MeterId id,
            @JsonProperty("bandwidth") long bandwidth) {
        requireNonNull(id, "Argument meterId must not be null");

        this.id = id;
        this.bandwidth = bandwidth;
    }
}