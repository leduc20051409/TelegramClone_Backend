package com.leanhduc.telegramclone.controller.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leanhduc.telegramclone.config.CustomUserDetailsService;
import com.leanhduc.telegramclone.dto.message.ChatMessageRequest;
import com.leanhduc.telegramclone.dto.message.ChatMessageResponse;
import com.leanhduc.telegramclone.dto.websocket.WsEnvelope;
import com.leanhduc.telegramclone.repository.*;
import com.leanhduc.telegramclone.security.JwtTokenProvider;
import com.leanhduc.telegramclone.service.Presence.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "logging.level.org.springframework.web.socket=DEBUG",
                "logging.level.org.springframework.messaging.simp.stomp=DEBUG"
        }
)
@EnableAutoConfiguration (exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class
})
public class ChatControllerIntegrationTest {

    @LocalServerPort
    private int port;

    // Inject ObjectMapper của Spring Boot (đã được cấu hình sẵn JavaTimeModule để parse Instant)
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private ContactRepository contactRepository;

    @MockitoBean
    private ConversationRepository conversationRepository;

    @MockitoBean
    private ConversationMemberRepository conversationMemberRepository;

    @MockitoBean
    private MessageRepository messageRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private PresenceService presenceService;

    private WebSocketStompClient stompClient;
    private final UUID mockUserId = UUID.randomUUID();
    private final String MOCK_TOKEN = "mock-jwt-token";

    @BeforeEach
    void setup() {
        // Cấu hình WebSocket Client hỗ trợ SockJS
        List<Transport> transports = Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);

        stompClient = new WebSocketStompClient(sockJsClient);

        // Gắn ObjectMapper chuẩn vào MessageConverter để parse được Record và Instant
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        // Giả lập hành vi của JwtTokenProvider
        when(jwtTokenProvider.validateToken(MOCK_TOKEN)).thenReturn(Boolean.valueOf(true));
        when(jwtTokenProvider.getUserIdFromToken(MOCK_TOKEN)).thenReturn(mockUserId);
        when(jwtTokenProvider.getAuthorities(MOCK_TOKEN)).thenReturn("ROLE_USER");
    }

    @Test
    void shouldSendMessageAndReceiveBroadcast() throws ExecutionException, InterruptedException, TimeoutException {
        // 1. Chuẩn bị kết nối WebSocket tới endpoint "/ws"
        String wsUrl = "ws://localhost:" + port + "/ws";

        // Nhồi token vào header STOMP (mô phỏng quá trình CONNECT của Frontend)
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + MOCK_TOKEN);

        // 2. Kết nối tới server
        StompSession stompSession = stompClient
                .connectAsync(wsUrl, new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        // 3. Chuẩn bị CompletableFuture để hứng message trả về từ server
        CompletableFuture<WsEnvelope<ChatMessageResponse>> resultKeeper = new CompletableFuture<>();

        // 4. Subscribe vào kênh của user
        stompSession.subscribe("/user/queue/chat", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                // Yêu cầu Spring trả về byte thô thay vì cố gắng tự ép kiểu Generic
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    byte[] rawBytes = (byte[]) payload;
                    // Tự tay dùng ObjectMapper để ép kiểu dữ liệu sang đúng WsEnvelope<ChatMessageResponse>
                    WsEnvelope<ChatMessageResponse> envelope = objectMapper.readValue(
                            rawBytes,
                            new com.fasterxml.jackson.core.type.TypeReference<WsEnvelope<ChatMessageResponse>>() {}
                    );
                    resultKeeper.complete(envelope);
                } catch (Exception e) {
                    resultKeeper.completeExceptionally(e);
                }
            }
        });

        // 5. Chuẩn bị Payload gửi đi bằng Java Record
        UUID mockConversationId = UUID.randomUUID();
        String mockBody = "Xin chào từ Integration Test!";
        ChatMessageRequest request = new ChatMessageRequest(mockConversationId, mockBody, List.of());

        // 6. Gửi message lên server qua endpoint "/app/chat.send"
        stompSession.send("/app/chat.send", request);

        // 7. Chờ tối đa 5 giây và Assert kết quả
        WsEnvelope<ChatMessageResponse> receivedEnvelope = resultKeeper.get(5, TimeUnit.SECONDS);

        // Kiểm tra WsEnvelope
        assertNotNull(receivedEnvelope);
        assertEquals("NEW_MESSAGE", receivedEnvelope.event());

        // Kiểm tra dữ liệu bên trong (ChatMessageResponse)
        ChatMessageResponse receivedMessage = receivedEnvelope.data();

        System.out.println("\n=========================================================");
        System.out.println("💌 TIN NHẮN SERVER BROADCAST VỀ CLIENT THÀNH CÔNG:");
        System.out.println("Nội dung: " + receivedMessage.message());
        System.out.println("=========================================================\n");

        assertNotNull(receivedMessage);
        assertEquals(mockUserId, receivedMessage.senderId());
        assertEquals(mockConversationId, receivedMessage.conversationId());
        assertEquals(mockBody, receivedMessage.message());
        assertNotNull(receivedMessage.createdAt()); // Đảm bảo Instant được parse thành công
    }
}