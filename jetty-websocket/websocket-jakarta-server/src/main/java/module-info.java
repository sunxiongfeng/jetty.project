//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//


import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.servlet.ServletContainerInitializer;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.websocket.jakarta.server.config.ContainerDefaultConfigurator;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketConfiguration;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;

module org.eclipse.jetty.websocket.jakarta.server
{
    exports org.eclipse.jetty.websocket.jakarta.server.config;

    requires transitive org.eclipse.jetty.webapp;
    requires transitive org.eclipse.jetty.websocket.jakarta.client;
    requires org.eclipse.jetty.websocket.servlet;

    provides ServletContainerInitializer with JakartaWebSocketServletContainerInitializer;
    provides ServerEndpointConfig.Configurator with ContainerDefaultConfigurator;
    provides Configuration with JakartaWebSocketConfiguration;
}
