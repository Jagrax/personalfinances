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

**Opción A — Para producción / servidores remotos**: configurar en `standalone.xml` dentro de `<datasources>`. Las credenciales van en un tag `<security>` **self-closing** con atributos `user-name` y `password`:

```xml
<datasource jndi-name="java:/personalfinancesDS"
            pool-name="personalfinancesDS">
    <connection-url>jdbc:mysql://localhost:3306/pfindesa</connection-url>
    <driver>mysql</driver>
    <pool>
        <min-pool-size>5</min-pool-size>
        <max-pool-size>20</max-pool-size>
    </pool>
    <security user-name="pfindesa" password="pfindesa"/>
</datasource>
```

> **⚠️ Importante — Las sintaxis NO son intercambiables**: En `standalone.xml` (schema `urn:jboss:domain:datasources:7.1`, WildFly 30+) las credenciales van en `<security user-name="..." password="..."/>` (self-closing con atributos). En el archivo `-ds.xml` (schema IronJacamar) van como `<security><user-name>...</user-name><password>...</password></security>` (elementos hijo). **No sirve poner la sintaxis de uno en el otro**, aunque el XML sea válido cada schema parsea distinto.

**Opción B — Para desarrollo local**: crear el archivo `src/main/webapp/WEB-INF/personalfinances-ds.xml`. La sintaxis es distinta: las credenciales van dentro de un tag `<security>` como elementos hijo:

```xml
<datasource jndi-name="java:/personalfinancesDS"
            pool-name="personalfinancesDS">
    <connection-url>jdbc:mysql://localhost:3306/pfindesa</connection-url>
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

Este archivo está en `.gitignore` (no se versiona), por lo que solo existe en tu máquina local. Al buildear el WAR, Maven lo incluye automáticamente. No hace falta tocar `standalone.xml`.

### 3.4 Configurar JAVA_OPTS

Agregar al final de `WILDFLY_HOME/bin/standalone.conf.bat`:

```batch
set "JAVA_OPTS=%JAVA_OPTS% -DAPP_CRYPTO_KEY=J4lZ8P2uZ0XzvGJ7v8gZ1m5xF0zZ3rQJmF7b2S1e8Yw= -Dspring.profiles.active=wildfly"
```

Para desarrollo local, agregar `-Dspring.jpa.hibernate.ddl-auto=update`:

```batch
set "JAVA_OPTS=%JAVA_OPTS% -DAPP_CRYPTO_KEY=J4lZ8P2uZ0XzvGJ7v8gZ1m5xF0zZ3rQJmF7b2S1e8Yw= -Dspring.profiles.active=wildfly -Dspring.jpa.hibernate.ddl-auto=update"
```

### 3.5 Maven: perfil `release`

Se agregó un perfil de Maven llamado `release` que se activa automáticamente cuando ejecutás el build con `-DskipTests` (que es necesario porque los tests no pueden correr fuera del WildFly).

Cuando el perfil está activo, el `maven-war-plugin` excluye del WAR el archivo `WEB-INF/personalfinances-ds.xml`. Esto evita que el datasource definido dentro del WAR entre en conflicto con el que ya está configurado en `standalone.xml` de producción.

| Comando / Acción | ¿Perfil activo? | WAR contiene -ds.xml | ¿Para qué sirve? |
|---|---|---|---|
| `mvn package` (o build desde IntelliJ) | No | Sí | Desarrollo local con WildFly dev |
| `mvn package -DskipTests` | Sí | No | Release a producción |

### 3.6 Cómo hacer cada build desde IntelliJ

**A — Desarrollo (deploy a WildFly dev desde IntelliJ)**

No requiere ningún cambio. Simplemente ejecutás la configuración de Run/Debug del servidor WildFly como siempre. IntelliJ corre `mvn package` internamente (sin `-DskipTests`), el perfil `release` no se activa, el WAR incluye el `-ds.xml`, y el datasource se registra automáticamente en el servidor dev.

**B — Release a producción (generar el WAR con `mvn package -DskipTests`)**

Cuando quieras generar el WAR para producción (que se copia solo a `C:\wildfly-33.0.1.Final-pfinprod\standalone\deployments\`), tenés dos opciones desde IntelliJ:

1. **Opcion recomendada — Run Configuration Maven** (una vez creada, es solo un click):
   - `Run → Edit Configurations...`
   - Click `+` → `Maven`
   - Name: `personalfinances release`
   - Working directory: `C:\Users\Jagrax\Workspace\personalfinances`
   - Command line: `package -DskipTests`
   - Aceptar
   - A partir de ahora, para release ejecutás esa configuración (el botón de Run con el nombre `personalfinances release` seleccionado)

2. **Maven tool window (una vez, sin crear configuración)**:
   - Abrir la ventana `Maven` (View → Tool Windows → Maven, o el costado derecho)
   - Click en el ícono `Execute Maven Goal` (una `m` con una flechita, o `Ctrl+Alt+Shift+X` → `Ctrl+Alt+Shift+X` de vuelta para goals)
   - Escribir: `package -DskipTests`
   - Enter

Con cualquiera de las dos, el perfil `release` se activa por el `-DskipTests`, se excluye el `-ds.xml` del WAR, y el `maven-antrun-plugin` copia el WAR a la carpeta de producción automáticamente.

---

## 4. Recomendaciones

- El usuario debe tener el mismo nombre que la base.
- Mantener las contraseñas en un archivo `.env` si se usan herramientas como Docker o un gestor de secretos.
- No usar `ddl-auto=create` en producción (peligroso).
- Hacer backups periódicos.


