# RBAC API Contract

## Overview

This document defines the RBAC (Role-Based Access Control) API endpoints for the Nocteon API. It covers role/permission management, user role changes, token freshness, and related endpoints.

## Authentication and Authorization

- All dashboard endpoints (`/api/dashboard/**`) require `.authenticated()` in addition to `@PreAuthorize` annotations.
- Public storefront endpoints (`/api/categories`, `/api/origins`, etc.) are permitAll.
- Role-based authorities use the format `resource:action` (e.g., `user:read`, `product:update`).
- New permissions `role:read` and `role:manage` have been added to the Permission enum.

## Endpoints

### B1 — Change role endpoint

**PUT** `/api/dashboard/users/{id}/role`

- **Request body**: `{"role":"ADMIN"}`
- **Authority**: `user:change_role`
- **Responses**:
  - `200` — Role changed successfully. Response includes the updated user plus `{granted:[slug], revoked:[slug]}` diff of effective permissions.
  - `403` — `CANNOT_CHANGE_OWN_ROLE` — Caller is attempting to change their own role.
  - `409` — `LAST_ADMIN_REQUIRED` — The change would leave zero admins in the system. Race condition prevented with pessimistic locking.
  - `404` — Unknown user.
  - `422` — `UNKNOWN_ROLE` — The role specified in the request body is not a valid role.

### B2 — Role read endpoints

**GET** `/api/dashboard/roles`

- **Authority**: `user:read`
- **Response**: `[{name, description, system, userCount, permissionCount}]`
- **Description**: Returns all roles with user count and permission count. Uses a single grouped query to avoid N+1.

**GET** `/api/dashboard/roles/{name}`

- **Authority**: `user:read`
- **Path variable**: `name` — Role name (e.g., "ADMIN", "MODERATOR", "CUSTOMER")
- **Response**: `{name, description, system, permissions:[slug]}`
- **Description**: Returns role details including all permission slugs.

### B3 — Role management

**POST** `/api/dashboard/roles`

- **Authority**: `role:manage`
- **Request body**: `{name, description, cloneFrom?}`
- **Response**: `201 Created`
- **Validation**:
  - Name must match regex `^[A-Z][A-Z0-9_]{2,31}$`
  - Name must be unique (409 `ROLE_EXISTS` if duplicate)
- **Response**: Created role details

**PUT** `/api/dashboard/roles/{name}`

- **Authority**: `role:manage`
- **Path variable**: `name` — Role name
- **Request body**: `{description, permissions:[slug]}`
- **Validation**:
  - Every permission slug must exist (422 `UNKNOWN_PERMISSION` with the list)
  - System roles (`ADMIN`, `CUSTOMER`): description editable, permissions READ-ONLY (409 `ROLE_IMMUTABLE`)
- **Response**: Updated role details

**DELETE** `/api/dashboard/roles/{name}`

- **Authority**: `role:manage`
- **Path variable**: `name` — Role name
- **Validation**:
  - 409 `ROLE_IN_USE` when `userCount > 0`
  - 409 `ROLE_IMMUTABLE` for system roles (`ADMIN`, `CUSTOMER`)
- **Response**: No content

### B4 — Token freshness (stale authorities)

**Token version & auth version mechanism**:

- Users have `tokenVersion` (int, incremented on logout all devices / role change) and `authVersion` (int, incremented on role/permission changes).
- Access tokens include both `tokenVersion` and `authVersion` claims.
- The JWT filter compares both claims with the DB values.
- Mismatch → 401 `TOKEN_STALE`. The refresh endpoint issues new tokens with fresh authorities.

**Error contract**:

- `401` — `TOKEN_STALE` — Token has become stale due to a role or permission change. Please refresh your token.
- Message key: `error.token.stale` (English) / `error.token.stale` (Arabic)

### B5 — Users list API

**GET** `/api/dashboard/users`

- **Query parameters**: `q` (search), `role` (filter by role), `status` (active|blocked), `verified` (true|false), `sort`, `page`, `size`
- **Response**: `ApiResponse<PageResponse<UserResponse>>` with added `summary` {total, admins, customers, blocked, unverified}
- **Each user item includes**: role, phone, emailVerified, blocked, createdAt, lastActiveAt
- **Summary computation**: Done over the WHOLE dataset (not the page, not the filters)
  - `total`: total number of users
  - `admins`: count of users with ADMIN role
  - `customers`: count of users with CUSTOMER role
  - `blocked`: count of users with `enabled=false`
  - `unverified`: count of users without email verification (addressed in future phase)
  - `byRole`: `[{role, userCount}]` per role over the whole dataset

### B6 — User activity

**GET** `/api/dashboard/users/{id}/activity`

- **Authority**: `user:read` and `order:read`
- **Response**: `{ordersCount, lifetimeSpend, lastOrderAt}`
- **lifetimeSpend**: Excludes cancelled and refunded orders

### B7 — Audit trail

- Uses existing `audit_events` table
- Recorded events:
  - `ROLE_CHANGED {actor, target, from, to, grantedCount, revokedCount}`
  - `ROLE_PERMISSIONS_UPDATED`
  - `ROLE_CREATED`
  - `ROLE_DELETED`
  - `USER_BLOCKED`
  - `USER_UNBLOCKED`
  - `USER_DELETED`

### B8 — Test coverage

- Missing authority → 403 on every new endpoint
- Self role change → 403; last admin demotion → 409; CONCURRENT test: two admins demote each other at the same time → at least one request fails and at least one admin remains
- Unknown role 422; success returns the correct granted/revoked diff
- Stale token rejected with `TOKEN_STALE` and works after refresh
- ADMIN permissions immutable; deleting a role in use → 409; `/roles` has no N+1 (assert query count); summary counts are correct and independent of filters
### Permission catalog

**GET** `/api/dashboard/roles/permissions`

- **Authority**: `user:read` or `role:read`
- **Response**: `[{slug, resource, action}]` — every declared permission, with resource/action split on `":"`.
