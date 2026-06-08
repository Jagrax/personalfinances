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

El proyecto usa el [Spring Profile](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.profiles) `wildfly` para el datasource vía JNDI y el resto de configuraciones. Se define en un solo archivo:

### application-wildfly.properties

```properties
spring.datasource.jndi-name=java:/personalfinancesDS
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=true
logging.pattern.dateformat=yyyy-MM-dd HH:mm:ss
server.servlet.context-path=/personalfinances
spring.mvc.format.date=dd/MM/yyyy
spring.mvc.format.date-time=dd/MM/yyyy HH:mm:ss
spring.mvc.format.time=HH:mm:ss
spring.jackson.serialization.write-dates-as-timestamps=false
```

> Para desarrollo, pasar `-Dspring.jpa.hibernate.ddl-auto=update` para que Hibernate ajuste el schema automáticamente.

---

## 3. Setup de WildFly

### 3.1 Crear el módulo MySQL

1. Crear la carpeta `modules/com/mysql/main/` dentro del WildFly:

   ```
   mkdir -p WILDFLY_HOME/modules/com/mysql/main
   ```

2. Copiar el conector MySQL a esa carpeta:

   ```
   cp ~/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar WILDFLY_HOME/modules/com/mysql/main/
   ```

3. Crear `WILDFLY_HOME/modules/com/mysql/main/module.xml`:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <module xmlns="urn:jboss:module:1.3" name="com.mysql">
       <resources>
           <resource-root path="mysql-connector-j-8.3.0.jar"/>
       </resources>
       <dependencies>
           <module name="jakarta.transaction.api"/>
           <module name="jakarta.servlet.api"/>
       </dependencies>
   </module>
   ```

### 3.2 Registrar el driver en standalone.xml

Dentro de `<datasources>`, agregar:

```xml
<drivers>
    <driver name="mysql" module="com.mysql">
        <driver-class>com.mysql.cj.jdbc.Driver</driver-class>
    </driver>
    <!-- ... driver h2 existente ... -->
</drivers>
```

### 3.3 Configurar el datasource

Cada instalación de WildFly debe tener un datasource con JNDI `java:/personalfinancesDS`.

**Opción A — Para producción / servidores remotos**: configurar en `standalone.xml` dentro de `<datasources>`:

```xml
<datasource jndi-name="java:/personalfinancesDS"
            pool-name="personalfinancesDS">
    <connection-url>jdbc:mysql://localhost:3306/pfindesa</connection-url>
    <driver-class>com.mysql.cj.jdbc.Driver</driver-class>
    <driver>mysql</driver>
    <security>
        <user-name>pfindesa</user-name>
        <password>pfindesa</password>
    </security>
    <pool>
        <min-pool-size>5</min-pool-size>
        <max-pool-size>20</max-pool-size>
    </pool>
</datasource>
```

**Opción B — Para desarrollo local**: crear el archivo `src/main/webapp/WEB-INF/personalfinances-ds.xml` con el mismo contenido. Este archivo está en `.gitignore` (no se versiona), por lo que solo existe en tu máquina local. Al buildear el WAR, Maven lo incluye automáticamente. No hace falta tocar `standalone.xml`.

### 3.4 Configurar JAVA_OPTS

Agregar al final de `WILDFLY_HOME/bin/standalone.conf.bat`:

```batch
set "JAVA_OPTS=%JAVA_OPTS% -DAPP_CRYPTO_KEY=J4lZ8P2uZ0XzvGJ7v8gZ1m5xF0zZ3rQJmF7b2S1e8Yw= -Dspring.profiles.active=wildfly"
```

Para desarrollo local, agregar `-Dspring.jpa.hibernate.ddl-auto=update`:

```batch
set "JAVA_OPTS=%JAVA_OPTS% -DAPP_CRYPTO_KEY=J4lZ8P2uZ0XzvGJ7v8gZ1m5xF0zZ3rQJmF7b2S1e8Yw= -Dspring.profiles.active=wildfly -Dspring.jpa.hibernate.ddl-auto=update"
```

### 3.5 Despliegue del WAR

- **Manual**: copiar `target/personalfinances.war` a `WILDFLY_HOME/standalone/deployments/`
- **IntelliJ**: configurar Run Server con WildFly y deployar via management API (no dejar el WAR fijo en `deployments/`)

---

## 4. Recomendaciones

- El usuario debe tener el mismo nombre que la base.
- Mantener las contraseñas en un archivo `.env` si se usan herramientas como Docker o un gestor de secretos.
- No usar `ddl-auto=create` en producción (peligroso).
- Hacer backups periódicos.


