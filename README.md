# Firmware Upgrade Campaign Controller

## Overview

Spring Boot-based firmware upgrade campaign management application.

The system manages firmware upgrades across simulated broadband gateways, tracks device progress, handles asynchronous responses, and provides campaign status reporting.

## Tech Stack

- Java 17
- Spring Boot 4.1.2
- Maven
- Spring Web MVC
- Spring Data JPA
- H2 file-based database
- Spring Boot Actuator
- Spring Scheduler
- Python FastAPI device simulator
- Docker
- Docker Compose

## Architecture

The application consists of a Spring Boot controller, an H2 database, and a Python-based device simulator.

```mermaid
flowchart TB
    O["Operator<br/>Create / Monitor Campaign"]

    subgraph APP["Spring Boot Application"]
        API["REST APIs"]
        CS["Campaign Service"]
        SCH["Campaign Scheduler"]
        SM["Device State Machine"]

        API --> CS
        CS --> SCH
        CS --> SM
    end

    DB[("H2 Database<br/>Campaigns / Devices / Commands")]
    SIM["Python Simulator<br/>Broadband Gateways"]

    O --> API
    SM <--> DB
    SCH -->|"Connection Request"| SIM
    SIM -->|"Inform / Command Response"| API
    API -.->|"DOWNLOAD Command"| SIM

    classDef app fill:#E3EFFF,stroke:#4678B8,color:#173C6C
    classDef storage fill:#E0F4E7,stroke:#3F9463,color:#175834
    classDef simulator fill:#F0E5FF,stroke:#9462BF,color:#512F77

    class API,CS,SCH,SM app
    class DB storage
    class SIM simulator
```

### Components

- **REST APIs:** Handle campaign requests and device callbacks.
- **Campaign Service:** Manages upgrade campaigns and business rules.
- **Campaign Scheduler:** Controls upgrade execution and concurrency.
- **Device State Machine:** Tracks each device's upgrade progress.
- **H2 Database:** Stores campaigns, device states, and command details.
- **Python Simulator:** Simulates gateway firmware upgrades, reboots, and failures.

## Firmware Upgrade Sequence

```mermaid
sequenceDiagram
    autonumber

    actor O as Operator
    participant C as Spring Boot Controller
    participant DB as H2 Database
    participant G as Gateway Simulator

    rect rgb(232, 241, 255)
        Note over O,G: Campaign Creation
        O->>C: Create Campaign
        C->>G: Get Eligible Devices
        G-->>C: Device List
        C->>DB: Save Campaign and Devices
    end

    rect rgb(243, 235, 255)
        Note over C,G: Device Communication
        C->>G: Connection Request
        G-->>C: 202 Accepted
        G->>C: Inform (Firmware 1.0.0)
        C->>DB: Save Unique commandKey
        C-->>G: DOWNLOAD (Target 2.0.0)
        G->>C: Command Response (ACCEPTED)
    end

    rect rgb(255, 245, 225)
        Note over G: Firmware Upgrade
        G->>G: Download and Reboot
    end

    rect rgb(231, 248, 237)
        Note over C,G: Upgrade Confirmation
        G->>C: BOOT / TRANSFER_COMPLETE
        C->>DB: Correlate Command and Record Result
        O->>C: Get Campaign Status
        C-->>O: Campaign Progress
    end
```

### Upgrade Flow

1. An operator creates a firmware upgrade campaign.
2. The controller retrieves eligible gateways from the simulator.
3. The scheduler sends connection requests.
4. Gateways initiate sessions using the Inform API.
5. The controller returns a DOWNLOAD command with a unique `commandKey`.
6. Gateways acknowledge the command, download firmware, and reboot.
7. The controller processes completion events and updates device status.

The `deviceId` identifies a gateway, while the `commandKey` uniquely identifies an upgrade command.

## Device State Machine

```mermaid
flowchart TB
    P["PENDING"]
    N["NOTIFIED"]
    D["DOWNLOAD_SENT"]
    A["ACCEPTED"]

    S["SUCCEEDED"]
    F["FAILED"]
    T["TIMED_OUT"]

    P -->|"Connection Request"| N
    N -->|"Inform Received"| D
    D -->|"Command Accepted"| A

    A -->|"TransferComplete Success"| S
    A -->|"BOOT with Target Version"| S

    D -->|"Command Rejected"| F
    A -->|"Upgrade Fault"| F

    N -->|"No Inform"| T
    D -->|"No Acknowledgment"| T
    A -->|"Completion Timeout"| T

    classDef active fill:#E5EFFF,stroke:#477BBD,color:#214B7D
    classDef success fill:#DDF4E5,stroke:#36925C,color:#1A653B
    classDef failure fill:#FFE2E2,stroke:#C85A5A,color:#8C2929
    classDef timeout fill:#FFF0D0,stroke:#BE8D32,color:#805716

    class P,N,D,A active
    class S success
    class F failure
    class T timeout
```

### State Descriptions

| State | Description |
|---|---|
| PENDING | Gateway is eligible but has not been contacted |
| NOTIFIED | Connection request sent |
| DOWNLOAD_SENT | Download command issued |
| ACCEPTED | Gateway accepted the download command |
| SUCCEEDED | Firmware upgrade confirmed |
| FAILED | Upgrade rejected or failed |
| TIMED_OUT | Device did not respond within the configured deadline |

## Package Structure

Planned package structure:

```text
device.simulator
├── controller
├── dto
├── entity
├── repository
├── service
├── scheduler
└── exception
```

## Design Decisions

- **Single Spring Boot application**
  - **Why:** Simple architecture with fewer deployment dependencies.
  - **Trade-off:** No horizontal scaling in the initial implementation.

- **H2 file-based database**
  - **Why:** Lightweight database with straightforward Spring Data JPA integration.
  - **Trade-off:** A shared relational database would be preferred for multiple production instances.

- **Scheduler-based processing**
  - **Why:** Manages asynchronous upgrades without blocking application threads.
  - **Trade-off:** Scheduling frequency affects execution responsiveness.

- **Unique commandKey**
  - **Why:** Correlates device acknowledgments and completion callbacks.
  - **Trade-off:** Requires persistent command tracking and idempotency handling.

- **Firmware version-based confirmation**
  - **Why:** Supports gateways that reboot successfully without sending TransferComplete.
  - **Trade-off:** Requires additional validation of reported firmware versions.

- **Configurable retries and timeouts**
  - **Why:** Handles offline and unresponsive devices.
  - **Trade-off:** Late callbacks may arrive after timeout decisions.

## REST APIs

| Method | Endpoint | Description |
|---|---|---|
| POST | `/campaigns` | Create firmware upgrade campaign |
| GET | `/campaigns/{id}` | Retrieve campaign status |
| POST | `/campaigns/{id}/pause` | Pause campaign |
| POST | `/campaigns/{id}/resume` | Resume campaign |
| POST | `/devices/{deviceId}/inform` | Process device Inform event |
| POST | `/devices/{deviceId}/command-response` | Process command acknowledgment |

## Campaign Request Example

```json
{
  "targetVersion": "2.0.0",
  "imageUrl": "https://example.com/firmware/2.0.0.bin",
  "deviceFilter": {
    "currentVersion": "1.0.0"
  },
  "maxConcurrent": 50,
  "failureThresholdPercent": 20
}
```

## Testing Approach

Testing will include:

- Unit tests for campaign and device state transitions
- Integration tests for REST APIs
- Duplicate and delayed callbacks
- Successful and failed firmware upgrades
- Timeout and retry scenarios
- Maximum concurrent upgrades
- Automatic campaign halt
- Controller restart and database persistence
- End-to-end tests using 10–20 simulated gateways
- Full-fleet validation using 1,000 gateways

## Limitations and Trade-offs

- H2 is intended for local development and single-instance operation.
- The simulator does not download actual firmware images.
- The initial implementation uses a single application instance.
- Production authentication and authorization are outside the initial scope.
- Distributed scheduling and shared persistence would be needed at larger scale.

## Run Locally

### Maven

Build and test:

```cmd
mvnw.cmd clean package
```

Start:

```cmd
mvnw.cmd spring-boot:run
```

Verify:

```cmd
curl http://localhost:8080/actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

### Docker Compose

Build:

```cmd
mvnw.cmd clean package
```

Start:

```cmd
docker compose up --build -d
```

Verify:

```cmd
curl http://localhost:8080/actuator/health
```

Stop:

```cmd
docker compose down
```

## Observability

Spring Boot Actuator provides health checks and application metrics.

- `/actuator/health`
- `/actuator/metrics`

Additional metrics will track connection requests, download commands, successful upgrades, failures, and timeouts.

## Implementation Status

**Completed:**
- Spring Boot project initialization
- Maven build and local execution
- Docker Compose setup
- Health endpoint validation

**Planned:**
- Campaign and device entities
- Database repositories
- REST API implementation
- Campaign scheduler
- Upgrade state machine
- Failure handling and metrics
- Simulator integration and end-to-end testing
