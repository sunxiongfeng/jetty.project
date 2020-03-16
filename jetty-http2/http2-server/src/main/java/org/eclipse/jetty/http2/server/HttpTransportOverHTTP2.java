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

package org.eclipse.jetty.http2.server;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.IStream;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.PushPromiseFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpTransport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpTransportOverHTTP2 implements HttpTransport
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpTransportOverHTTP2.class);

    private final AtomicBoolean commit = new AtomicBoolean();
    private final TransportCallback transportCallback = new TransportCallback();
    private final Connector connector;
    private final HTTP2ServerConnection connection;
    private IStream stream;
    private MetaData.Response metaData;

    public HttpTransportOverHTTP2(Connector connector, HTTP2ServerConnection connection)
    {
        this.connector = connector;
        this.connection = connection;
    }

    public IStream getStream()
    {
        return stream;
    }

    public void setStream(IStream stream)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("{} setStream {}", this, stream.getId());
        this.stream = stream;
    }

    public void recycle()
    {
        this.stream = null;
        commit.set(false);
    }

    @Override
    public void send(MetaData.Request request, MetaData.Response response, ByteBuffer content, boolean lastContent, Callback callback)
    {
        boolean isHeadRequest = HttpMethod.HEAD.is(request.getMethod());
        boolean hasContent = BufferUtil.hasContent(content) && !isHeadRequest;
        if (response != null)
        {
            metaData = response;
            int status = response.getStatus();
            boolean interimResponse = status == HttpStatus.CONTINUE_100 || status == HttpStatus.PROCESSING_102;
            if (interimResponse)
            {
                // Must not commit interim responses.
                if (hasContent)
                {
                    callback.failed(new IllegalStateException("Interim response cannot have content"));
                }
                else
                {
                    if (transportCallback.start(callback, false))
                        sendHeadersFrame(response, false, transportCallback);
                }
            }
            else
            {
                if (commit.compareAndSet(false, true))
                {
                    if (hasContent)
                    {
                        Callback commitCallback = new Callback.Nested(callback)
                        {
                            @Override
                            public void succeeded()
                            {
                                if (lastContent)
                                {
                                    HttpFields trailers = retrieveTrailers();
                                    if (trailers != null)
                                    {
                                        if (transportCallback.start(new SendTrailers(getCallback(), trailers), false))
                                            sendDataFrame(content, true, false, transportCallback);
                                    }
                                    else
                                    {
                                        if (transportCallback.start(getCallback(), false))
                                            sendDataFrame(content, true, true, transportCallback);
                                    }
                                }
                                else
                                {
                                    if (transportCallback.start(getCallback(), false))
                                        sendDataFrame(content, false, false, transportCallback);
                                }
                            }
                        };
                        if (transportCallback.start(commitCallback, true))
                            sendHeadersFrame(response, false, transportCallback);
                    }
                    else
                    {
                        if (lastContent)
                        {
                            if (isTunnel(request, response))
                            {
                                if (transportCallback.start(callback, true))
                                    sendHeadersFrame(response, false, transportCallback);
                            }
                            else
                            {
                                HttpFields trailers = retrieveTrailers();
                                if (trailers != null)
                                {
                                    if (transportCallback.start(new SendTrailers(callback, trailers), true))
                                        sendHeadersFrame(response, false, transportCallback);
                                }
                                else
                                {
                                    if (transportCallback.start(callback, true))
                                        sendHeadersFrame(response, true, transportCallback);
                                }
                            }
                        }
                        else
                        {
                            if (transportCallback.start(callback, true))
                                sendHeadersFrame(response, false, transportCallback);
                        }
                    }
                }
                else
                {
                    callback.failed(new IllegalStateException("committed"));
                }
            }
        }
        else
        {
            if (hasContent || (lastContent && !isTunnel(request, metaData)))
            {
                if (lastContent)
                {
                    HttpFields trailers = retrieveTrailers();
                    if (trailers != null)
                    {
                        SendTrailers sendTrailers = new SendTrailers(callback, trailers);
                        if (hasContent)
                        {
                            if (transportCallback.start(sendTrailers, false))
                                sendDataFrame(content, true, false, transportCallback);
                        }
                        else
                        {
                            sendTrailers.succeeded();
                        }
                    }
                    else
                    {
                        if (transportCallback.start(callback, false))
                            sendDataFrame(content, true, true, transportCallback);
                    }
                }
                else
                {
                    if (transportCallback.start(callback, false))
                        sendDataFrame(content, false, false, transportCallback);
                }
            }
            else
            {
                callback.succeeded();
            }
        }
    }

    private HttpFields retrieveTrailers()
    {
        Supplier<HttpFields> supplier = metaData.getTrailerSupplier();
        if (supplier == null)
            return null;
        HttpFields trailers = supplier.get();
        if (trailers == null)
            return null;
        return trailers.size() == 0 ? null : trailers;
    }

    private boolean isTunnel(MetaData.Request request, MetaData.Response response)
    {
        return HttpMethod.CONNECT.is(request.getMethod()) && response.getStatus() == HttpStatus.OK_200;
    }

    @Override
    public boolean isPushSupported()
    {
        return stream.getSession().isPushEnabled();
    }

    @Override
    public void push(final MetaData.Request request)
    {
        if (!stream.getSession().isPushEnabled())
        {
            if (LOG.isDebugEnabled())
                LOG.debug("HTTP/2 Push disabled for {}", request);
            return;
        }

        if (LOG.isDebugEnabled())
            LOG.debug("HTTP/2 Push {}", request);

        stream.push(new PushPromiseFrame(stream.getId(), 0, request), new Promise<>()
        {
            @Override
            public void succeeded(Stream pushStream)
            {
                connection.push(connector, (IStream)pushStream, request);
            }

            @Override
            public void failed(Throwable x)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Could not push " + request, x);
            }
        }, new Stream.Listener.Adapter()); // TODO: handle reset from the client ?
    }

    private void sendHeadersFrame(MetaData.Response info, boolean endStream, Callback callback)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("HTTP2 Response #{}/{}:{}{} {}{}{}",
                stream.getId(), Integer.toHexString(stream.getSession().hashCode()),
                System.lineSeparator(), HttpVersion.HTTP_2, info.getStatus(),
                System.lineSeparator(), info.getFields());
        }

        HeadersFrame frame = new HeadersFrame(stream.getId(), info, null, endStream);
        stream.headers(frame, callback);
    }

    private void sendDataFrame(ByteBuffer content, boolean lastContent, boolean endStream, Callback callback)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("HTTP2 Response #{}/{}: {} content bytes{}",
                stream.getId(), Integer.toHexString(stream.getSession().hashCode()),
                content.remaining(), lastContent ? " (last chunk)" : "");
        }
        DataFrame frame = new DataFrame(stream.getId(), content, endStream);
        stream.data(frame, callback);
    }

    private void sendTrailersFrame(MetaData metaData, Callback callback)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug("HTTP2 Response #{}/{}: trailers",
                stream.getId(), Integer.toHexString(stream.getSession().hashCode()));
        }

        HeadersFrame frame = new HeadersFrame(stream.getId(), metaData, null, true);
        stream.headers(frame, callback);
    }

    public void onStreamFailure(Throwable failure)
    {
        transportCallback.failed(failure);
    }

    public boolean onStreamTimeout(Throwable failure)
    {
        return transportCallback.onIdleTimeout(failure);
    }

    boolean prepareUpgrade()
    {
        HttpChannelOverHTTP2 channel = (HttpChannelOverHTTP2)stream.getAttachment();
        Request request = channel.getRequest();
        if (request.getHttpInput().hasContent())
            return channel.sendErrorOrAbort("Unexpected content in CONNECT request");
        Connection connection = (Connection)request.getAttribute(UPGRADE_CONNECTION_ATTRIBUTE);
        EndPoint endPoint = connection.getEndPoint();
        endPoint.upgrade(connection);
        stream.setAttachment(endPoint);
        // Only now that we have switched the attachment,
        // we can demand DATA frames to process them.
        stream.demand(1);

        if (LOG.isDebugEnabled())
            LOG.debug("Upgrading to {}", connection);

        return false;
    }

    @Override
    public void onCompleted()
    {
        Object attachment = stream.getAttachment();
        if (attachment instanceof HttpChannelOverHTTP2)
        {
            // TODO: we used to "fake" a 101 response to upgrade the endpoint
            //  but we don't anymore, so this code should be deleted.
            HttpChannelOverHTTP2 channel = (HttpChannelOverHTTP2)attachment;
            if (channel.getResponse().getStatus() == HttpStatus.SWITCHING_PROTOCOLS_101)
            {
                Connection connection = (Connection)channel.getRequest().getAttribute(UPGRADE_CONNECTION_ATTRIBUTE);
                EndPoint endPoint = connection.getEndPoint();
                // TODO: check that endPoint implements HTTP2Channel.
                if (LOG.isDebugEnabled())
                    LOG.debug("Tunnelling DATA frames through {}", endPoint);
                endPoint.upgrade(connection);
                stream.setAttachment(endPoint);
                return;
            }

            // If the stream is not closed, it is still reading the request content.
            // Send a reset to the other end so that it stops sending data.
            if (!stream.isClosed())
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("HTTP2 Response #{}: unconsumed request content, resetting stream", stream.getId());
                stream.reset(new ResetFrame(stream.getId(), ErrorCode.CANCEL_STREAM_ERROR.code), Callback.NOOP);
            }

            // Consume the existing queued data frames to
            // avoid stalling the session flow control.
            channel.consumeInput();
        }
    }

    @Override
    public void abort(Throwable failure)
    {
        IStream stream = this.stream;
        if (LOG.isDebugEnabled())
            LOG.debug("HTTP2 Response #{}/{} aborted", stream == null ? -1 : stream.getId(),
                stream == null ? -1 : Integer.toHexString(stream.getSession().hashCode()));
        if (stream != null)
            stream.reset(new ResetFrame(stream.getId(), ErrorCode.CANCEL_STREAM_ERROR.code), Callback.NOOP);
    }

    private class TransportCallback implements Callback
    {
        private State state = State.IDLE;
        private Callback callback;
        private Throwable failure;
        private boolean commit;

        public boolean start(Callback callback, boolean commit)
        {
            State state;
            Throwable failure;
            synchronized (this)
            {
                state = this.state;
                failure = this.failure;
                if (state == State.IDLE)
                {
                    this.state = State.WRITING;
                    this.callback = callback;
                    this.commit = commit;
                    return true;
                }
            }
            if (failure == null)
                failure = new IllegalStateException("Invalid transport state: " + state);
            callback.failed(failure);
            return false;
        }

        @Override
        public void succeeded()
        {
            boolean commit;
            Callback callback = null;
            synchronized (this)
            {
                commit = this.commit;
                if (state == State.WRITING)
                {
                    this.state = State.IDLE;
                    callback = this.callback;
                    this.callback = null;
                    this.commit = false;
                }
            }
            if (LOG.isDebugEnabled())
                LOG.debug("HTTP2 Response #{}/{} {} {}",
                    stream.getId(), Integer.toHexString(stream.getSession().hashCode()),
                    commit ? "commit" : "flush",
                    callback == null ? "failure" : "success");
            if (callback != null)
                callback.succeeded();
        }

        @Override
        public void failed(Throwable failure)
        {
            boolean commit;
            Callback callback;
            synchronized (this)
            {
                commit = this.commit;
                this.state = State.FAILED;
                callback = this.callback;
                this.callback = null;
                this.failure = failure;
            }
            if (LOG.isDebugEnabled())
                LOG.debug(String.format("HTTP2 Response #%d/%h %s %s", stream.getId(), stream.getSession(),
                    commit ? "commit" : "flush", callback == null ? "ignored" : "failed"), failure);
            if (callback != null)
                callback.failed(failure);
        }

        private boolean onIdleTimeout(Throwable failure)
        {
            boolean result;
            Callback callback = null;
            synchronized (this)
            {
                // Ignore idle timeouts if not writing,
                // as the application may be suspended.
                result = state == State.WRITING;
                if (result)
                {
                    this.state = State.TIMEOUT;
                    callback = this.callback;
                    this.callback = null;
                    this.failure = failure;
                }
            }
            if (LOG.isDebugEnabled())
                LOG.debug(String.format("HTTP2 Response #%d/%h idle timeout %s", stream.getId(), stream.getSession(), result ? "expired" : "ignored"), failure);
            if (result)
                callback.failed(failure);
            return result;
        }

        @Override
        public InvocationType getInvocationType()
        {
            Callback callback;
            synchronized (this)
            {
                callback = this.callback;
            }
            return callback != null ? callback.getInvocationType() : Callback.super.getInvocationType();
        }
    }

    private enum State
    {
        IDLE, WRITING, FAILED, TIMEOUT
    }

    private class SendTrailers extends Callback.Nested
    {
        private final HttpFields trailers;

        private SendTrailers(Callback callback, HttpFields trailers)
        {
            super(callback);
            this.trailers = trailers;
        }

        @Override
        public void succeeded()
        {
            if (transportCallback.start(getCallback(), false))
                sendTrailersFrame(new MetaData(HttpVersion.HTTP_2, trailers), transportCallback);
        }
    }
}
