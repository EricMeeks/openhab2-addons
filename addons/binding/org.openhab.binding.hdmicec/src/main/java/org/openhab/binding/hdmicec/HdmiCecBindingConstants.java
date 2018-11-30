/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_PULSE_EIGHT_CEC = new ThingTypeUID(BINDING_ID, "pulseEightCec");

    // List of all Channel ids
    public final static String CHANNEL_POWER = "power";

}
