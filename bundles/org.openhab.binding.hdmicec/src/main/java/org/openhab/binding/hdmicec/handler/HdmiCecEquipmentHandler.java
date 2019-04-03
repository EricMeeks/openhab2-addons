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
package org.openhab.binding.hdmicec.handler;

import java.util.regex.Matcher;

import org.eclipse.smarthome.core.library.types.OnOffType;
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
    public static final String ADDRESS = "address";

    // config paramaters
    private String device; // hex number, like 0 or e
    private String address; // of the form 0.0.0.0

    private HdmiCecBridgeHandler bridgeHandler;

    public HdmiCecEquipmentHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_POWER)) {
            if (command.equals(OnOffType.ON)) {
                bridgeHandler.sendCommand("on " + getDevice());
            } else if (command.equals(OnOffType.OFF)) {
                bridgeHandler.sendCommand("standby " + getDevice());
            }
        } else if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE)) {
            if (command.equals(OnOffType.ON)) {
                bridgeHandler.sendCommand("tx " + getDevice() + "F:82:" + getAddressAsFrame());
            } else if (command.equals(OnOffType.OFF)) {
                bridgeHandler.sendCommand("tx " + getDevice() + "F:9D:" + getAddressAsFrame());
            }
        }
    }

    public String getDevice() {
        return device;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressAsFrame() {
        return address.replace(".", "").substring(0, 2) + ":" + address.replace(".", "").substring(2);
    }

    @Override
    public void initialize() {
        try {
            getThing().setLabel(getThing().getLabel().replace("Equipment", getThing().getUID().getId()));
            device = (String) this.getConfig().get(DEVICE);
            address = (String) this.getConfig().get(ADDRESS);

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
        Matcher matcher = bridgeHandler.getPowerOn().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.ON);
            return;
        }
        matcher = bridgeHandler.getPowerOff().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.OFF);
            return;
        }
        matcher = bridgeHandler.getActiveSourceOn().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE, OnOffType.ON);
            return;
        }
        matcher = bridgeHandler.getActiveSourceOff().matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE, OnOffType.OFF);
            return;
        }
        matcher = bridgeHandler.getEventPattern().matcher(line);
        if (matcher.matches()) {
            triggerChannel(HdmiCecBindingConstants.CHANNEL_EVENT, matcher.group(2));
            return;
        }
    }

}
