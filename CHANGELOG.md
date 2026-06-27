# Changelog

## [1.0.0-SNAPSHOT] - 2026-06-28

### Added
- Budget module: department-based monthly budget tracking with limit warnings
- Bank statement CSV import with automatic column detection
- Telegram bot with 15 commands including limit warnings and statement dates
- Database sequence auto-fixer on startup
- Structured logging (JSON format in production)
- Caffeine cache for active cards, departments, and exchange rates
- Swagger/OpenAPI documentation at /swagger-ui.html
- Actuator health indicators for Telegram bot and currency service
- Bean Validation annotations on domain entities
- PWA manifest improvements

### Fixed
- Expense amount field JS formatting freeze
- Audit log sequence duplicate key error
- Dashboard critical warning count including limit warnings
- Limit alert persistence across app restarts
- Orphan expense with null card prevention
