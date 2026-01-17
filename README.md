# 🔐 Secure Large File Upload Application

A modern, enterprise-grade Spring Boot 3.2 application for **secure large file uploads (up to 10GB)** featuring **Zero-Knowledge End-to-End Encryption**, multi-user authentication, role-based access control, and Docker deployment.

## ✨ Key Features

### 📤 File Management
- **Large File Support** - Upload files up to 10GB
- **Streaming Upload** - Files stream directly to disk (no memory buffering)
- **Real-time Progress** - Upload progress bar with speed and ETA
- **Drag & Drop UI** - Modern, intuitive interface
- **Download & Delete** - Full file lifecycle management

### 🔒 Zero-Knowledge Encryption
- **Client-Side Encryption** - Files encrypted in your browser before upload
- **Password-Derived Keys** - Encryption key derived from your password using PBKDF2
- **Server Never Sees Plaintext** - True end-to-end encryption
- **Per-User Isolation** - Each user has unique encryption, even admins can't decrypt other users' files

### 👥 Multi-User System
- **User Registration & Login** - Secure authentication with BCrypt
- **Role-Based Access Control** - USER and ADMIN roles
- **Session Management** - Secure session handling with CSRF protection
- **Per-User File Storage** - Users only see their own files

### 🛡️ Admin Panel
- **Dashboard** - Overview of system statistics
- **User Management** - View, enable/disable, change roles, delete users
- **File Management** - View all files in the system (encrypted - can't read contents)
- **Storage Monitoring** - Track total storage usage

### 🐳 Docker Ready
- **Full Docker Compose** - One-command deployment
- **Nginx Reverse Proxy** - HTTPS/TLS termination
- **MySQL Database** - Persistent data storage
- **Health Checks** - Automatic service monitoring

## 🔐 Security Architecture

### Zero-Knowledge Encryption Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       ZERO-KNOWLEDGE ENCRYPTION                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  LOGIN:                                                                         │
│  ┌──────────┐     ┌─────────────┐     ┌────────────────┐     ┌───────────────┐ │
│  │ Password │────▶│   Browser   │────▶│ PBKDF2 310K    │────▶│ AES-256 Key   │ │
│  └──────────┘     │ (Client)    │     │ iterations     │     │ (Browser Only)│ │
│                   └─────────────┘     └────────────────┘     └───────────────┘ │
│                         │                                                       │
│                         ▼                                                       │
│                   ┌─────────────┐                                               │
│                   │   Server    │  Password sent for authentication only        │
│                   │  (BCrypt)   │  Server NEVER has encryption key!             │
│                   └─────────────┘                                               │
│                                                                                 │
│  UPLOAD:                                                                        │
│  ┌──────────┐     ┌─────────────┐     ┌────────────────┐     ┌───────────────┐ │
│  │  File    │────▶│   Browser   │────▶│  AES-256-GCM   │────▶│  Encrypted    │ │
│  │(Plaintext)     │  Encrypts   │     │  Encryption    │     │  to Server    │ │
│  └──────────┘     └─────────────┘     └────────────────┘     └───────────────┘ │
│                                                                                 │
│  Server stores encrypted blob - CANNOT decrypt without user's password!         │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Security Features

| Layer | Technology | Description |
|-------|------------|-------------|
| **Transport** | TLS 1.3 | All traffic encrypted via HTTPS |
| **Authentication** | BCrypt | Password hashing with salt |
| **Session** | Spring Security | CSRF protection, session fixation prevention |
| **Encryption** | AES-256-GCM | Client-side file encryption |
| **Key Derivation** | PBKDF2-SHA256 | 310,000 iterations (OWASP 2023) |
| **Authorization** | Role-Based | USER and ADMIN roles |

## 🛠️ Tech Stack

- **Java 21** (LTS)
- **Spring Boot 3.2.5**
- **Spring Security 6**
- **Spring Data JPA**
- **MySQL 8.0**
- **Nginx** (HTTPS reverse proxy)
- **Thymeleaf + Bootstrap 5**
- **Web Crypto API** (client-side encryption)
- **Docker & Docker Compose**

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- OpenSSL (for certificate generation)

### 1. Clone and Setup

```bash
git clone <repository-url>
cd fileUpload

# Generate SSL certificates (for development)
./generate-ssl-cert.sh
```

### 2. Start the Application

```bash
# Build and run all services
docker-compose up --build

# Or run in background
docker-compose up -d --build
```

### 3. Access the Application

| URL | Description |
|-----|-------------|
| https://localhost | Main application |
| https://localhost/login | Login page |
| https://localhost/register | User registration |
| https://localhost/admin | Admin panel (admin only) |

### 4. Default Admin Credentials

```
Username: admin
Password: admin123
⚠️ CHANGE THIS PASSWORD IMMEDIATELY!
```

## 📁 Project Structure

```
fileUpload/
├── src/main/java/com/example/demo/
│   ├── FileUploadApplication.java          # Main application
│   ├── config/
│   │   ├── SecurityConfig.java             # Spring Security config
│   │   ├── DataInitializer.java            # Default admin creation
│   │   └── EncryptionKeyAuthenticationHandler.java
│   ├── controller/
│   │   ├── UploadController.java           # File upload/download
│   │   ├── AuthController.java             # Login/register + salt API
│   │   └── AdminController.java            # Admin panel
│   ├── model/
│   │   ├── User.java                       # User entity
│   │   ├── Role.java                       # USER/ADMIN enum
│   │   └── Folder.java                     # File metadata entity
│   ├── repository/
│   │   ├── UserRepository.java
│   │   └── FileRepository.java
│   ├── service/
│   │   ├── UserService.java                # User management
│   │   ├── FolderService.java              # File storage
│   │   ├── EncryptionService.java          # Server-side encryption
│   │   └── CustomUserDetailsService.java   # Spring Security
│   └── exception/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.properties
│   ├── static/js/
│   │   └── zero-knowledge-encryption.js    # Client-side crypto
│   └── templates/
│       ├── login.html
│       ├── register.html
│       ├── upload.html
│       └── admin/
│           ├── dashboard.html
│           ├── users.html
│           └── files.html
├── nginx/
│   ├── nginx.conf                          # Nginx configuration
│   └── certs/                              # SSL certificates
├── docker-compose.yml
├── Dockerfile
└── README.md
```

## 🔧 Configuration

### Environment Variables (docker-compose.yml)

```yaml
environment:
  # Database
  - SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/fileupload
  - SPRING_DATASOURCE_USERNAME=fileupload
  - SPRING_DATASOURCE_PASSWORD=your_password
  
  # Encryption
  - ENCRYPTION_ENABLED=true
  
  # Default admin password (change in production!)
  - ADMIN_DEFAULT_PASSWORD=admin123
  
  # JVM settings for large files
  - JAVA_OPTS=-Xms512m -Xmx4g -XX:+UseG1GC
```

### Application Properties

```properties
# File upload limits
spring.servlet.multipart.max-file-size=10GB
spring.servlet.multipart.max-request-size=10GB
spring.servlet.multipart.file-size-threshold=1MB

# Upload directory
file.upload-dir=./uploads

# Encryption
encryption.enabled=true
```

## 📡 API Endpoints

### Public Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/login` | Login page |
| GET | `/register` | Registration page |
| POST | `/login` | Authenticate user |
| POST | `/register` | Register new user |
| GET | `/api/auth/salt/{username}` | Get user's encryption salt |
| GET | `/actuator/health` | Health check |

### Authenticated Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Dashboard with file list |
| POST | `/upload` | Upload a file |
| GET | `/download/{id}` | Download a file |
| POST | `/delete/{id}` | Delete a file |
| GET | `/api/stats` | Storage statistics |

### Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin` | Admin dashboard |
| GET | `/admin/users` | User management |
| POST | `/admin/users/{id}/toggle` | Enable/disable user |
| POST | `/admin/users/{id}/role` | Change user role |
| POST | `/admin/users/{id}/delete` | Delete user |
| GET | `/admin/files` | All files in system |
| GET | `/admin/users/{id}/files` | Files for specific user |

## 🐳 Docker Commands

```bash
# Start all services
docker-compose up -d --build

# Stop services
docker-compose down

# View logs
docker-compose logs -f app

# Reset database (WARNING: deletes all data!)
docker-compose down -v
docker-compose up -d --build

# Rebuild after code changes
docker-compose build app
docker-compose up -d
```

## 🐳 Docker Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Docker Network                                │
│  ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐    │
│  │    Nginx     │   │   Spring Boot    │   │      MySQL       │    │
│  │   (HTTPS)    │──▶│   Application    │──▶│    Database      │    │
│  │  Port 443    │   │    Port 8080     │   │   Port 3306      │    │
│  └──────────────┘   └──────────────────┘   └──────────────────┘    │
│         │                    │                      │               │
│         ▼                    ▼                      ▼               │
│  ┌─────────────┐      ┌─────────────┐       ┌─────────────┐        │
│  │ SSL Certs   │      │upload-data  │       │ mysql-data  │        │
│  │  (Volume)   │      │  (Volume)   │       │  (Volume)   │        │
│  └─────────────┘      └─────────────┘       └─────────────┘        │
└─────────────────────────────────────────────────────────────────────┘
```

## 🔒 Security Considerations

### Zero-Knowledge Encryption
- **Pro**: Server cannot read your files, even if compromised
- **Con**: If you forget your password, **files are permanently lost**
- **Note**: There is NO password recovery - your password IS your encryption key

### For Production Deployment

1. **Change default admin password immediately**
2. **Use real SSL certificates** (Let's Encrypt)
3. **Set strong database passwords**
4. **Enable firewall rules**
5. **Regular backups** (encrypted files only - no key backup possible)
6. **Monitor logs** for suspicious activity

### SSL Certificates for Production

```bash
# Using certbot for Let's Encrypt
certbot certonly --standalone -d yourdomain.com

# Copy to nginx/certs/
cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem nginx/certs/server.crt
cp /etc/letsencrypt/live/yourdomain.com/privkey.pem nginx/certs/server.key
```

## ⚠️ Important Warnings

1. **Password = Encryption Key**: Your password derives the encryption key. If forgotten, files are **UNRECOVERABLE**.

2. **No Admin File Access**: Admins can manage users and see file metadata, but **CANNOT decrypt or read file contents**.

3. **Client-Side Encryption**: Files are encrypted in your browser. The server never sees plaintext data.

4. **Session-Based Keys**: Encryption key exists only in browser session. Logging out clears it.

## 🧪 Testing

```bash
# Run unit tests
./mvnw test

# Run with coverage
./mvnw test jacoco:report
```

## 📝 Changelog

### v2.0.0 (January 2026)
- ✨ Zero-Knowledge End-to-End Encryption
- ✨ Client-side file encryption using Web Crypto API
- ✨ Multi-user authentication system
- ✨ Role-based access control (USER/ADMIN)
- ✨ Admin panel for user and file management
- ✨ PBKDF2 key derivation (310K iterations)
- 🔒 CSRF protection
- 🔒 Session fixation prevention
- 🔒 HSTS headers

### v1.0.0
- Initial release
- Large file upload support (10GB)
- Server-side AES-256 encryption
- Docker deployment

## 📄 License

MIT License

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

---

**Built with ❤️ using Spring Boot 3.2 and Zero-Knowledge Encryption**
