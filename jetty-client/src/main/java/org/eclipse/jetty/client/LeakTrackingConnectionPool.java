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

package org.eclipse.jetty.client;

import org.eclipse.jetty.client.api.Connection;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.LeakDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeakTrackingConnectionPool extends DuplexConnectionPool
{
    private static final Logger LOG = LoggerFactory.getLogger(LeakTrackingConnectionPool.class);

    private final LeakDetector<Connection> leakDetector = new LeakDetector<Connection>()
    {
        @Override
        protected void leaked(LeakInfo leakInfo)
        {
            LeakTrackingConnectionPool.this.leaked(leakInfo);
        }
    };

    public LeakTrackingConnectionPool(Destination destination, int maxConnections, Callback requester)
    {
        super(destination, maxConnections, requester);
        start();
    }

    private void start()
    {
        try
        {
            leakDetector.start();
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public void close()
    {
        stop();
        super.close();
    }

    private void stop()
    {
        try
        {
            leakDetector.stop();
        }
        catch (Exception x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    protected void acquired(Connection connection)
    {
        if (!leakDetector.acquired(connection))
            LOG.info("Connection {}@{} not tracked", connection, leakDetector.id(connection));
    }

    @Override
    protected void released(Connection connection)
    {
        if (!leakDetector.released(connection))
            LOG.info("Connection {}@{} released but not acquired", connection, leakDetector.id(connection));
    }

    protected void leaked(LeakDetector.LeakInfo leakInfo)
    {
        LOG.info("Connection " + leakInfo.getResourceDescription() + " leaked at:", leakInfo.getStackFrames());
    }
}
