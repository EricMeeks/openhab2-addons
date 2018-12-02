/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdmicec.internal;

import static org.openhab.binding.hdmicec.HdmiCecBindingConstants.*;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.hdmicec.handler.HdmiCecBridgeHandler;
import org.openhab.binding.hdmicec.handler.HdmiCecEquipmentHandler;

/**
 * The {@link HdmiCecHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author David Masshardt - Initial contribution
 */
public class HdmiCecHandlerFactory extends BaseThingHandlerFactory {

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Stream.of(THING_TYPE_BRIDGE, THING_TYPE_EQUIPMENT)
            .collect(Collectors.toSet());

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {

        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(THING_TYPE_EQUIPMENT)) {
            return new HdmiCecEquipmentHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_BRIDGE)) {
            return new HdmiCecBridgeHandler((Bridge) thing);
        }

        return null;
    }
}
