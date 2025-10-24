\# Trading API \- T2 First Iteration

A RESTful trading platform built with Spring Boot providing market data integration, account management, comprehensive audit logging, and health monitoring.

**Course:** COMS 4156 \- Advanced Software Engineering, Columbia University

**Team Members:**

* Ramya Manasa Amancherla
* Nigel Nosakhare Alexis
* Ankit Mohapatra
* Hiba Altaf

**GitHub Repository:** [https://github.com/APLionsAdvancedSWE/Advanced-Programming-Group-Project](https://github.com/APLionsAdvancedSWE/Advanced-Programming-Group-Project)

**Tagged Release:** `v1.0-T2`

---

## **Table of Contents**

* API Endpoints
* Setup Instructions
* Running the Service
* Testing
* Code Quality
* Tools & Configuration
* Project Structure
* Project Management
* Team Contributions
* AI Usage

---

## **API Endpoints**

### **Health Monitoring**

#### **`GET /health`**

Returns comprehensive health status of the service with component-level monitoring.

**Request:**

bash

```shell
curl http://localhost:8080/health
```

**Response (200 OK):**

json

```json
{
  "status": "ok",
  "timestamp": "2025-10-23T18:12:00.950900Z",
  "version": "0.0.1-SNAPSHOT",
  "components": {
    "database": "healthy",
    "auditLogging": "healthy",
    "marketData": "healthy"
  }
}
```

**Response Fields:**

* `status`: Overall system status (`ok` or `degraded`)
* `timestamp`: Current server time in ISO-8601 format
* `version`: Application version
* `components`: Health status of individual system components
    * `database`: PostgreSQL connection status
    * `auditLogging`: Audit system operational status
    * `marketData`: Market data service availability

**Status Codes:**

* `200 OK` \- Service is healthy
* `405 Method Not Allowed` \- Invalid HTTP method used

**Notes:**

* All components must be healthy for overall status to be "ok"
* If any component is unhealthy, overall status becomes "degraded"

---

### **Audit Logs**

#### **`GET /audit/logs`**

Retrieves audit logs of all API requests with filtering and pagination support.

**Query Parameters:**

* `apiKey` (optional, string) \- Filter by API key
* `path` (optional, string) \- Filter by request path (e.g., "/health")
* `from` (optional, ISO-8601 timestamp) \- Start of date range
* `to` (optional, ISO-8601 timestamp) \- End of date range
* `page` (optional, integer, default: 0\) \- Page number for pagination
* `pageSize` (optional, integer, default: 50, max: 100\) \- Results per page

**Request Examples:**

bash

```shell
# Get all logs
curl http://localhost:8080/audit/logs

# Filter by path
curl "http://localhost:8080/audit/logs?path=/health"

# Filter by date range
curl "http://localhost:8080/audit/logs?from=2025-10-22T00:00:00Z&to=2025-10-23T23:59:59Z"

# Pagination
curl "http://localhost:8080/audit/logs?page=1&pageSize=20"

# Combined filters
curl "http://localhost:8080/audit/logs?path=/market/quote&from=2025-10-23T00:00:00Z&pageSize=10"
```

**Response (200 OK):**

json

```json
{
  "items": [
    {
      "id": "f0c7280c-d99b-40fc-a010-817068fef4b1",
      "ts": "2025-10-23T18:12:00.961097Z",
      "apiKey": null,
      "method": "GET",
      "path": "/health",
      "status": 200,
      "latencyMs": 72,
      "bodyHash": null
    }
  ],
  "page": 0,
  "pageSize": 50,
  "total": 150
}
```

**Response Fields:**

* `items`: Array of audit log entries
    * `id`: Unique log entry identifier (UUID)
    * `ts`: Request timestamp in ISO-8601 format
    * `apiKey`: API key used (null if none)
    * `method`: HTTP method (GET, POST, PUT, DELETE)
    * `path`: Request path
    * `status`: HTTP status code
    * `latencyMs`: Request processing time in milliseconds
    * `bodyHash`: SHA-256 hash of request body (if present)
* `page`: Current page number
* `pageSize`: Number of items per page
* `total`: Total number of matching records

**Status Codes:**

* `200 OK` \- Successfully retrieved logs
* `400 Bad Request` \- Invalid query parameters (e.g., malformed date)
* `500 Internal Server Error` \- Database error

**Implementation Notes:**

* All API requests are automatically logged by `AuditLoggingFilter`
* The `/audit/*` endpoints themselves are NOT logged to prevent recursive logging
* Logs are persisted in PostgreSQL `audit_logs` table with indexes on `ts`, `api_key`, and `path` for query optimization

---

### **Market Data**

#### **`GET /market/quote/{symbol}`**

Retrieves current market quote for a stock symbol using Alpha Vantage API.

**Path Parameters:**

* `symbol` (required, string) \- Stock ticker symbol (e.g., "AAPL", "IBM")

**Request:**

bash

```shell
curl http://localhost:8080/market/quote/AAPL
```

**Response (200 OK):**

json

```json
{
  "symbol": "AAPL",
  "open": 233.5292,
  "high": 233.5889,
  "low": 233.4296,
  "close": 233.5490,
  "volume": 46760,
  "ts": "2024-10-23T14:04:00Z",
  "last": 233.5490,
  "midPrice": 233.5093
}
```

**Response Fields:**

* `symbol`: Stock ticker symbol
* `open`: Opening price for the period
* `high`: Highest price during the period
* `low`: Lowest price during the period
* `close`: Closing price
* `volume`: Trading volume
* `ts`: Quote timestamp in ISO-8601 format
* `last`: Last traded price
* `midPrice`: Calculated mid-point between bid and ask

**Status Codes:**

* `200 OK` \- Successfully retrieved quote
* `404 Not Found` \- Symbol not found or not supported
* `500 Internal Server Error` \- External API error

**Supported Symbols:**

* AAPL (Apple Inc.)
* IBM (International Business Machines)

**External API:**

* Uses Alpha Vantage TIME\_SERIES\_INTRADAY endpoint
* Demo API key configured in `application.properties`
* Rate limits apply based on Alpha Vantage tier

---

### **Accounts & Positions**

#### **`GET /accounts/{accountId}/positions`**

Retrieves all open positions for a specific account.

**Path Parameters:**

* `accountId` (required, UUID) \- Account identifier

**Request:**

bash

```shell
curl http://localhost:8080/accounts/123e4567-e89b-12d3-a456-426614174000/positions
```

**Response (200 OK):**

json

```json
[
  {
    "accountId": "123e4567-e89b-12d3-a456-426614174000",
    "symbol": "AAPL",
    "qty": 100,
    "avgCost": 150.25
  },
  {
    "accountId": "123e4567-e89b-12d3-a456-426614174000",
    "symbol": "IBM",
    "qty": 50,
    "avgCost": 135.50
  }
]
```

**Response Fields:**

* Array of position objects:
    * `accountId`: Account identifier
    * `symbol`: Stock symbol
    * `qty`: Quantity of shares held
    * `avgCost`: Average cost basis per share

**Status Codes:**

* `200 OK` \- Successfully retrieved positions (empty array if no positions)
* `404 Not Found` \- Account does not exist
* `500 Internal Server Error` \- Database error

---

#### **`GET /accounts/{accountId}/pnl`**

Calculates total profit or loss for a specific account based on current market prices.

**Path Parameters:**

* `accountId` (required, UUID) \- Account identifier

**Request:**

bash

```shell
curl http://localhost:8080/accounts/123e4567-e89b-12d3-a456-426614174000/pnl
```

**Response (200 OK):**

json

```json
1234.56
```

**Response:** Single numeric value representing total P\&L in dollars

**Calculation:**

* For each position: (current\_market\_price \- avg\_cost) × quantity
* Sum across all positions

**Status Codes:**

* `200 OK` \- Successfully calculated P\&L
* `404 Not Found` \- Account does not exist
* `500 Internal Server Error` \- Calculation or market data error

---

### **Orders (In Progress)**

The following order management endpoints are currently under development:

* `POST /orders` \- Submit new order
* `GET /orders/{orderId}` \- Get order details
* `GET /orders/{orderId}/fills` \- Get order fills
* `POST /orders/{orderId}:cancel` \- Cancel order

**Status:** Backend services implemented, REST API endpoints coming soon.

---

## **Setup Instructions**

### **Prerequisites**

* **Java 17** or higher
* **Maven 3.9+**
* **PostgreSQL 14+**
* **Postman** (for API testing)

### **Database Setup**

**1\. Install PostgreSQL:**

macOS:

bash

```shell
brew install postgresql@14
brew services start postgresql@14
```

Ubuntu/Debian:

bash

```shell
sudo apt-get install postgresql-14
sudo systemctl start postgresql
```

**2\. Create Database and User:**

bash

```shell
# Connect to PostgreSQL
psql postgres

# Execute these SQL commands:
CREATE DATABASE trading_api;
CREATE USER trading_user WITH PASSWORD 'trading_pass';
GRANT ALL PRIVILEGES ON DATABASE trading_api TO trading_user;
\q
```

**3\. Verify Connection:**

bash

```shell
psql -U trading_user -d trading_api -c "\dt"
```

**Note:** Database tables are created automatically by Hibernate on first application startup using `spring.jpa.hibernate.ddl-auto=update`.

### **Repository Setup**

**1\. Clone Repository:**

bash

```shell
git clone https://github.com/APLionsAdvancedSWE/Advanced-Programming-Group-Project.git
cd Advanced-Programming-Group-Project
```

**2\. Install Dependencies:**

bash

```shell
mvn clean install
```

---

## **Running the Service**

### **Start Application**

bash

```shell
mvn spring-boot:run
```

Application starts on **port 8080** by default.

**Verify Running:**

bash

```shell
curl http://localhost:8080/health
```

Expected response: `{"status":"ok", ...}`

### **Stop Application**

Press `Ctrl+C` in the terminal running the application.

---

## **Testing**

### **Unit Tests**

**Run All Tests:**

bash

````shell
mvn clean test
```

**Output:**
```
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
````

**Test Breakdown:**

* `HealthControllerTest`: 5 tests
* `AuditControllerTest`: 7 tests
* `AuditLoggingFilterTest`: 6 tests
* `AuditServiceTest`: 11 tests

**Run Specific Test Class:**

bash

```shell
mvn test -Dtest=HealthControllerTest
```

**Testing Framework:** JUnit 5 with Mockito for mocking

**Test Patterns:**

* Each component has tests for typical, atypical, and invalid inputs
* `@BeforeEach` setup methods for test initialization
* `@ExtendWith(MockitoExtension.class)` for dependency mocking
* Tests organized by component in dedicated test classes

---

### **API Testing**

**Tool:** Postman

**Test Collection:** `TradingAPI_T2_Final.postman_collection.json`

**Import Collection:**

1. Open Postman
2. Click "Import"
3. Select `TradingAPI_T2_Final.postman_collection.json`

**Run All Tests:**

1. Select the collection
2. Click "Run"
3. Click "Run Trading API \- T2 Final"

**Test Coverage:**

* Health endpoint: 3 tests (typical, atypical, invalid)
* Audit logs endpoint: 6 tests (filtering, pagination, edge cases)
* Market data endpoint: 3 tests (valid symbols, invalid symbols, errors)

**Total:** 12 requests with 36 test assertions

**Test Results:** Exported results in `Trading API - T2 Final.postman_test_run.json`

**Features Verified:**

* ✅ Persistent data storage (audit logs written and retrieved)
* ✅ Request logging across all endpoints
* ✅ Multiple concurrent clients (simulated via Postman)

---

## **Code Quality**

### **Coverage**

**Generate Report:**

bash

```shell
mvn clean test jacoco:report
```

**View Report:**

bash

```shell
open target/site/jacoco/index.html
```

**Coverage Summary:**

* Overall project: 27% instruction coverage, 19% branch coverage
* Implemented components:
    * HealthController: 100% coverage
    * AuditController: 100% coverage
    * AuditLoggingFilter: 93% instruction, 62% branch
    * AuditService: 61% coverage

**Coverage Tool:** JaCoCo 0.8.11

**Report Location:** `target/site/jacoco/index.html`

---

### **Style Checking**

**Tool:** Checkstyle 10.12.4

**Configuration:** Google Java Style Guide (`checkstyle.xml`)

**Run Check:**

bash

```shell
mvn checkstyle:check
```

**Generate Report:**

bash

```shell
mvn checkstyle:checkstyle
open target/site/checkstyle.html
```

**Current Status:** 0 violations in implemented code

**Report Location:** `target/site/checkstyle.html`

---

### **Static Analysis**

**Tool:** PMD 6.55.0

**Run Check:**

bash

```shell
mvn pmd:check
```

**Generate Report:**

bash

```shell
mvn pmd:pmd
open target/site/pmd.html
```

**Current Status:** 0 warnings in implemented code

**Report Location:** `target/site/pmd.html`

---

## **Tools & Configuration**

### **Build System**

* **Maven 3.9.11** \- Build automation and dependency management
* **Configuration:** `pom.xml`

### **Testing Frameworks**

* **JUnit 5.11.3** \- Unit testing framework
* **Mockito 5.14.2** \- Mocking framework for isolated unit tests
* **Spring Boot Test** \- Integration testing support
* **Postman** \- API testing tool

### **Code Quality Tools**

* **Checkstyle 10.12.4** \- Style verification (Google Java Style)
    * Config: `checkstyle.xml`
    * Reports: `target/site/checkstyle.html`
* **PMD 6.55.0** \- Static code analysis
    * Reports: `target/site/pmd.html`
* **JaCoCo 0.8.11** \- Code coverage analysis
    * Reports: `target/site/jacoco/index.html`

### **Database & Persistence**

* **PostgreSQL 14** \- Relational database
* **Hibernate/JPA** \- ORM framework
* **HikariCP** \- Connection pooling
* **Auto DDL:** `spring.jpa.hibernate.ddl-auto=update` (creates tables automatically)

### **External APIs**

* **Alpha Vantage** \- Market data provider
* **API Key:** Configured in `application.properties` (`alphavantage.api.key=demo`)

### **Application Configuration**

**File:** `src/main/resources/application.properties`

properties

```
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/trading_api
spring.datasource.username=trading_user
spring.datasource.password=trading_pass
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true

# Flyway (disabled - using Hibernate DDL)
spring.flyway.enabled=false

# Alpha Vantage
alphavantage.api.key=demo

# Logging
logging.level.com.dev.tradingapi=DEBUG
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=DEBUG
```

---

## **Project Structure**

### **Domain Models (`src/main/java/com/dev/tradingapi/model/`)**

* **Account** \- User accounts with risk limits and balances
* **Order** \- Order entities (in progress)
* **Fill** \- Trade execution records (in progress)
* **Position** \- Open positions per account
* **Quote** \- Market data from Alpha Vantage
* **AuditLog** \- API request audit trail with indexed fields

### **Services (`src/main/java/com/dev/tradingapi/service/`)**

* **AccountService** \- Account management operations
* **PositionService** \- Position tracking and retrieval
* **PnlService** \- Profit/loss calculations
* **MarketService** \- Market data integration with Alpha Vantage
* **AuditService** \- Audit log management with filtering
* **ExecutionService** \- Order execution logic (backend implemented)
* **RiskService** \- Risk validation (in progress)

### **Controllers (`src/main/java/com/dev/tradingapi/controller/`)**

* **HealthController** \- `/health` endpoint
* **AuditController** \- `/audit/logs` endpoint
* **MarketController** \- `/market/quote/{symbol}` endpoint
* **AccountController** \- `/accounts/{accountId}/*` endpoints
* **OrderController** \- `/orders` endpoints (in progress)

### **Repositories (`src/main/java/com/dev/tradingapi/repository/`)**

* **AuditLogRepository** \- JPA repository for audit logs with custom queries
* **AccountRepository** \- Account data access
* **PositionRepository** \- Position data access
* **OrderRepository** \- Order data access (in progress)
* **FillRepository** \- Fill data access (in progress)

### **Filters (`src/main/java/com/dev/tradingapi/filter/`)**

* **AuditLoggingFilter** \- Automatically logs all API requests with method, path, status, and latency

### **DTOs (`src/main/java/com/dev/tradingapi/dto/`)**

* **CreateOrderRequest** \- Order submission request (in progress)

### **Exceptions (`src/main/java/com/dev/tradingapi/exception/`)**

* **NotFoundException** \- Resource not found
* **RiskException** \- Risk limit violations

---

## **Project Management**

Work is tracked using GitHub Projects with issue-based task management.

**Project Boards:**

1. [Controllers Progress Tracker](https://github.com/orgs/APLionsAdvancedSWE/projects/1)
2. [Model Progress Tracker](https://github.com/orgs/APLionsAdvancedSWE/projects/2)
3. [Service Progress Tracker](https://github.com/orgs/APLionsAdvancedSWE/projects/3)
4. [Tests Progress Tracker](https://github.com/orgs/APLionsAdvancedSWE/projects/4)
5. [Miscellaneous Progress Tracker](https://github.com/orgs/APLionsAdvancedSWE/projects/5)

**Workflow:**

* Tasks created as GitHub Issues
* Issues linked to project boards
* Status columns: Backlog → Ready → In Progress → In Review → Done
* All team members have access

---

## **Team Contributions**

### **Ramya Manasa Amancherla**

**Components Implemented:**

* Health monitoring system with component-level status checks
* Complete audit logging infrastructure:
    * AuditLog entity with database indexes
    * AuditLogRepository with custom JPA queries
    * AuditService with filtering and pagination
    * AuditLoggingFilter for automatic request logging
    * AuditController REST API
* Database schema configuration
* PostgreSQL setup and integration

**Testing:**

* 29 unit tests (HealthController: 5, AuditController: 7, AuditLoggingFilter: 6, AuditService: 11\)
* 12 Postman API tests with 36 assertions
* Test coverage: 78-93% on implemented components

**Code Quality:**

* 0 Checkstyle violations
* 0 PMD warnings
* Full JavaDoc documentation

**Metrics:**

* 13 files created/modified
* 2,348 lines of code added

### **Nigel Nosakhare Alexis**

**Components Implemented:**

* Market data service with Alpha Vantage integration
* Account service and controller
* Position service with account-based retrieval
* PnL calculation service
* Model classes: Account, Position, Quote
* Market data simulation engine

### **Ankit Mohapatra**

**Components Implemented:**

* Order model class
* Order management infrastructure (in progress)

### **Hiba Altaf**

**Components Implemented:**

* ExecutionService backend logic
* Fill model class
* Service integration (in progress)

---

## **Third-Party Dependencies**

All dependencies managed via Maven. No third-party code directly included in repository.

**Key Dependencies:**

* Spring Boot 3.4.4 \- Application framework
* Spring Data JPA \- Database abstraction
* PostgreSQL JDBC Driver 42.7.2 \- Database connectivity
* Jackson \- JSON processing
* Lombok \- Boilerplate reduction
* JUnit 5 \- Testing framework
* Mockito \- Mocking framework
* Checkstyle Maven Plugin \- Style checking
* PMD Maven Plugin \- Static analysis
* JaCoCo Maven Plugin \- Code coverage

**Complete list:** See `pom.xml`

---

## **AI Usage**

### **Tools Used**

* **Claude (Anthropic)** \- Conversational AI assistant
    * Source: [https://claude.ai](https://claude.ai)
    * Access: Free tier with Columbia .edu email
    * Cost: $0 (educational/student access)
* **OpenAI (ChatGPT)** \- Conversational AI assistant
    * Source: [https://chatgpt.com/](https://chatgpt.com/)
    * Access: Free tier
    * Cost: $0

### **Primary Use Cases**

**Development:**

* Architecture and design pattern discussions
* Debugging assistance for compilation and runtime errors
* Understanding Spring Boot framework concepts
* JPA/Hibernate query optimization guidance
* Exception handling strategies

**Documentation:**

* README structure and organization
* JavaDoc comment templates
* API documentation formatting

**Testing:**

* Test case design patterns (typical, atypical, invalid inputs)
* Mockito framework usage examples
* JUnit 5 best practices and annotations
* Test organization strategies

**Quality Assurance:**

* Checkstyle violation resolution guidance
* PMD warning interpretation
* Code review suggestions
* Best practices for maintainable code

### **Development Process**

AI was used as a development aid throughout the project:

1. Discussed architectural approaches and design patterns
2. Received suggestions for code structure and organization
3. Used AI to understand error messages and debug issues
4. Consulted AI for testing strategies and framework usage
5. All code manually written, reviewed, and tested by team members

### **Code Generation**

AI tools provided:

* Code structure suggestions and templates
* Example implementations for reference
* Debugging assistance and error resolution
* Documentation templates

All suggestions were:

* Manually reviewed and understood before implementation
* Modified to fit project-specific requirements
* Tested to verify functionality
* Checked for style compliance (Checkstyle, PMD)

### **Quality Assurance**

Quality was maintained through:

* Manual code review of all implementations
* Comprehensive unit and API testing
* Style checker verification (0 violations)
* Static analysis validation (0 warnings)
* Functional testing of all features

### **Educational Value**

AI enhanced the learning experience by:

* Providing immediate explanations of complex concepts
* Suggesting industry-standard patterns and practices
* Offering alternative implementation approaches
* Enabling faster iteration and learning cycles
* Allowing focus on system design and architecture

**Note:** All final implementations were created, understood, and verified by team members. AI served as a learning aid and development assistant, not a replacement for understanding or effort.

---

## **License**

Academic project for Columbia University COMS 4156 \- Advanced Software Engineering.

For academic integrity, this code should not be copied for course submissions.

---

## **Additional Notes**

### **Ordering Requirements**

No specific ordering requirements exist for the current endpoints. All can be called independently.

### **Future Enhancements (T3)**

* Complete order management REST API
* API key authentication via ApiKeyFilter
* Enhanced error handling with GlobalExceptionHandler
* Integration tests
* Sample client application
* Continuous integration pipeline
* 80%+ test coverage target 