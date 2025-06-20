/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.testing.async;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.util.TimeValue;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class TestHttp1RequestReExecution extends AbstractIntegrationTestBase {

    public TestHttp1RequestReExecution(final URIScheme scheme) {
        this(scheme, false);
    }

    public TestHttp1RequestReExecution(final URIScheme scheme, final boolean useUnixDomainSocket) {
        super(scheme, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD, useUnixDomainSocket);
    }

    @BeforeEach
    void setup() {
        final Resolver<HttpRequest, TimeValue> serviceAvailabilityResolver = new Resolver<HttpRequest, TimeValue>() {

            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public TimeValue resolve(final HttpRequest request) {
                final int n = count.incrementAndGet();
                return n <= 3 ? TimeValue.ofSeconds(1) : null;
            }

        };

        configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(handler -> new ServiceUnavailableAsyncDecorator(handler, serviceAvailabilityResolver)));
    }

    @Test
    void testGiveUpAfterOneRetry() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        configureClient(builder -> builder
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(1, TimeValue.ofSeconds(1))));
        final TestAsyncClient client = startClient();

        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response = future.get();
        assertThat(response, CoreMatchers.notNullValue());
        assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));
    }

    @Test
    void testDoNotGiveUpEasily() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        configureClient(builder -> builder
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(5, TimeValue.ofSeconds(1))));
        final TestAsyncClient client = startClient();

        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response = future.get();
        assertThat(response, CoreMatchers.notNullValue());
        assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
    }

}
