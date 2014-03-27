/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.net;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OptionalDataException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import org.apache.logging.log4j.core.config.ConfigurationFactory;

/**
 * Listens for events over a socket connection.
 */
public class UDPSocketServer extends AbstractSocketServer implements Runnable {

    /**
     * Creates a socket server that reads JSON log events.
     * 
     * @param port the port to listen
     * @return a new a socket server
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public static UDPSocketServer createJsonSocketServer(final int port) throws IOException {
        return new UDPSocketServer(port, new JSONLogEventInput());
    }

    /**
     * Creates a socket server that reads serialized log events.
     * 
     * @param port the port to listen
     * @return a new a socket server
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public static UDPSocketServer createSerializedSocketServer(final int port) throws IOException {
        return new UDPSocketServer(port, new SerializedLogEventInput());
    }

    /**
     * Creates a socket server that reads XML log events.
     * 
     * @param port the port to listen
     * @return a new a socket server
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public static UDPSocketServer createXmlSocketServer(final int port) throws IOException {
        return new UDPSocketServer(port, new XMLLogEventInput());
    }

    private final DatagramSocket datagramSocket;

    // max size so we only have to deal with one packet
    private final int maxBufferSize = 1024 * 65 + 1024;

    /**
     * Constructor.
     * 
     * @param port to listen on.
     * @param logEventInput
     * @throws IOException If an error occurs.
     */
    public UDPSocketServer(final int port, final LogEventInput logEventInput) throws IOException {
        super(port, logEventInput);
        this.datagramSocket = new DatagramSocket(port);
    }

    /**
     * Main startup for the server.
     * 
     * @param args The command line arguments.
     * @throws Exception if an error occurs.
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Incorrect number of arguments");
            printUsage();
            return;
        }
        final int port = Integer.parseInt(args[0]);
        if (port <= 0 || port >= MAX_PORT) {
            System.err.println("Invalid port number");
            printUsage();
            return;
        }
        if (args.length == 2 && args[1].length() > 0) {
            ConfigurationFactory.setConfigurationFactory(new ServerConfigurationFactory(args[1]));
        }
        final UDPSocketServer socketServer = UDPSocketServer.createSerializedSocketServer(port);
        final Thread server = new Thread(socketServer);
        server.start();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            final String line = reader.readLine();
            if (line == null || line.equalsIgnoreCase("Quit") || line.equalsIgnoreCase("Stop") || line.equalsIgnoreCase("Exit")) {
                socketServer.shutdown();
                server.join();
                break;
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: ServerSocket port configFilePath");
    }

    /**
     * Shutdown the server.
     */
    public void shutdown() {
        this.setActive(false);
        Thread.currentThread().interrupt();
    }

    /**
     * Accept incoming events and processes them.
     */
    @Override
    public void run() {
        while (isActive()) {
            try {
                final byte[] buf = new byte[maxBufferSize];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                datagramSocket.receive(packet);
                final ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
                log(logEventInput.readLogEvent(bais));
            } catch (final OptionalDataException e) {
                logger.error("OptionalDataException eof=" + e.eof + " length=" + e.length, e);
            } catch (final EOFException e) {
                logger.info("EOF encountered");
            } catch (final IOException e) {
                logger.error("Exception encountered on accept. Ignoring. Stack Trace :", e);
            }
        }
    }
}
