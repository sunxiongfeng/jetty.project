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

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.MultiPartContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;

/**
 * Pax-Exam to make sure the jetty-osgi-boot can be started along with the
 * httpservice web-bundle. Then make sure we can deploy an OSGi service on the
 * top of this.
 */
@RunWith(PaxExam.class)

public class TestJettyOSGiBootWithAnnotations
{
    private static final String LOG_LEVEL = "WARN";

    @Inject
    BundleContext bundleContext = null;

    @Configuration
    public static Option[] configure()
    {
        ArrayList<Option> options = new ArrayList<>();
        options.add(TestOSGiUtil.optionalRemoteDebug());
        options.add(CoreOptions.junitBundles());
        options.addAll(TestOSGiUtil.configureJettyHomeAndPort(false, "jetty-http-boot-with-annotations.xml"));
        options.add(CoreOptions.bootDelegationPackages("org.xml.sax", "org.xml.*", "org.w3c.*", "javax.sql.*", "javax.xml.*", "javax.activation.*"));
        options.add(CoreOptions.systemPackages("com.sun.org.apache.xalan.internal.res", "com.sun.org.apache.xml.internal.utils",
            "com.sun.org.apache.xml.internal.utils", "com.sun.org.apache.xpath.internal",
            "com.sun.org.apache.xpath.internal.jaxp", "com.sun.org.apache.xpath.internal.objects"));

        options.addAll(TestOSGiUtil.coreJettyDependencies());
        // TODO uncomment and update the following once 9.4.19 is released with a fix for #3726
        // options.add(mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-util").version("9.4.19.v????????").noStart());
        options.add(mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-java-client").versionAsInProject().start());
        options.add(mavenBundle().groupId("org.eclipse.jetty").artifactId("jetty-alpn-client").versionAsInProject().start());
        options.add(systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value(LOG_LEVEL));
        options.add(systemProperty("org.eclipse.jetty.LEVEL").value(LOG_LEVEL));
        options.addAll(jspDependencies());
        options.addAll(annotationDependencies());
        options.add(mavenBundle().groupId("org.eclipse.jetty.osgi").artifactId("test-jetty-osgi-fragment").versionAsInProject().noStart());
        return options.toArray(new Option[0]);
    }

    public static List<Option> jspDependencies()
    {
        return TestOSGiUtil.jspDependencies();
    }

    public static List<Option> annotationDependencies()
    {
        List<Option> res = new ArrayList<>();
        res.add(mavenBundle().groupId("com.sun.activation").artifactId("javax.activation").version("1.2.0").noStart());
        res.add(mavenBundle().groupId("org.eclipse.jetty.orbit").artifactId("javax.mail.glassfish").version("1.4.1.v201005082020").noStart());
        res.add(mavenBundle().groupId("org.eclipse.jetty.tests").artifactId("test-container-initializer").versionAsInProject());
        res.add(mavenBundle().groupId("org.eclipse.jetty.tests").artifactId("test-mock-resources").versionAsInProject());
        //test webapp bundle
        res.add(mavenBundle().groupId("org.eclipse.jetty.tests").artifactId("test-spec-webapp").classifier("webbundle").versionAsInProject());
        return res;
    }

    public void assertAllBundlesActiveOrResolved()
    {
        TestOSGiUtil.debugBundles(bundleContext);
        TestOSGiUtil.assertAllBundlesActiveOrResolved(bundleContext);
    }

    @Test
    public void testIndex() throws Exception
    {
        if (Boolean.getBoolean(TestOSGiUtil.BUNDLE_DEBUG))
            assertAllBundlesActiveOrResolved();

        HttpClient client = new HttpClient();
        try
        {
            client.start();
            String port = System.getProperty("boot.annotations.port");
            assertNotNull(port);

            ContentResponse response = client.GET("http://127.0.0.1:" + port + "/index.html");
            assertEquals("Response status code", HttpStatus.OK_200, response.getStatus());

            String content = response.getContentAsString();
            TestOSGiUtil.assertContains("Response contents", content, "Test WebApp");

            Request req = client.POST("http://127.0.0.1:" + port + "/test");
            response = req.send();
            assertEquals("Response status code", HttpStatus.OK_200, response.getStatus());
            content = response.getContentAsString();
            TestOSGiUtil.assertContains("Response contents", content,
                "<p><b>Result: <span class=\"pass\">PASS</span></p>");

            response = client.GET("http://127.0.0.1:" + port + "/frag.html");
            assertEquals("Response status code", HttpStatus.OK_200, response.getStatus());
            content = response.getContentAsString();
            TestOSGiUtil.assertContains("Response contents", content, "<h1>FRAGMENT</h1>");
            MultiPartContentProvider multiPart = new MultiPartContentProvider();
            multiPart.addFieldPart("field", new StringContentProvider("foo"), null);
            response = client.newRequest("http://127.0.0.1:" + port + "/multi").method("POST")
                .content(multiPart).send();
            assertEquals(HttpStatus.OK_200, response.getStatus());
        }
        finally
        {
            client.stop();
        }
    }
}
