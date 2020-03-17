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

package org.eclipse.jetty.server;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class CheckReverseProxyHeadersTest
{
    @Test
    public void testCheckReverseProxyHeaders() throws Exception
    {
        // Classic ProxyPass from example.com:80 to localhost:8080
        testRequest("Host: localhost:8080\n" +
            "X-Forwarded-For: 10.20.30.40\n" +
            "X-Forwarded-Host: example.com", request ->
            {
                assertEquals("example.com", request.getServerName());
                assertEquals(80, request.getServerPort());
                assertEquals("10.20.30.40", request.getRemoteAddr());
                assertEquals("10.20.30.40", request.getRemoteHost());
                assertEquals("example.com", request.getHeader("Host"));
                assertEquals("http", request.getScheme());
                assertFalse(request.isSecure());
            });

        // IPv6 ProxyPass from example.com:80 to localhost:8080
        testRequest("Host: localhost:8080\n" +
            "X-Forwarded-For: 10.20.30.40\n" +
            "X-Forwarded-Host: [::1]", request ->
            {
                assertEquals("[::1]", request.getServerName());
                assertEquals(80, request.getServerPort());
                assertEquals("10.20.30.40", request.getRemoteAddr());
                assertEquals("10.20.30.40", request.getRemoteHost());
                assertEquals("[::1]", request.getHeader("Host"));
                assertEquals("http", request.getScheme());
                assertFalse(request.isSecure());
            });

        // IPv6 ProxyPass from example.com:80 to localhost:8080
        testRequest("Host: localhost:8080\n" +
            "X-Forwarded-For: 10.20.30.40\n" +
            "X-Forwarded-Host: [::1]:8888", request ->
            {
                assertEquals("[::1]", request.getServerName());
                assertEquals(8888, request.getServerPort());
                assertEquals("10.20.30.40", request.getRemoteAddr());
                assertEquals("10.20.30.40", request.getRemoteHost());
                assertEquals("[::1]:8888", request.getHeader("Host"));
                assertEquals("http", request.getScheme());
                assertFalse(request.isSecure());
            });

        // ProxyPass from example.com:81 to localhost:8080
        testRequest("Host: localhost:8080\n" +
            "X-Forwarded-For: 10.20.30.40\n" +
            "X-Forwarded-Host: example.com:81\n" +
            "X-Forwarded-Server: example.com\n" +
            "X-Forwarded-Proto: https", request ->
            {
                assertEquals("example.com", request.getServerName());
                assertEquals(81, request.getServerPort());
                assertEquals("10.20.30.40", request.getRemoteAddr());
                assertEquals("10.20.30.40", request.getRemoteHost());
                assertEquals("example.com:81", request.getHeader("Host"));
                assertEquals("https", request.getScheme());
                assertTrue(request.isSecure());

            });

        // Multiple ProxyPass from example.com:80 to rp.example.com:82 to localhost:8080
        testRequest("Host: localhost:8080\n" +
            "X-Forwarded-For: 10.20.30.40, 10.0.0.1\n" +
            "X-Forwarded-Host: example.com, rp.example.com:82\n" +
            "X-Forwarded-Server: example.com, rp.example.com\n" +
            "X-Forwarded-Proto: https, http", request ->
            {
                assertEquals("example.com", request.getServerName());
                assertEquals(443, request.getServerPort());
                assertEquals("10.20.30.40", request.getRemoteAddr());
                assertEquals("10.20.30.40", request.getRemoteHost());
                assertEquals("example.com", request.getHeader("Host"));
                assertEquals("https", request.getScheme());
                assertTrue(request.isSecure());
            });
    }

    private void testRequest(String headers, RequestValidator requestValidator) throws Exception
    {
        Server server = new Server();
        // Activate reverse proxy headers checking
        HttpConnectionFactory http = new HttpConnectionFactory();
        http.getHttpConfiguration().addCustomizer(new ForwardedRequestCustomizer());

        LocalConnector connector = new LocalConnector(server, http);

        server.setConnectors(new Connector[]{connector});
        ValidationHandler validationHandler = new ValidationHandler(requestValidator);
        server.setHandler(validationHandler);

        try
        {
            server.start();
            connector.getResponse("GET / HTTP/1.1\r\n" + "Connection: close\r\n" + headers + "\r\n\r\n");
            Error error = validationHandler.getError();

            if (error != null)
            {
                throw error;
            }
        }
        finally
        {
            server.stop();
        }
    }

    /**
     * Interface for validate a wrapped request.
     */
    private static interface RequestValidator
    {
        /**
         * Validate the current request.
         *
         * @param request the request.
         */
        void validate(HttpServletRequest request);
    }

    /**
     * Handler for validation.
     */
    private static class ValidationHandler extends AbstractHandler
    {
        private final RequestValidator _requestValidator;
        private Error _error;

        private ValidationHandler(RequestValidator requestValidator)
        {
            _requestValidator = requestValidator;
        }

        /**
         * Retrieve the validation error.
         *
         * @return the validation error or <code>null</code> if there was no error.
         */
        public Error getError()
        {
            return _error;
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            try
            {
                _requestValidator.validate(request);
            }
            catch (Error e)
            {
                _error = e;
            }
            catch (Throwable e)
            {
                _error = new Error(e);
            }
        }
    }
}
