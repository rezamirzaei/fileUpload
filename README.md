# Large File Upload Application

A modern Spring Boot 3.2 application designed for **uploading large files (up to 10GB)** with streaming support, **AES-256 encryption at rest**, **HTTPS encryption in transit**, progress tracking, and Docker deployment.

## ✨ Features

- 📤 **Large File Upload** - Supports files up to 10GB
- 🔒 **AES-256-GCM Encryption** - Files encrypted at rest for security
- 🔐 **HTTPS/TLS** - Data encrypted in transit (SSL/TLS)
- 🌊 **Streaming Upload** - Files stream directly to disk, no memory buffering
- 📊 **Real-time Progress** - Upload progress bar with speed and ETA
- 🎯 **Drag & Drop** - Modern drag-and-drop UI
- ⬇️ **Download Files** - Stream downloads with automatic decryption
- 🗑️ **Delete Files** - Remove files with one click
- 🐳 **Docker Ready** - Full Docker & Docker Compose support
- 💾 **Persistent Storage** - Files survive container restarts
- 📈 **Storage Stats** - View total files, storage used, and available space

## 🔐 Security Features

### Encryption in Transit (HTTPS/TLS)
- **TLS 1.2/1.3** - Modern protocols only
- **Strong ciphers** - AES-GCM, ChaCha20-Poly1305
- **HTTP→HTTPS redirect** - All traffic encrypted
- **Nginx reverse proxy** - Handles TLS termination

### Encryption at Rest (AES-256-GCM)
- **AES-256-GCM encryption** - Military-grade encryption for files
- **Unique IV per file** - Each file uses a unique initialization vector
- **Authentication tag** - Ensures file integrity (detects tampering)
- **Configurable secret key** - Set via environment variable for production

## 🛠️ Tech Stack

- **Java 21**
- **Spring Boot 3.2.5**
- **Spring Data JPA**
- **MySQL 8.0**
- **Nginx** (HTTPS reverse proxy)
- **Thymeleaf + Bootstrap 5**
- **Docker & Docker Compose**

## 🚀 Quick Start with Docker

```bash
# Generate an encryption key (IMPORTANT: save this!)
export ENCRYPTION_SECRET_KEY=$(openssl rand -base64 32)
echo "Save this key: $ENCRYPTION_SECRET_KEY"

# Generate SSL certificates (for development)
./generate-ssl-cert.sh

# Clone and start
git clone <repository-url>
cd fileUpload

# Build and run all services
docker-compose up --build

# Run in background
docker-compose up -d --build
```

**Access the application:** 
- 🔒 **HTTPS:** https://localhost (recommended)
- 🔓 HTTP: http://localhost (redirects to HTTPS)

### Docker Commands

```bash
# Stop services
docker-compose down

# Stop and remove all data (uploaded files + database)
docker-compose down -v

# View logs
docker-compose logs -f app

# Rebuild after code changes
docker-compose up --build
```

## 🔑 Encryption Configuration

### Quick Setup
```bash
# 1. Copy the example env file
cp .env.example .env

# 2. Generate and add your encryption key
echo "ENCRYPTION_SECRET_KEY=$(openssl rand -base64 32)" >> .env

# 3. Generate SSL certificates
./generate-ssl-cert.sh

# 4. Start the application
docker-compose up --build
```

### Where is the Key Stored?
The key is stored in `.env` file (not committed to git). Example:
```
ENCRYPTION_SECRET_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

### Backup Your Key!
**Store your key in a safe place:**
- Password manager (1Password, Bitwarden, etc.)
- Secure notes
- Encrypted backup

⚠️ **WARNING**: Without the secret key, encrypted files **cannot be recovered**!

## 🔐 SSL/TLS Certificates

### For Development (Self-Signed)
```bash
./generate-ssl-cert.sh
```
Browser will show a security warning - click "Advanced" → "Proceed" (this is normal for self-signed certs).

### For Production (Let's Encrypt)
Replace the certificates in `nginx/certs/` with real certificates:
```bash
# Using certbot
certbot certonly --standalone -d yourdomain.com

# Copy certificates
cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem nginx/certs/server.crt
cp /etc/letsencrypt/live/yourdomain.com/privkey.pem nginx/certs/server.key
```

## 💻 Local Development

### Prerequisites

- Java 21
- Maven 3.8+
- MySQL 8.0

### Setup

1. **Create MySQL database:**
```sql
CREATE DATABASE fileupload;
```

2. **Update database credentials** in `src/main/resources/application.properties` if needed.

3. **Run the application:**
```bash
./mvnw spring-boot:run
```

4. **Access:** http://localhost:8080

## 📁 Project Structure

```
src/main/java/com/example/demo/
├── FileUploadApplication.java      # Main application
├── controller/
│   └── UploadController.java       # Web & API endpoints
├── model/
│   └── Folder.java                 # JPA entity for file metadata
├── repository/
│   └── FileRepository.java         # Spring Data JPA repository
├── service/
│   └── FolderService.java          # File storage service (streaming)
└── exception/
    ├── FileStorageException.java
    ├── FileNotFoundException.java
    └── GlobalExceptionHandler.java
```

## 🔧 Configuration

### File Upload Settings (application.properties)

```properties
# Maximum file size (default: 10GB)
spring.servlet.multipart.max-file-size=10GB
spring.servlet.multipart.max-request-size=10GB

# Files larger than 1MB are streamed directly to disk
spring.servlet.multipart.file-size-threshold=1MB

# Upload directory
file.upload-dir=./uploads
```

### JVM Settings for Large Files (docker-compose.yml)

```yaml
environment:
  - JAVA_OPTS=-Xms512m -Xmx4g -XX:+UseG1GC -XX:MaxDirectMemorySize=1g
```

## 📡 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Home page with file list |
| POST | `/upload` | Upload a file (multipart/form-data) |
| GET | `/download/{id}` | Download a file |
| POST | `/delete/{id}` | Delete a file |
| GET | `/api/stats` | Get storage statistics (JSON) |
| GET | `/actuator/health` | Health check |

## 🐳 Docker Architecture

```
┌─────────────────────────────────────────────────┐
│                 Docker Network                   │
│  ┌──────────────────┐    ┌──────────────────┐   │
│  │   fileupload-app │    │   fileupload-db  │   │
│  │   (Spring Boot)  │◄──►│     (MySQL)      │   │
│  │    Port: 8080    │    │    Port: 3306    │   │
│  └────────┬─────────┘    └────────┬─────────┘   │
│           │                       │             │
│           ▼                       ▼             │
│    ┌─────────────┐         ┌─────────────┐      │
│    │upload-data  │         │ mysql-data  │      │
│    │  (Volume)   │         │  (Volume)   │      │
│    └─────────────┘         └─────────────┘      │
└─────────────────────────────────────────────────┘
```

## 🔒 Security Notes

- Application runs as non-root user in Docker
- File names are sanitized to prevent path traversal
- Unique UUIDs prevent file name collisions
- Health checks ensure service availability

## 📝 License

MIT License
