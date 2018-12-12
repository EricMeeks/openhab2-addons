/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
    public static final String DEVICE_STATEMENT_REGEX = "deviceStatementRegex";
    public static final String POWER_ON_REGEX = "powerOnRegex";
    public static final String POWER_OFF_REGEX = "powerOffRegex";
    public static final String ACTIVE_SOURCE_ON_REGEX = "activeSourceOnRegex";
    public static final String ACTIVE_SOURCE = "ActiveSourceOffRegex";

    private String cecClientPath;
    private String comPort;

    // we're betting on the fact that the first value in () is the device ID. Seems valid from what I've seen!
    private Pattern deviceStatement = Pattern.compile("DEBUG.* \\((.)\\).*");
    private Pattern powerOn = Pattern.compile(".*: power status changed from '(.*)' to 'on'");
    private Pattern powerOff = Pattern.compile(".*: power status changed from '(.*)' to 'standby'");
    private Pattern activeSourceOn = Pattern.compile(".*making .* \\((.)\\) the active source");
    private Pattern activeSourceOff = Pattern.compile(".*marking .* \\((.)\\) as inactive source");
    private Pattern eventPattern = Pattern.compile("^(?!.*(<<|>>)).*: (.*)$"); // the 2nd group is the event

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
        if (this.getConfig().containsKey(DEVICE_STATEMENT_REGEX)) {
            deviceStatement = Pattern.compile((String) this.getConfig().get(DEVICE_STATEMENT_REGEX));
        }
        if (this.getConfig().containsKey(POWER_ON_REGEX)) {
            powerOn = Pattern.compile((String) this.getConfig().get(POWER_ON_REGEX));
        }
        if (this.getConfig().containsKey(POWER_OFF_REGEX)) {
            powerOff = Pattern.compile((String) this.getConfig().get(POWER_OFF_REGEX));
        }
        if (this.getConfig().containsKey(ACTIVE_SOURCE_ON_REGEX)) {
            activeSourceOn = Pattern.compile((String) this.getConfig().get(ACTIVE_SOURCE_ON_REGEX));
        }
        if (this.getConfig().containsKey(ACTIVE_SOURCE)) {
            activeSourceOff = Pattern.compile((String) this.getConfig().get(ACTIVE_SOURCE));
        }

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
        }
    }

    @Override
    public void dispose() {
        logger.debug("Disposing bridge handler.");
        try {
            sendCommand("q");
        } catch (Exception e) {
            logger.debug("Bridge handler exception.", e);
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
        }

        process = null;
        thread = null;
        bufferedReader = null;
        writer = null;
    }

    private void open() {
        isRunning = true;

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
                            Matcher matcher = deviceStatement.matcher(line);
                            if (matcher.matches()) {
                                for (Thing thing : getThing().getThings()) {
                                    HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler) thing.getHandler();
                                    if (equipment != null && equipment.getDevice().equalsIgnoreCase(matcher.group(1))) {
                                        equipment.cecMatchLine(line);
                                    }
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

    Pattern getDeviceStatement() {
        return deviceStatement;
    }

    Pattern getPowerOn() {
        return powerOn;
    }

    Pattern getPowerOff() {
        return powerOff;
    }

    Pattern getActiveSourceOn() {
        return activeSourceOn;
    }

    Pattern getActiveSourceOff() {
        return activeSourceOff;
    }

    Pattern getEventPattern() {
        return eventPattern;
    }

    public void sendCommand(String command) {
        try {
            writer.write(command);
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            logger.debug("Bridge handler exception in sendCommand: " + command, e);
        }

    }

    private void callbackCecClientStatus(boolean online, String status) {
        if (!online) {
            updateStatus(ThingStatus.OFFLINE);
            stopCecClient();
        }
        for (Thing thing : getThing().getThings()) {
            HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler) thing.getHandler();
            if (equipment != null) {
                // actually, do we want to do this?
                equipment.cecClientStatus(online, status);
            }
        }
    }
}
