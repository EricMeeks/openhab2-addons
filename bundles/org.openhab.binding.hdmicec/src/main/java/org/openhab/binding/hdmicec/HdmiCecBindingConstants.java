/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.hdmicec;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link HdmiCecBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author David Masshardt - Initial contribution
 */
public class HdmiCecBindingConstants {

    public static final String BINDING_ID = "hdmicec";

    public final static ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_EQUIPMENT = new ThingTypeUID(BINDING_ID, "equipment");

    // List of all Channel ids
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_ACTIVE_SOURCE = "activeSource";
    public static final String CHANNEL_EVENT = "event";
    public static final String CHANNEL_SEND = "send";

}
