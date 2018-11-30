/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hdmicec.handler;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
 * The {@link HdmiCecHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author David Masshardt - Initial contribution
 */
public class HdmiCecHandler extends BaseThingHandler implements CecClientCallback {

    private Logger logger = LoggerFactory.getLogger(HdmiCecHandler.class);

    // List of Configurations constants
    public static final String CEC_CLIENT_PATH = "cecClientPath";
    public static final String COM_PORT = "comPort";
    public static final String POLL_INTERVAL = "pollInterval";
    public static final String IGNORE_UNKNOWN_STATE = "ignoreUnknownState";

    private CecClientHandler handler;
    private ScheduledFuture<?> executionJob;
    private ScheduledFuture<?> reconnectJob;

    public HdmiCecHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_POWER)) {
            try {
                if (command.equals(OnOffType.ON)) {
                    handler.sendCommand("on 0");
                } else if (command.equals(OnOffType.OFF)) {
                    handler.sendCommand("standby 0");
                }
            } catch (IOException e) {
                logger.error("Error in handleCommand: {}", e.toString());
            }
        }
    }

    @Override
    public void initialize() {
        try {
            String cecClientPath = (String) this.getConfig().get(CEC_CLIENT_PATH);
            String comPort = (String) this.getConfig().get(COM_PORT);

            logger.debug("initialize client: {}, com port: {}", cecClientPath, comPort);

            File cecClientFile = new File(cecClientPath);

            if (!cecClientFile.exists()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "cec-client executable not found.");
                return;
            }

            handler = new CecClientHandler(cecClientPath, comPort);
            handler.setEventHandler(this);
            handler.startCecClient();

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (handler != null) {
                            handler.stopCecClient();
                        }
                    } catch (InterruptedException e) {
                        logger.error("Error in initialize addShutdownHook: {}", e.toString());
                    } catch (IOException e) {
                        logger.error("Error in initialize addShutdownHook: {}", e.toString());
                    }
                }
            }));
        } catch (Exception e) {
            logger.error("Error in initialize: {}", e.toString());
        }
    }

    private void startPollingJob() {
        if (executionJob == null || executionJob.isCancelled()) {
            if (((BigDecimal) getConfig().get(POLL_INTERVAL)) != null
                    && ((BigDecimal) getConfig().get(POLL_INTERVAL)).intValue() > 0) {
                int polling_interval = ((BigDecimal) getConfig().get(POLL_INTERVAL)).intValue();
                executionJob = scheduler.scheduleWithFixedDelay(pollingRunnable, polling_interval, polling_interval,
                        TimeUnit.SECONDS);
                logger.debug("Polling job started");
            }
        }
    }

    private void stopPollingJob() {
        if (executionJob != null && !executionJob.isCancelled()) {
            executionJob.cancel(true);
            executionJob = null;
            logger.debug("Polling job stopped");
        }
    }

    private void startReconnectJob() {
        if (reconnectJob == null || reconnectJob.isCancelled()) {
            reconnectJob = scheduler.scheduleWithFixedDelay(reconnectRunnable, 60, 60, TimeUnit.SECONDS);
            logger.debug("Reconnect job started");
        }
    }

    private void stopReconnectJob() {
        if (reconnectJob != null && !reconnectJob.isCancelled()) {
            reconnectJob.cancel(true);
            reconnectJob = null;
            logger.debug("Reconnect job stopped");
        }
    }

    @Override
    public void dispose() {
        super.dispose();

        disposeCecHandler();
        stopPollingJob();
        stopReconnectJob();
    }

    private void disposeCecHandler() {
        if (handler != null) {
            try {
                handler.dispose();
                handler = null;
            } catch (InterruptedException e) {
                logger.error("Error in disposeCecHandler: {}", e.toString());
            } catch (IOException e) {
                logger.error("Error in disposeCecHandler: {}", e.toString());
            }
        }
    }

    @Override
    public void cecPowerChange(String from, String to) {
        boolean ignoreUnknownState = (boolean) getConfig().get(IGNORE_UNKNOWN_STATE);

        if (to.equals("on")) {
            this.updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.ON);
        } else if (to.equals("off") || (to.equals("unknown") && !ignoreUnknownState)) {
            this.updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.OFF);
        }

        logger.debug("Cec power change from {} to {}", from, to);
    }

    @Override
    public void cecClientStatus(boolean online, String status) {
        if (online) {
            updateStatus(ThingStatus.ONLINE);
            stopReconnectJob();
            startPollingJob();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, status);
            stopPollingJob();
            disposeCecHandler();
            startReconnectJob();
        }

        logger.debug("Cec client status: online = {} status = {}", online, status);
    }

    protected Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (handler != null) {
                try {
                    handler.sendCommand("pow 0");
                } catch (IOException e) {
                    logger.error("Error in pollingRunnable: {}", e.toString());
                }
            }
        }
    };

    protected Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            logger.debug("{}", "Trying to reconnect");
            stopReconnectJob();
            initialize();
        }
    };
}
