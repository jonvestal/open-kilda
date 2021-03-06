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

package org.openkilda.persistence.ferma.frames.converters;

import org.openkilda.model.MirrorDirection;

/**
 * Case-insensitive converter to convert {@link MirrorDirection} to {@link String} and back.
 */
public class MirrorDirectionConverter implements AttributeConverter<MirrorDirection, String> {
    public static final MirrorDirectionConverter INSTANCE = new MirrorDirectionConverter();

    @Override
    public String toGraphProperty(MirrorDirection value) {
        if (value == null) {
            return null;
        }
        return value.name().toLowerCase();
    }

    @Override
    public MirrorDirection toEntityAttribute(String value) {
        return MirrorDirection.valueOf(value.toUpperCase());
    }
}
