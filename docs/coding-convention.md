# Coding Convention

## Naming
- Controller: UserController
- Service interface: IUserService
- Service impl: UserService
- Repository: UserRepository
- DTO suffix: Request / Response / Dto

## Package Rule
- dto grouped by feature (auth, contact, user)
- service grouped by feature

## Transaction Rule
- @Transactional only in service layer
- Read operations use readOnly = true

## Exception Handling
- Use custom exceptions and handle them centrally in `GlobalExceptionHandler`.
- For business logic errors, throw `BusinessException` with an appropriate `ErrorCode` enum.
    - Example: `throw new BusinessException(ErrorCode.USER_NOT_FOUND);`
- Define other custom exceptions (e.g., `UnauthorizedException`, `NotFoundException`) by extending `RuntimeException`.
- All exceptions are handled in `GlobalExceptionHandler`, which returns a consistent error response structure containing `timestamp`, `status`, `error`, and `message`.
- Do not catch exceptions in controllers or services unless there is a specific need (e.g., retry, rollback). Let exceptions propagate to the global handler.

## Logging
- Log only in service layer
- No log in controller

## DTO & Mapping
- Controllers must only accept and return DTOs (Data Transfer Objects). Never return entities directly.
- Use **MapStruct** for mapping between entities and DTOs.
- Place mapper interfaces in the `mapper` package and name them following the pattern `{Entity}Mapper` (e.g., `UserMapper`).
- Configure MapStruct with `componentModel = "spring"` (either in `@Mapper` or via build tool) so that mappers can be injected as Spring beans.
- Inject mappers into the service layer; avoid manual field mapping.
- API response bodies must always be DTOs (or collections of DTOs).

## WebSocket & STOMP Convention
- **Endpoint**: WebSocket: `/ws`, App prefix: `/app`, Topic: `/topic`, Queue: `/user/queue`.
- **Destination Naming**: Broadcast: `/topic/{domain}.{action}`, Room: `/topic/{domain}.{resourceId}`, Private: `/user/queue/{domain}`.
- **Message Format**: Envelope with `event: string`, `timestamp: number`, `data: object`.
- **Security**: derive userId from `Principal` (never from client payload).
- **Error Handling**: Use event `ERROR` and codes from `WsErrorCode` enum.