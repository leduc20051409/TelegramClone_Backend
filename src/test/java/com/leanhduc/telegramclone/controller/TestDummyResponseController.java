package com.leanhduc.telegramclone.controller;

import com.leanhduc.telegramclone.dto.common.ApiResponse;
import com.leanhduc.telegramclone.exception.BusinessException;
import com.leanhduc.telegramclone.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Test-only controller placed under {@code com.leanhduc.telegramclone.controller}
 * so {@link com.leanhduc.telegramclone.web.advice.GlobalResponseHandler} intercepts its endpoints.
 */
@RestController
@RequestMapping("/test")
public class TestDummyResponseController {

    @GetMapping("/dto")
    public Map<String, String> getDto() {
        return Map.of("key", "value");
    }

    @GetMapping("/list")
    public List<String> getList() {
        return List.of("item1", "item2");
    }

    @GetMapping("/created")
    public ResponseEntity<Map<String, String>> getCreated() {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", "123"));
    }

    @GetMapping("/no-content")
    public ResponseEntity<Void> getNoContent() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/void-ok")
    public ResponseEntity<Void> getVoidOk() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/string")
    public String getString() {
        return "hello world";
    }

    @GetMapping("/already-wrapped")
    public ApiResponse<String> getAlreadyWrapped() {
        return ApiResponse.success(200, "Already wrapped", "existing");
    }

    @GetMapping("/business-error")
    public Map<String, String> getBusinessError() {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
}
