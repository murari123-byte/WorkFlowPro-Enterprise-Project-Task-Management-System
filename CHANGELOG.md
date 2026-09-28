# Changelog

All notable changes to this project are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added — Phase 1 / Step 1: project structure and basic services (2026-09-28)
- Maven multi-module parent `pom.xml` (Spring Boot 4.0.8, Spring Cloud 2025.1.3, Java 21).
- Modules: `api-gateway`, `auth-service`, `project-service`, `task-service`, `notification-service`.
- Each service: main class, `application.yml` with port from env var, `/actuator/health`.
- Business services: `GET /api/<service>/ping` endpoint + unit test + context-load test.
- `api-gateway`: Spring Cloud Gateway (WebFlux) skeleton, no routes yet.
- `.env.example`, `.gitignore`, `README.md`, `docs/` folder.

### Changed
- Default ports moved from 8080–8084 to **9080–9084** because 8080–8084 were already used
  by other applications on the development machine.
