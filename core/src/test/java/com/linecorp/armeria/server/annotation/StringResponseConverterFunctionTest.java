/*
 * Copyright 2019 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.server.annotation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.internal.FallthroughException;
import com.linecorp.armeria.server.ServiceRequestContext;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class StringResponseConverterFunctionTest {

    private static final ResponseConverterFunction function = new StringResponseConverterFunction();
    private static final ServiceRequestContext ctx = mock(ServiceRequestContext.class);

    private static final HttpHeaders DEFAULT_TRAILING_HEADERS = HttpHeaders.of();

    @BeforeClass
    public static void setup() {
        when(ctx.blockingTaskExecutor()).thenReturn(MoreExecutors.newDirectExecutorService());
    }

    @Test
    public void aggregatedText() throws Exception {
        final ResponseHeaders expectedHeadersWithoutContentLength =
                ResponseHeaders.of(HttpStatus.OK,
                                   HttpHeaderNames.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
        final ResponseHeaders expectedHeadersWithContentLength =
                expectedHeadersWithoutContentLength.withMutations(h -> {
                    h.addInt(HttpHeaderNames.CONTENT_LENGTH, 11);
                });

        final List<String> contents = ImmutableList.of("foo", ",", "bar", ",", "baz");
        for (final Object result : ImmutableList.of(Flux.fromIterable(contents),    // publisher
                                                    contents.stream(),              // stream
                                                    contents)) {                    // iterable
            StepVerifier.create(from(result))
                        .expectNext(result instanceof Iterable ? expectedHeadersWithContentLength
                                                               : expectedHeadersWithoutContentLength)
                        .expectNext(HttpData.of("foo,bar,baz".getBytes()))
                        .expectComplete()
                        .verify();
        }
    }

    @Test
    public void withoutContentType() throws Exception {
        StepVerifier.create(function.convertResponse(ctx, ResponseHeaders.of(HttpStatus.OK),
                                                     "foo", DEFAULT_TRAILING_HEADERS))
                    .expectNext(ResponseHeaders.of(HttpStatus.OK,
                                                   HttpHeaderNames.CONTENT_TYPE,
                                                   MediaType.PLAIN_TEXT_UTF_8,
                                                   HttpHeaderNames.CONTENT_LENGTH, 3))
                    .expectNext(HttpData.ofUtf8("foo"))
                    .expectComplete()
                    .verify();

        assertThatThrownBy(() -> function.convertResponse(
                ctx, ResponseHeaders.of(HttpStatus.OK), ImmutableList.of(), DEFAULT_TRAILING_HEADERS))
                .isInstanceOf(FallthroughException.class);
    }

    @Test
    public void charset() throws Exception {
        final ResponseHeaders headers = ResponseHeaders.of(HttpStatus.OK,
                                                           HttpHeaderNames.CONTENT_TYPE,
                                                           "text/plain; charset=euc-kr");
        StepVerifier.create(function.convertResponse(ctx, headers, "한글", DEFAULT_TRAILING_HEADERS))
                    .expectNext(headers.toBuilder()
                                       .addInt(HttpHeaderNames.CONTENT_LENGTH, 4)
                                       .build())
                    .expectNext(HttpData.of(Charset.forName("euc-kr"), "한글"))
                    .expectComplete()
                    .verify();
    }

    private static HttpResponse from(Object result) throws Exception {
        final ResponseHeaders headers =
                ResponseHeaders.of(HttpStatus.OK,
                                   HttpHeaderNames.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8);
        return function.convertResponse(ctx, headers, result, DEFAULT_TRAILING_HEADERS);
    }
}
