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

package org.eclipse.jetty.osgi.test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Inject;

import javax.websocket.ContainerProvider;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

/**
 * Test using websocket in osgi
 */
@RunWith(PaxExam.class)
public class TestJettyOSGiBootWithJakartaWebSocket
{
    private static final String LOG_LEVEL = "WARN";

    @Inject
    BundleContext bundleContext = null;

    @Configuration
    public static Option[] configure()
    {
        ArrayList<Option> options = new ArrayList<>();
        // options.add(TestOSGiUtil.optionalRemoteDebug());
        options.add(CoreOptions.junitBundles());
        options.addAll(TestOSGiUtil.configureJettyHomeAndPort(false, "jetty-http-boot-with-jakarta-websocket.xml"));
        options.add(CoreOptions.bootDelegationPackages("org.xml.sax", "org.xml.*", "org.w3c.*", "javax.sql.*", "javax.xml.*", "javax.activation.*"));
        options.add(CoreOptions.systemPackages("com.sun.org.apache.xalan.internal.res", "com.sun.org.apache.xml.internal.utils",
            "com.sun.org.apache.xml.internal.utils", "com.sun.org.apache.xpath.internal",
            "com.sun.org.apache.xpath.internal.jaxp", "com.sun.org.apache.xpath.internal.objects"));

        options.addAll(TestOSGiUtil.coreJettyDependencies());
        options.add(mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-java-client").versionAsInProject().start());
        options.add(mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-client").versionAsInProject().start());
        options.add(systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value(LOG_LEVEL));
        options.add(systemProperty("org.eclipse.jetty.LEVEL").value(LOG_LEVEL));
        options.addAll(jspDependencies());
        options.addAll(testJettyWebApp());
        options.addAll(extraDependencies());
        return options.toArray(new Option[0]);
    }

    public static List<Option> jspDependencies()
    {
        return TestOSGiUtil.jspDependencies();
    }

    public static List<Option> testJettyWebApp()
    {
        List<Option> res = new ArrayList<>();
        //test webapp bundle
        res.add(mavenBundle().groupId("org.eclipse.jetty").artifactId("test-jetty-webapp").classifier("webbundle").versionAsInProject().noStart());
        return res;
    }

    public static List<Option> extraDependencies()
    {
        List<Option> res = new ArrayList<>();
        res.add(mavenBundle().groupId("biz.aQute.bnd").artifactId("bndlib").versionAsInProject().start());
        res.add(mavenBundle().groupId("org.ops4j.pax.tinybundles").artifactId("tinybundles").version("2.1.1").start());
        return res;
    }

    public void assertAllBundlesActiveOrResolved()
    {
        TestOSGiUtil.assertAllBundlesActiveOrResolved(bundleContext);
        TestOSGiUtil.debugBundles(bundleContext);
    }

    @Test
    public void testWebsocket() throws Exception
    {
        startBundle(bundleContext, "org.eclipse.jetty.websocket.jakarta.common");
        startBundle(bundleContext, "org.eclipse.jetty.websocket.jakarta.client");
        startBundle(bundleContext, "org.eclipse.jetty.websocket.jakarta.server");
        startBundle(bundleContext, "org.eclipse.jetty.tests.webapp");

        if (Boolean.getBoolean(TestOSGiUtil.BUNDLE_DEBUG))
            assertAllBundlesActiveOrResolved();

        String port = System.getProperty("boot.jakarta.websocket.port");
        assertNotNull(port);

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        assertNotNull(container);

        SimpleJakartaWebSocket socket = new SimpleJakartaWebSocket();
        URI uri = new URI("ws://127.0.0.1:" + port + "/jakarta.websocket/");
        try (Session session = container.connectToServer(socket, uri))
        {
            RemoteEndpoint.Basic remote = session.getBasicRemote();
            String msg = "Foo";
            remote.sendText(msg);
            assertTrue(socket.messageLatch.await(1, TimeUnit.SECONDS)); // give remote 1 second to respond
        }
        finally
        {
            assertTrue(socket.closeLatch.await(1, TimeUnit.SECONDS)); // give remote 1 second to acknowledge response
        }
    }

    private void startBundle(BundleContext bundleContext, String symbolicName) throws BundleException
    {
        Bundle bundle = TestOSGiUtil.getBundle(bundleContext, symbolicName);
        assertNotNull("Bundle[" + symbolicName + "] should exist", bundle);
        bundle.start();
    }
}
