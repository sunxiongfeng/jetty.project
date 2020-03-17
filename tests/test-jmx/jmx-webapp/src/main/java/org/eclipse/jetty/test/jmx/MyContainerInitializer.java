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

package org.eclipse.jetty.test.jmx;

import java.util.Set;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class MyContainerInitializer implements ServletContainerInitializer
{
    private static final Logger LOG = Log.getLogger(MyContainerInitializer.class);

    @Override
    public void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException
    {
        // Directly annotated with @ManagedObject
        CommonComponent common = new CommonComponent();
        LOG.info("Initializing " + common.getClass().getName());
        ctx.setAttribute("org.eclipse.jetty.test.jmx.common", common);

        // Indirectly managed via a MBean
        ctx.setAttribute("org.eclipse.jetty.test.jmx.ping", new Pinger());
        ctx.setAttribute("org.eclipse.jetty.test.jmx.echo", new Echoer());
    }
}
