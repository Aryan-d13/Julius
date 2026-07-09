# ==============================================================================
# Julius — Developer Task Runner
# ==============================================================================
# Requires: Docker, Maven, Java 21
# Usage: make <target>
# Run 'make help' for available commands.
# ==============================================================================

.PHONY: help up down dev test test-integration test-all validate migrate info clean db-reset

help: ## Show available commands
	@echo.
	@echo   Julius Developer Commands
	@echo   ========================
	@echo.
	@echo   make up                Start PostgreSQL and Redis (Docker Compose)
	@echo   make down              Stop Docker Compose services
	@echo   make dev               Start dev server (launches infra + Spring Boot)
	@echo   make test              Run unit tests (H2, no Docker required)
	@echo   make test-integration  Run integration tests (PostgreSQL Testcontainers)
	@echo   make test-all          Run all tests
	@echo   make validate          Validate Flyway migrations against PostgreSQL
	@echo   make migrate           Run Flyway migrations against local PostgreSQL
	@echo   make info              Show Flyway migration status
	@echo   make clean             Clean build artifacts
	@echo   make db-reset          Reset database (drop all + re-migrate)
	@echo.

up: ## Start PostgreSQL and Redis via Docker Compose
	docker compose up -d
	@echo Waiting for PostgreSQL to be ready...
	@docker compose exec postgres pg_isready -U julius -t 30 >nul 2>&1 || echo Warning: PostgreSQL may not be ready yet

down: ## Stop Docker Compose services
	docker compose down

dev: up ## Start development server (launches infra + Spring Boot)
	mvn spring-boot:run

test: ## Run unit tests (H2, no Docker required)
	mvn clean test -DexcludedGroups=integration

test-integration: ## Run integration tests (requires Docker for Testcontainers)
	mvn clean test -Dgroups=integration

test-all: ## Run all tests (unit + integration)
	mvn clean test

validate: up ## Validate Flyway migrations against local PostgreSQL
	mvn flyway:validate

migrate: up ## Run Flyway migrations against local PostgreSQL
	mvn flyway:migrate

info: up ## Show Flyway migration status
	mvn flyway:info

clean: ## Clean build artifacts
	mvn clean

db-reset: up ## Reset database (drop all tables + re-migrate)
	mvn flyway:clean flyway:migrate
