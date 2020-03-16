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

package org.eclipse.jetty.tests.webapp.websocket;

import java.io.IOException;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerEndpoint("/onopen/{arg}")
public class OnOpenServerEndpoint
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OnOpenServerEndpoint.class);
    private static String open = "";

    @OnMessage
    public String echo(String echo)
    {
        return open + echo;
    }

    @OnOpen
    public void onOpen(Session session)
    {
        LOGGER.info("Session opened");
    }

    @OnError
    public void onError(Session session, Throwable t)
        throws IOException
    {
        String message = "Error happened:" + t.getMessage();
        session.getBasicRemote().sendText(message);
    }
}
