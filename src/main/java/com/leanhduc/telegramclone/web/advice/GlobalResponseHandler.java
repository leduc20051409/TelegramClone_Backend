package com.leanhduc.telegramclone.web.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leanhduc.telegramclone.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@Slf4j
@RestControllerAdvice(basePackages = "com.leanhduc.telegramclone.controller")
@RequiredArgsConstructor
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Exclude Void and ResponseEntity<Void>
        Class<?> paramType = returnType.getParameterType();
        if (paramType.equals(Void.TYPE) || paramType.equals(Void.class)) {
            return false;
        }

        if (ResponseEntity.class.isAssignableFrom(paramType)) {
            Type genericType = returnType.getGenericParameterType();
            if (genericType instanceof ParameterizedType parameterizedType) {
                Type[] typeArgs = parameterizedType.getActualTypeArguments();
                if (typeArgs.length > 0 && (typeArgs[0].equals(Void.class) || typeArgs[0].equals(Void.TYPE))) {
                    return false;
                }
            }
        }

        // Exclude binary and resource converters defensively
        if (ByteArrayHttpMessageConverter.class.isAssignableFrom(converterType)
                || ResourceHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }

        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        // 1. Get actual HTTP status code
        int actualStatus = 200;
        if (response instanceof ServletServerHttpResponse servletResponse) {
            actualStatus = servletResponse.getServletResponse().getStatus();
        }

        // 2. Skip non-2xx responses (preserves error contract from GlobalExceptionHandler)
        if (actualStatus < 200 || actualStatus >= 300) {
            return body;
        }

        // 3. Skip 204 No Content (must not have body per RFC 7231)
        if (actualStatus == 204) {
            return body;
        }

        // 4. Skip already wrapped ApiResponse
        if (body instanceof ApiResponse) {
            return body;
        }

        // 5. Special handling for String return types to avoid ClassCastException in StringHttpMessageConverter
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType) || body instanceof String) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(ApiResponse.success(actualStatus, "Success", body));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize ApiResponse string: {}", e.getMessage());
                return body;
            }
        }

        // 6. Wrap standard DTOs / Lists / Objects
        return ApiResponse.success(actualStatus, "Success", body);
    }
}
