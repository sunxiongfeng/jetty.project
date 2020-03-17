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

package org.eclipse.jetty.servlets;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.tools.HttpTester;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.eclipse.jetty.util.StringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrossOriginFilterTest
{
    private ServletTester tester;

    @BeforeEach
    public void init() throws Exception
    {
        tester = new ServletTester();
        tester.start();
    }

    @AfterEach
    public void destroy() throws Exception
    {
        if (tester != null)
            tester.stop();
    }

    @Test
    public void testRequestWithNoOriginArrivesToApplication() throws Exception
    {
        tester.getContext().addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));

        final CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithNonMatchingOrigin() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        String origin = "http://localhost";
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, origin);
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String otherOrigin = StringUtil.replace(origin, "localhost", "127.0.0.1");
        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: " + otherOrigin + "\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, not(is(in(fieldNames))));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, not(is(in(fieldNames))));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithWildcardOrigin() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");
        String origin = "http://foo.example.com";

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), "Vary", is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithMatchingWildcardOrigin() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        String origin = "http://subdomain.example.com";
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "http://*.example.com");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();

        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), "Vary", is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithMatchingWildcardOriginAndMultipleSubdomains() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        String origin = "http://subdomain.subdomain.example.com";
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "http://*.example.com");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), "Vary", is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithMatchingOriginAndWithoutTimingOrigin() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        String origin = "http://localhost";
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, origin);
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.TIMING_ALLOW_ORIGIN_HEADER, not(is(in(fieldNames))));
        assertThat(response.toString(), "Vary", is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithMatchingOriginAndNonMatchingTimingOrigin() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        String origin = "http://localhost";
        String timingOrigin = "http://127.0.0.1";
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, origin);
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_TIMING_ORIGINS_PARAM, timingOrigin);
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        String response = tester.getResponses(request);
        assertTrue(response.contains("HTTP/1.1 200"));
        assertTrue(response.contains(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
        assertTrue(response.contains(CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER));
        assertFalse(response.contains(CrossOriginFilter.TIMING_ALLOW_ORIGIN_HEADER));
        assertTrue(response.contains("Vary"));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithMatchingOriginAndMatchingTimingOrigin() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        String origin = "http://localhost";
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, origin);
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_TIMING_ORIGINS_PARAM, origin);
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: " + origin + "\r\n" +
                "\r\n";
        String response = tester.getResponses(request);
        assertTrue(response.contains("HTTP/1.1 200"));
        assertTrue(response.contains(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER));
        assertTrue(response.contains(CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER));
        assertTrue(response.contains(CrossOriginFilter.TIMING_ALLOW_ORIGIN_HEADER));
        assertTrue(response.contains("Vary"));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithMatchingMultipleOrigins() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        String origin = "http://localhost";
        String otherOrigin = StringUtil.replace(origin, "localhost", "127.0.0.1");
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, origin + "," + otherOrigin);
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                // Use 2 spaces as separator to test that the implementation does not fail
                "Origin: " + otherOrigin + " " + " " + origin + "\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), "Vary", is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithoutCredentials() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "false");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, not(is(in(fieldNames))));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testNonSimpleRequestWithoutPreflight() throws Exception
    {
        // We cannot know if an actual request has performed the preflight before:
        // we'll trust browsers to do it right, so responses to actual requests
        // will contain the CORS response headers.

        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "PUT / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testOptionsRequestButNotPreflight() throws Exception
    {
        // We cannot know if an actual request has performed the preflight before:
        // we'll trust browsers to do it right, so responses to actual requests
        // will contain the CORS response headers.

        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "OPTIONS / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testPreflightWithWildcardCustomHeaders() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "*");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "OPTIONS / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_HEADERS_HEADER + ": X-Foo-Bar\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_METHOD_HEADER + ": GET\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_HEADERS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testPUTRequestWithPreflight() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "PUT");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        // Preflight request
        String request =
            "OPTIONS / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_METHOD_HEADER + ": PUT\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_MAX_AGE_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_HEADERS_HEADER, is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // Preflight request was ok, now make the actual request
        request =
            "PUT / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        rawResponse = tester.getResponses(request);
        response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
    }

    @Test
    public void testDELETERequestWithPreflightAndAllowedCustomHeaders() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,HEAD,POST,PUT,DELETE");
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin,X-Custom");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        // Preflight request
        String request =
            "OPTIONS / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_METHOD_HEADER + ": DELETE\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_HEADERS_HEADER + ": origin,x-custom,x-requested-with\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();

        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_MAX_AGE_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_HEADERS_HEADER, is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // Preflight request was ok, now make the actual request
        request =
            "DELETE / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "X-Custom: value\r\n" +
                "X-Requested-With: local\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        rawResponse = tester.getResponses(request);
        response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, is(in(fieldNames)));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, is(in(fieldNames)));
    }

    @Test
    public void testDELETERequestWithPreflightAndNotAllowedCustomHeaders() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,HEAD,POST,PUT,DELETE");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        // Preflight request
        String request =
            "OPTIONS / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_METHOD_HEADER + ": DELETE\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_HEADERS_HEADER + ": origin,x-custom,x-requested-with\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, not(is(in(fieldNames))));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, not(is(in(fieldNames))));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        // The preflight request failed because header X-Custom is not allowed, actual request not issued
    }

    @Test
    public void testCrossOriginFilterDisabledForWebSocketUpgrade() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: Upgrade\r\n" +
                "Upgrade: WebSocket\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, not(is(in(fieldNames))));
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, not(is(in(fieldNames))));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSimpleRequestWithExposedHeaders() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter("exposedHeaders", "Content-Length");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        String request =
            "GET / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_EXPOSE_HEADERS_HEADER, is(in(fieldNames)));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testChainPreflightRequest() throws Exception
    {
        FilterHolder filterHolder = new FilterHolder(new CrossOriginFilter());
        filterHolder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "PUT");
        filterHolder.setInitParameter(CrossOriginFilter.CHAIN_PREFLIGHT_PARAM, "false");
        tester.getContext().addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));

        CountDownLatch latch = new CountDownLatch(1);
        tester.getContext().addServlet(new ServletHolder(new ResourceServlet(latch)), "/*");

        // Preflight request
        String request =
            "OPTIONS / HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Connection: close\r\n" +
                CrossOriginFilter.ACCESS_CONTROL_REQUEST_METHOD_HEADER + ": PUT\r\n" +
                "Origin: http://localhost\r\n" +
                "\r\n";
        String rawResponse = tester.getResponses(request);
        HttpTester.Response response = HttpTester.parseResponse(rawResponse);

        assertThat(response.toString(), response.getStatus(), is(HttpStatus.OK_200));
        Set<String> fieldNames = response.getFieldNamesCollection();
        assertThat(response.toString(), CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER, is(in(fieldNames)));
        assertFalse(latch.await(1, TimeUnit.SECONDS));
    }

    public static class ResourceServlet extends HttpServlet
    {
        private static final long serialVersionUID = 1L;
        private final CountDownLatch latch;

        public ResourceServlet(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
        {
            latch.countDown();
        }
    }
}
