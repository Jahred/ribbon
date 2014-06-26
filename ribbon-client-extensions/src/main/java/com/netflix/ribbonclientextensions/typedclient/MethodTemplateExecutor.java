/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.ribbonclientextensions.typedclient;

import com.netflix.ribbonclientextensions.RibbonRequest;
import com.netflix.ribbonclientextensions.http.HttpRequestBuilder;
import com.netflix.ribbonclientextensions.http.HttpRequestTemplate;
import com.netflix.ribbonclientextensions.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.reactivex.netty.protocol.http.client.RawContentSource;
import io.reactivex.netty.protocol.http.client.RawContentSource.SingletonRawSource;
import io.reactivex.netty.serialization.ContentTransformer;
import io.reactivex.netty.serialization.StringTransformer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class MethodTemplateExecutor {

    private static final ContentTransformer<ByteBuf> BYTE_BUF_TRANSFORMER = new ContentTransformer<ByteBuf>() {
        @Override
        public ByteBuf transform(ByteBuf toTransform, ByteBufAllocator byteBufAllocator) {
            return toTransform;
        }
    };

    private static final StringTransformer STRING_TRANSFORMER = new StringTransformer();

    private final MethodTemplate methodTemplate;

    public MethodTemplateExecutor(MethodTemplate methodTemplate) {
        this.methodTemplate = methodTemplate;
    }

    @SuppressWarnings("unchecked")
    public <O> RibbonRequest<O> executeFromTemplate(HttpResourceGroup httpResourceGroup, Object[] args) {
        HttpRequestTemplate<?> httpRequestTemplate = createBaseHttpRequestTemplate(httpResourceGroup);
        withRequestUriBase(httpRequestTemplate);
        withHystrixHandlers(httpRequestTemplate);
        withCache(httpRequestTemplate);

        HttpRequestBuilder<?> requestBuilder = httpRequestTemplate.requestBuilder();
        withParameters(requestBuilder, args);
        withContent(requestBuilder, args);

        return (RibbonRequest<O>) requestBuilder.build();
    }

    private HttpRequestTemplate<?> createBaseHttpRequestTemplate(HttpResourceGroup httpResourceGroup) {
        HttpRequestTemplate<?> httpRequestTemplate;
        if (ByteBuf.class.isAssignableFrom(methodTemplate.getResultType())) {
            httpRequestTemplate = httpResourceGroup.newRequestTemplate(methodTemplate.getTemplateName());
        } else {
            httpRequestTemplate = httpResourceGroup.newRequestTemplate(methodTemplate.getTemplateName(), methodTemplate.getResultType());

        }
        return httpRequestTemplate;
    }

    private void withRequestUriBase(HttpRequestTemplate<?> httpRequestTemplate) {
        httpRequestTemplate.withMethod(methodTemplate.getHttpMethod().name());
        if (methodTemplate.getPath() != null) {
            httpRequestTemplate.withUriTemplate(methodTemplate.getPath());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void withHystrixHandlers(HttpRequestTemplate httpRequestTemplate) {
        if(methodTemplate.getHystrixFallbackHandler() != null) {
            httpRequestTemplate.withFallbackProvider(methodTemplate.getHystrixFallbackHandler());
            httpRequestTemplate.withResponseValidator(methodTemplate.getHystrixResponseValidator());
        }
    }

    private void withCache(HttpRequestTemplate<?> httpRequestTemplate) {
        if(methodTemplate.getCacheKey() != null) {
            httpRequestTemplate.withRequestCacheKey(methodTemplate.getCacheKey());
        }
    }

    private void withParameters(HttpRequestBuilder<?> requestBuilder, Object[] args) {
        int length = methodTemplate.getParamSize();
        for (int i = 0; i < length; i++) {
            String name = methodTemplate.getParamName(i);
            Object value = args[methodTemplate.getParamPosition(i)];
            requestBuilder.withRequestProperty(name, value);
        }
    }

    @SuppressWarnings("unchecked")
    private void withContent(HttpRequestBuilder<?> requestBuilder, Object[] args) {
        if (methodTemplate.getContentArgPosition() < 0) {
            return;
        }
        Object contentValue = args[methodTemplate.getContentArgPosition()];
        if (contentValue instanceof RawContentSource) {
            requestBuilder.withRawContentSource((RawContentSource<?>) contentValue);
        } else if (contentValue instanceof ByteBuf) {
            requestBuilder.withRawContentSource(new SingletonRawSource(contentValue, BYTE_BUF_TRANSFORMER));
        } else if (contentValue instanceof String) {
            requestBuilder.withRawContentSource(new SingletonRawSource(contentValue, STRING_TRANSFORMER));
        } else {
            ContentTransformer<?> contentTransformer = Utils.newInstance(methodTemplate.getContentTransformerClass());
            requestBuilder.withRawContentSource(new SingletonRawSource(contentValue, contentTransformer));
        }
    }

    public static Map<Method, MethodTemplateExecutor> from(Class<?> clientInterface) {
        Map<Method, MethodTemplateExecutor> tgm = new HashMap<Method, MethodTemplateExecutor>();
        for (MethodTemplate mt : MethodTemplate.from(clientInterface)) {
            tgm.put(mt.getMethod(), new MethodTemplateExecutor(mt));
        }
        return tgm;
    }
}
