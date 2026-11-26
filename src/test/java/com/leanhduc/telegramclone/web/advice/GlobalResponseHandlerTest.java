package com.leanhduc.telegramclone.web.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leanhduc.telegramclone.controller.TestDummyResponseController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalResponseHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        GlobalResponseHandler responseHandler = new GlobalResponseHandler(objectMapper);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestDummyResponseController())
                .setControllerAdvice(responseHandler, exceptionHandler)
                .build();
    }

    @Test
    void testDtoWrappedInApiResponse() throws Exception {
        mockMvc.perform(get("/test/dto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.data.key").value("value"));
    }

    @Test
    void testListWrappedInApiResponse() throws Exception {
        mockMvc.perform(get("/test/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0]").value("item1"));
    }

    @Test
    void testCreatedPreservesStatus201() throws Exception {
        mockMvc.perform(get("/test/created"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.id").value("123"));
    }

    @Test
    void testNoContentHasNoBody() throws Exception {
        mockMvc.perform(get("/test/no-content"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void testVoidOkDoesNotCrash() throws Exception {
        mockMvc.perform(get("/test/void-ok"))
                .andExpect(status().isOk());
    }

    @Test
    void testStringWrappedAsJson() throws Exception {
        mockMvc.perform(get("/test/string"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data").value("hello world"));
    }

    @Test
    void testAlreadyWrappedNotDoubleWrapped() throws Exception {
        mockMvc.perform(get("/test/already-wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Already wrapped"))
                .andExpect(jsonPath("$.data").value("existing"));
    }

    @Test
    void testErrorNotWrappedInApiResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
