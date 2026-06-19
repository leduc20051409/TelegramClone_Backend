package com.leanhduc.telegramclone.config;

import com.leanhduc.telegramclone.repository.UserRepository;
import com.leanhduc.telegramclone.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:5173")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (StompCommand.CONNECT.equals(accessor != null ? accessor.getCommand() : null)) {
                    String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                        String token = authorizationHeader.substring(7);
                        if(jwtTokenProvider.validateToken(token)) {
                            UUID userId = jwtTokenProvider.getUserIdFromToken(token);
                            String authorities = jwtTokenProvider.getAuthorities(token);

                            List<GrantedAuthority> auth = AuthorityUtils.commaSeparatedStringToAuthorityList(authorities);
                            // Use userId.toString() to ensure the principal is a String, which getName() can safely return
                            Authentication authentication = new UsernamePasswordAuthenticationToken(userId.toString(), null, auth);
                            accessor.setUser(authentication);
                            // Rebuild the message to ensure headers are updated
                            return org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
                        }
                        else {
                            log.error("Invalid JWT token: {}", token);
                            throw new IllegalArgumentException("Invalid JWT token in WebSocket connect");
                        }
                    } else {
                        log.error("Missing Authorization header in WebSocket connect");
                        throw new IllegalArgumentException("Missing Authorization header");
                    }
                } else if (StompCommand.SEND.equals(accessor != null ? accessor.getCommand() : null)) {
                    java.security.Principal principal = accessor.getUser();
                    if (principal != null) {
                        try {
                            UUID userId = UUID.fromString(principal.getName());
                            boolean exists = userRepository.existsById(userId);
                            if (!exists) {
                                log.error("User {} is no longer active. Dropping message.", userId);
                                throw new IllegalArgumentException("User session invalid");
                            }
                        } catch (Exception e) {
                            log.error("Failed to validate principal on SEND", e);
                            throw new IllegalArgumentException("Invalid principal");
                        }
                    }
                }
                return message;
            }
        });
    }
}
