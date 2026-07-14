# Local Development Setup Guide — Julius Platform

This guide outlines the step-by-step instructions to configure, build, and run the Julius video clipping platform locally on a clean system.

---

## 1. System Requirements & Prerequisites

The following software dependencies must be installed on your development workstation:

| Dependency | Minimum Version | Installation Command (Windows) | Installation Command (macOS / Homebrew) |
| :--- | :--- | :--- | :--- |
| **Java Development Kit (JDK)** | **JDK 21** | `winget install Eclipse.Temurin.21.JDK` | `brew install openjdk@21` |
| **Maven** | **Maven 3.9+** | `winget install Apache.Maven` | `brew install maven` |
| **Node.js & npm** | **Node.js v20.x** | `winget install OpenJS.NodeJS.LTS` | `brew install node` |
| **Docker Desktop** | **Docker 25+** | `winget install Docker.DockerDesktop` | `brew install --cask docker` |
| **FFmpeg CLI** | **FFmpeg 6.0+** | `winget install Gyan.FFmpeg` | `brew install ffmpeg` |
| **Python** | **Python 3.11** | `winget install Python.Python.3.11` | `brew install python@3.11` |

> [!IMPORTANT]
> Ensure all installed binary executables (`java`, `mvn`, `node`, `docker`, `ffmpeg`, `python`) are successfully added to your system's global `PATH` environment variable.

---

## 2. Infrastructure Setup (Docker Services)

Julius requires PostgreSQL 16 (relational database) and Redis (caching and job queues) during local execution.

1. Navigate to the root directory of the cloned repository.
2. Launch the docker-compose services in detached/background mode:
   ```bash
   docker compose up -d
   ```
3. Verify that the database and queue containers are healthy:
   ```bash
   docker compose ps
   ```
   * **PostgreSQL** runs on `localhost:5432` (credentials: `julius`/`julius`).
   * **Redis** runs on `localhost:6379`.

---

## 3. Backend (Spring Boot API Server) Setup

### A. Environment Configuration
Create a `.env` file at the repository root to feed developer overrides (or export these environment variables directly):
```env
# Google Gemini API Access (required for AI auto-framing and summaries)
GOOGLE_API_KEY=your_gemini_api_key_here

# Stripe Keys (required for local checkout/billing portal simulation)
STRIPE_API_KEY=sk_test_mock_123
STRIPE_WEBHOOK_SECRET=whsec_mock_123
```

### B. Compile and Install Dependencies
Validate and compile the Java source files, downloading required Maven dependencies:
```bash
mvn clean install -DskipTests
```

### C. Run the Spring Boot Server
Launch the backend server using the Spring Boot Maven plugin:
```bash
mvn spring-boot:run
```
* The API server bootstraps and runs on **`http://localhost:8080`**.
* At startup, Flyway automatically runs database migrations (`V1` through `V7`) and seeds dev sandbox data.

---

## 4. Frontend (Next.js SPA Client) Setup

The user interface is located inside the `web-interface/` subdirectory.

1. Navigate to the frontend directory:
   ```bash
   cd web-interface
   ```
2. Install npm dependencies:
   ```bash
   npm install
   ```
3. Run the Next.js development server:
   ```bash
   npm run dev
   ```
* The development portal runs on **`http://localhost:3000`**.
* The server compiles static page routes dynamically using the Turbopack build engine.

---

## 5. Stripe Webhook Testing Setup

To process subscriptions, upgrade cycles, and payments locally, configure Stripe CLI to forward events:

1. Download and authenticate the [Stripe CLI](https://stripe.com/docs/stripe-cli).
2. Forward Stripe webhook triggers to your local API backend server:
   ```bash
   stripe listen --forward-to http://localhost:8080/api/billing/webhook
   ```
3. Copy the outputted webhook signature key (starts with `whsec_`) and assign it to the `clipper.billing.stripe.webhook-secret` parameter inside your environment shell configuration.

---

## 6. Verification & Test Execution

Before submitting code, execute both test suites to ensure absolute stability:

### Run Backend Tests
Runs all unit tests, double-entry accounting ledger checks, and Compare-And-Swap (CAS) quota test flows:
```bash
mvn test
```
*(Dynamic skips ensure database integration tests gracefully bypass if the Docker container environment is offline).*

### Run Frontend Tests
```bash
cd web-interface
npx vitest run
```

### Run Linter Checks
Verify the NextJS client passes coding standard validations:
```bash
cd web-interface
npm run lint
```

---

## 7. Troubleshooting Diagnostics

* **FFmpeg Not Found**: Ensure executing `ffmpeg -version` in a clean command line output does not throw an error. If it does, add the `/bin` directory of your FFmpeg installation path directly to the Windows PATH.
* **Testcontainers Connection Failures**: Verify Docker Desktop is active and running. If you are developing offline without Docker, backend integration tests will dynamically fall back to the safe, in-memory H2 configuration automatically.
* **Flyway Migration Conflicts**: If database schemas become corrupted or desynchronized during iterative schema development, wipe out the local volume state and recreate:
  ```bash
  docker compose down -v
  docker compose up -d
  ```
