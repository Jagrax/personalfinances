# PFIN - Configuración de Base de Datos (DESA / PROD)

Este proyecto utiliza dos bases de datos distintas para los entornos de desarrollo y producción:

- **pfindesa**: Entorno de desarrollo
- **pfinprod**: Entorno de producción

Ambas bases tienen usuarios propios con el mismo nombre que la base:

| Entorno    | Base de datos | Usuario  |
| ---------- | ------------- | -------- |
| Desarrollo | pfindesa      | pfindesa |
| Producción | pfinprod      | pfinprod |

---

## 1. Creación de bases de datos y usuarios

Ejecutar este script en tu servidor MySQL:

```sql
-- Base de datos de desarrollo
CREATE DATABASE IF NOT EXISTS pfindesa CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pfindesa'@'localhost' IDENTIFIED BY 'pfindesa';
GRANT ALL PRIVILEGES ON pfindesa.* TO 'pfindesa'@'localhost';

-- Base de datos de producción
CREATE DATABASE IF NOT EXISTS pfinprod CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pfinprod'@'localhost' IDENTIFIED BY 'pfinprod';
GRANT ALL PRIVILEGES ON pfinprod.* TO 'pfinprod'@'localhost';

FLUSH PRIVILEGES;
```

---

## 2. Configuración en Spring Boot

El proyecto usa [Spring Profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles) para cambiar de entorno.

### application.properties (común)

```properties
spring.profiles.active=desa
```

> Cambiar a `prod` para el entorno de producción.

### application-desa.properties

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pfindesa
spring.datasource.username=pfindesa
spring.datasource.password=TuPasswordDesa
spring.jpa.hibernate.ddl-auto=update
```

### application-prod.properties

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pfinprod
spring.datasource.username=pfinprod
spring.datasource.password=TuPasswordProd
spring.jpa.hibernate.ddl-auto=validate
```

---

## 3. Cambio de entorno

- Para ejecutar en **IntelliJ**: se recomienda dejar `spring.profiles.active=desa` (modo desarrollo).
- Para ejecución en **servidor (Tomcat)**: se puede pasar el perfil como parámetro:

```bash
-Dspring.profiles.active=prod
```

Alternativamente, se puede setear como variable de entorno:

```bash
export SPRING_PROFILES_ACTIVE=prod
```

---

## 4. Recomendaciones

- El usuario debe tener el mismo nombre que la base.
- Mantener las contraseñas en un archivo `.env` si se usan herramientas como Docker o un gestor de secretos.
- No usar `ddl-auto=create` en producción (peligroso).
- Hacer backups periódicos.

---

¡Listo! Ya tenés la configuración separada por entornos para que el proyecto crezca ordenadamente 🚀

