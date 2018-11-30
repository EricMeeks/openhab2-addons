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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CecClientHandler} is responsible for
 * the communication with the cec device.
 *
 * @author David Masshardt - Initial contribution
 */
public class CecClientHandler {
    private Logger logger = LoggerFactory.getLogger(CecClientHandler.class);

    private BufferedReader bufferedReader;
    private InputStream inputStream;
    private Thread thread;
    private boolean isRunning;
    private Pattern pattern;
    private CecClientCallback callback;
    private Process process;
    private StreamHandler handler;
    private BufferedWriter writer;
    private String cecClientPath;
    private String comPort;

    public CecClientHandler(String cecClientPath, String comPort) {
        this.cecClientPath = cecClientPath;
        this.comPort = comPort;
    }

    public void setEventHandler(CecClientCallback callback) {
        this.callback = callback;
    }

    public void startCecClient() throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.redirectErrorStream(true); // This is the important part
        builder.command(cecClientPath, comPort);

        process = builder.start();
        InputStream is = process.getInputStream();
        OutputStream os = process.getOutputStream();
        writer = new BufferedWriter(new OutputStreamWriter(os));

        open(is);
    }

    public void stopCecClient() throws InterruptedException, IOException {
        if (handler != null) {
            handler.close();
            sendCommand("q");
            Thread.sleep(2000);
        }

        if (process != null) {
            process.destroy();
        }

        handler = null;
        process = null;
        writer = null;
    }

    public void dispose() throws InterruptedException, IOException {
        stopCecClient();
        callback = null;
    }

    private void open(InputStream inputStream) {
        this.inputStream = inputStream;
        bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()));
        isRunning = true;
        pattern = Pattern.compile(".*TV \\(0\\): power status changed from '(.*)' to '(.*)'");

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
                            Matcher matcher = pattern.matcher(line);

                            if (matcher.matches() && callback != null) {
                                logger.debug("Line trace: {}", line);
                                callbackCecPowerChange(matcher.group(1), matcher.group(2));
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

    private void callbackCecClientStatus(boolean online, String status) {
        if (callback != null) {
            callback.cecClientStatus(online, status);
        }
    }

    private void callbackCecPowerChange(String from, String to) {
        if (callback != null) {
            callback.cecPowerChange(from, to);
        }
    }

    public void close() throws InterruptedException, IOException {
        if (thread != null) {
            thread.interrupt();
            thread.join();
            thread = null;
            inputStream.close();
            callbackCecClientStatus(false, "closed");
        } else {
            throw new IllegalStateException("The logger is not running");
        }
    }

    public void sendCommand(String command) throws IOException {
        if (writer != null) {
            writer.write(command);
            writer.newLine();
            writer.flush();
        }
    }
}
