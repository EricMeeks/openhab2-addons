/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.hdmicec.handler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdmiCecBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Andrew Nagle - Initial contribution
 */
public class HdmiCecBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(HdmiCecBridgeHandler.class);

    // List of Configurations constants
    public static final String CEC_CLIENT_PATH = "cecClientPath";
    public static final String COM_PORT = "comPort";

    private String cecClientPath;
    private String comPort;
    private boolean isRunning;

    private Thread thread;
    private Process process;
    private BufferedReader bufferedReader;
    private BufferedWriter writer;

    public HdmiCecBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Bridge commands not supported.");
    }

    @Override
    public void initialize() {
        logger.debug("Initializing the HdmiCec Bridge handler");
        cecClientPath = (String) this.getConfig().get(CEC_CLIENT_PATH);
        comPort = (String) this.getConfig().get(COM_PORT);

        logger.debug("initialize client: {}, com port: {}", cecClientPath, comPort);

        File cecClientFile = new File(cecClientPath);

        if (!cecClientFile.exists()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "cec-client executable not found.");
            return;
        }
        try {
            startCecClient();
            updateStatus(ThingStatus.ONLINE);
        } catch (IOException e) {
            logger.debug("Bridge handler exception.", e);
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        logger.debug("Disposing bridge handler.");
        try {
            sendCommand("q");
        } catch (Exception e) {
            logger.debug("Bridge handler exception.", e);
            e.printStackTrace();
        }
        super.dispose();
        logger.debug("Bridge handler disposed.");
    }

    private void startCecClient() throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true); // This is the important part
        builder.command(cecClientPath, comPort);

        process = builder.start();
        bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        open();
    }

    private void stopCecClient() {
        try {
            if (process != null) {
                process.destroy();
            }

            if (writer != null) {
                writer.close();
            }

            if (bufferedReader != null) {
                bufferedReader.close();
            }
        } catch (Exception e) {
            logger.debug("Exception in stopCecClient", e);
            e.printStackTrace();
        }

        process = null;
        thread = null;
        bufferedReader = null;
        writer = null;
    }

    private void open() {
        isRunning = true;
        // this.pattern = Pattern.compile(".*TV \\(0\\): power status changed from '(.*)' to '(.*)'");

        if (thread == null) {
            thread = new Thread() {
                @Override
                public void run() {
                    while (isRunning) {
                        String line = null;
                        try {
                            if (process == null || !process.isAlive()) {
                                isRunning = false;
                                callbackCecClientStatus(false, "process ended");
                                return;
                            }

                            line = bufferedReader.readLine();
                        } catch (IOException e) {
                            logger.error("Error reading from cec-client: {}", e.toString());
                            isRunning = false;
                            callbackCecClientStatus(false, e.getMessage());
                            return;
                        }

                        if (line != null) {
                            logger.trace("Line trace: {}", line);
                        }

                        if (line == null) {
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException ie) {
                                logger.error("Sleep error: {}", ie.toString());
                                isRunning = false;
                                Thread.currentThread().interrupt();
                                callbackCecClientStatus(false, "thread aborted");
                                return;
                            }
                        } else if (line.contains("connection opened")) {
                            callbackCecClientStatus(true, "connection opened");
                        } else if (line.contains("communication thread ended")) {
                            callbackCecClientStatus(false, "communication thread ended");
                            isRunning = false;
                        } else if (line.contains("could not start CEC communications")) {
                            callbackCecClientStatus(false, "could not start CEC communications");
                            isRunning = false;
                        } else {
                            for (Thing thing : getThing().getThings()) {
                                HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler) thing.getHandler();
                                Pattern pattern = Pattern.compile(equipment.getPowerPattern());
                                Matcher matcher = pattern.matcher(line);
                                if (matcher.matches()) {
                                    logger.debug("Line trace: {}", line);
                                    // callbackCecPowerChange(matcher.group(1), matcher.group(2));
                                } else {
                                    equipment.cecTriggerEvent("crazyStringToLookFor");
                                }
                            }
                        }
                    }
                }
            };
        } else {
            throw new IllegalStateException("The logger is already running");
        }
        thread.start();
    }

    public void sendCommand(String command) throws IOException {
        if (writer != null) {
            writer.write(command);
            writer.newLine();
            writer.flush();
        }
    }

    private void callbackCecClientStatus(boolean online, String status) {
        if (!online) {
            updateStatus(ThingStatus.OFFLINE);
            stopCecClient();
        }
        for (Thing thing : getThing().getThings()) {
            HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler) thing.getHandler();
            // actually, do we want to do this?
            equipment.cecClientStatus(online, status);
        }
    }
}
