/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Cookie;
import org.openkilda.wfm.topology.stats.FlowDirectionHelper.Direction;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FlowDirectionHelperTest {
    private static final long FORWARD_COOKIE = 0x4000000000000001L;
    private static final long REVERSE_COOKIE = 0x2000000000000001L;
    private static final long FORWARD_LLDP_COOKIE = Cookie.buildLldpCookie(1L, true).getValue();
    private static final long REVERSE_LLDP_COOKIE = Cookie.buildLldpCookie(1L, false).getValue();
    private static final long BAD_COOKIE =     0x235789abcd432425L;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void isKildaCookieTest() {
        assertTrue(FlowDirectionHelper.isKildaCookie(FORWARD_COOKIE));
        assertTrue(FlowDirectionHelper.isKildaCookie(REVERSE_COOKIE));
        assertTrue(FlowDirectionHelper.isKildaCookie(FORWARD_LLDP_COOKIE));
        assertTrue(FlowDirectionHelper.isKildaCookie(REVERSE_LLDP_COOKIE));
        assertFalse(FlowDirectionHelper.isKildaCookie(BAD_COOKIE));
    }

    @Test
    public void findDirectionTest() throws Exception {
        assertEquals(Direction.FORWARD, FlowDirectionHelper.findDirection(FORWARD_COOKIE));
        assertEquals(Direction.REVERSE, FlowDirectionHelper.findDirection(REVERSE_COOKIE));
        assertEquals(Direction.FORWARD, FlowDirectionHelper.findDirection(FORWARD_LLDP_COOKIE));
        assertEquals(Direction.REVERSE, FlowDirectionHelper.findDirection(REVERSE_LLDP_COOKIE));


        thrown.expect(Exception.class);
        thrown.expectMessage(BAD_COOKIE + " is not a Kilda flow");
        FlowDirectionHelper.findDirection(BAD_COOKIE);
    }
}
