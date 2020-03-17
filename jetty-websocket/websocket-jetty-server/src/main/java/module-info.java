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

import jakarta.servlet.ServletContainerInitializer;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketConfiguration;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

module org.eclipse.jetty.websocket.jetty.server
{
    exports org.eclipse.jetty.websocket.server;
    exports org.eclipse.jetty.websocket.server.config;

    requires jetty.servlet.api;
    requires transitive org.eclipse.jetty.webapp;
    requires transitive org.eclipse.jetty.websocket.jetty.common;
    requires transitive org.eclipse.jetty.websocket.servlet;

    // Only required if using JMX.
    requires static org.eclipse.jetty.jmx;

    provides ServletContainerInitializer with JettyWebSocketServletContainerInitializer;
    provides Configuration with JettyWebSocketConfiguration;
}
