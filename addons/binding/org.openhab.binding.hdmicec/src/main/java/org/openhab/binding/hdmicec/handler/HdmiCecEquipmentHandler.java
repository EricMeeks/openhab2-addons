/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdmicec.handler;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.hdmicec.HdmiCecBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdmiCecEquipmentHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author David Masshardt - Initial contribution
 */
public class HdmiCecEquipmentHandler extends BaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(HdmiCecEquipmentHandler.class);

    // List of Configurations constants
    public static final String DEVICE = "device";

    private static final Pattern active = Pattern.compile(".*making .* the active source");
    private static final Pattern power = Pattern.compile(".* power status changed from '(.*)' to '(.*)'");

    // config paramaters
    private String device; // hex number, like 0 or e

    private HdmiCecBridgeHandler bridgeHandler;

    public HdmiCecEquipmentHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_SEND)) {
            try {
                if (command instanceof StringType) {
                    // think about this, do we want to have a controlled vocabulary or just transmit something raw, or
                    // both?
                    bridgeHandler.sendCommand(command.toString());
                }
            } catch (IOException e) {
                logger.error("Error in handleCommand: {}", e.toString());
            }
        }
    }

    public String getDevice() {
        return device;
    }

    @Override
    public void initialize() {
        try {
            device = (String) this.getConfig().get(DEVICE);

            logger.debug("Initializing thing {}", getThing().getUID());
            bridgeHandler = (HdmiCecBridgeHandler) getBridge().getHandler();

            if (getBridge().getStatus() == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } catch (Exception e) {
            logger.error("Error in initialize: {}", e.toString());
        }
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    public void cecClientStatus(boolean online, String status) {
        if (online) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, status);
        }

        logger.debug("Cec client status: online = {} status = {}", online, status);
    }

    void cecMatchLine(String line) {
        Matcher matcher = active.matcher(line);
        if (matcher.matches()) {
            triggerChannel(HdmiCecBindingConstants.CHANNEL_EVENT, "MADE_ACTIVE_SOURCE");
        } else {
            matcher = power.matcher(line);
            if (matcher.matches()) {
                triggerChannel(HdmiCecBindingConstants.CHANNEL_EVENT,
                        "POWER_CHANGED_TO_" + matcher.group(2).replace(' ', '_'));
            }
        }
    }

}
