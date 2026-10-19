# Identity Service Auth Flow Guide

This guide documents the basic phone OTP + JWT flow implemented in this service.
You can reuse the same shape in other microservice projects.

## Responsibilities

- API Gateway: routes external traffic to services. It should not create users, verify OTPs, or own auth business logic.
- Identity Service: owns login, users, token issuing, token validation, and protected user APIs.
- Redis: stores short-lived OTPs and refresh tokens.
- PostgreSQL: stores durable user data.

## Endpoint Flow

1. Request OTP through the gateway.

```bash
curl -X POST http://localhost:8080/api/v1/auth/phone/request-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+84901234567"}'
```

For local development the response includes `devOtp`. In production, send the OTP by SMS and remove this field from the response.

2. Verify OTP and receive tokens.

```bash
curl -X POST http://localhost:8080/api/v1/auth/phone/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+84901234567","otp":"123456","displayName":"Nhan"}'
```

The response contains:

- `accessToken`: short-lived token used in the `Authorization` header.
- `refreshToken`: longer-lived token used to obtain a new access token.
- `user`: current user profile.

3. Call a protected user endpoint through the gateway.

```bash
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <accessToken>"
```

4. Refresh tokens.

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

5. Logout.

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

## Reuse Checklist For Other Projects

1. Put route rules in the gateway.
2. Keep auth logic inside identity/auth service.
3. Store OTPs in Redis with a short TTL.
4. Store refresh tokens in Redis with a longer TTL.
5. Make access tokens short-lived.
6. Protect private endpoints with a JWT filter.
7. Test the app through the gateway port, not the internal service port.

## Current Route

The gateway forwards:

- `/api/v1/auth/**` to identity-service.
- `/api/v1/users/**` to identity-service.

## Current Security Rule

- Public: `/api/v1/auth/**`, `/actuator/health`.
- Protected: `/api/v1/users/**` and any other endpoint in identity-service.

## Production Notes

- Replace the local `app.jwt.secret` with a strong environment secret.
- Do not return `devOtp` in production.
- Send OTP through an SMS provider.
- Run Redis before testing OTP or refresh-token flows.
- Keep gateway stateless; each downstream service should still validate its own protected APIs.
