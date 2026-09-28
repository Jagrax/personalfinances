# PFIN - Configuración de Base de Datos (DESA / PROD)

Este proyecto utiliza dos bases de datos distintas para los entornos de desarrollo y producción.

Los nombres concretos de cada base son locales de cada instalación y **no se versionan**: en los
ejemplos de abajo se usan `<db_desarrollo>` y `<db_produccion>` como placeholders. Reemplazalos por
los tuyos.

| Entorno    | Base de datos     | Usuario           |
| ---------- | ----------------- | ----------------- |
| Desarrollo | `<db_desarrollo>` | `<usuario_desarrollo>` |
| Producción | `<db_produccion>` | `<usuario_produccion>`  |

---

## 1. Creación de bases de datos y usuarios

Ejecutar este script en tu servidor MySQL (reemplazando los placeholders, y usando contraseñas
reales fuertes):

```sql
-- Base de datos de desarrollo
CREATE DATABASE IF NOT EXISTS <db_desarrollo> CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '<usuario_desarrollo>'@'localhost' IDENTIFIED BY '<password_desarrollo>';
GRANT ALL PRIVILEGES ON <db_desarrollo>.* TO '<usuario_desarrollo>'@'localhost';

-- Base de datos de producción
CREATE DATABASE IF NOT EXISTS <db_produccion> CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '<usuario_produccion>'@'localhost' IDENTIFIED BY '<password_produccion>';
GRANT ALL PRIVILEGES ON <db_produccion>.* TO '<usuario_produccion>'@'localhost';

FLUSH PRIVILEGES;
```

> Nunca uses una contraseña igual al nombre del usuario. Las credenciales reales viven en
> `standalone.xml` (producción) o en `personalfinances-ds.xml` (local, gitignored).

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
    <connection-url>jdbc:mysql://localhost:3306/&lt;db_produccion&gt;</connection-url>
    <driver>mysql</driver>
    <pool>
        <min-pool-size>5</min-pool-size>
        <max-pool-size>20</max-pool-size>
    </pool>
    <security user-name="&lt;usuario&gt;" password="&lt;password&gt;"/>
</datasource>
```

> **⚠️ Importante — Las sintaxis NO son intercambiables**: En `standalone.xml` (schema `urn:jboss:domain:datasources:7.1`, WildFly 30+) las credenciales van en `<security user-name="..." password="..."/>` (self-closing con atributos). En el archivo `-ds.xml` (schema IronJacamar) van como `<security><user-name>...</user-name><password>...</password></security>` (elementos hijo). **No sirve poner la sintaxis de uno en el otro**, aunque el XML sea válido cada schema parsea distinto.

**Opción B — Para desarrollo local**: crear el archivo `src/main/webapp/WEB-INF/personalfinances-ds.xml`. La sintaxis es distinta: las credenciales van dentro de un tag `<security>` como elementos hijo:

```xml
<datasource jndi-name="java:/personalfinancesDS"
            pool-name="personalfinancesDS">
    <connection-url>jdbc:mysql://localhost:3306/&lt;db_desarrollo&gt;</connection-url>
    <driver>mysql</driver>
    <security>
        <user-name>&lt;usuario_desarrollo&gt;</user-name>
        <password>&lt;password_desarrollo&gt;</password>
    </security>
    <pool>
        <min-pool-size>5</min-pool-size>
        <max-pool-size>20</max-pool-size>
    </pool>
</datasource>
```

Este archivo está en `.gitignore` (no se versiona), por lo que solo existe en tu máquina local. Al buildear el WAR, Maven lo incluye automáticamente. No hace falta tocar `standalone.xml`.

### 3.4 Configurar Gemini API Key

Agregar en `standalone.xml` dentro de `<server>`, después de `</extensions>`:

```xml
<system-properties>
    <property name="app.ai.gemini.api-keys" value="AIzaKey1,AIzaKey2,AIzaKey3"/>
</system-properties>
```

Se rotan automáticamente en round-robin. Cuantas más keys, más cuota diaria disponible (1.500/día por key).

### 3.5 Configurar JAVA_OPTS

Agregar al final de `WILDFLY_HOME/bin/standalone.conf.bat`:

```batch
set "JAVA_OPTS=%JAVA_OPTS% -Dspring.profiles.active=wildfly"
```

### 3.6 Maven: perfil `release`

Se agregó un perfil de Maven llamado `release` que se activa automáticamente cuando ejecutás el build con `-DskipTests` (que es necesario porque los tests no pueden correr fuera del WildFly).

Cuando el perfil está activo, el `maven-war-plugin` excluye del WAR el archivo `WEB-INF/personalfinances-ds.xml`. Esto evita que el datasource definido dentro del WAR entre en conflicto con el que ya está configurado en `standalone.xml` de producción.

| Comando / Acción | ¿Perfil activo? | WAR contiene -ds.xml | ¿Para qué sirve? |
|---|---|---|---|
| `mvn package` (o build desde IntelliJ) | No | Sí | Desarrollo local con WildFly dev |
| `mvn package -DskipTests` | Sí | No | Release a producción |

### 3.7 Cómo hacer cada build desde IntelliJ

**A — Desarrollo (deploy a WildFly dev desde IntelliJ)**

No requiere ningún cambio. Simplemente ejecutás la configuración de Run/Debug del servidor WildFly como siempre. IntelliJ corre `mvn package` internamente (sin `-DskipTests`), el perfil `release` no se activa, el WAR incluye el `-ds.xml`, y el datasource se registra automáticamente en el servidor dev.

**B — Release a producción (generar el WAR con `mvn package -DskipTests`)**

Cuando quieras generar el WAR para producción, tenés dos opciones desde IntelliJ. El destino del
deploy **no está hardcodeado en el repo**: se pasa por línea de comandos con `-Dwildfly.deploy.dir=<ruta>`
(si no se pasa, el build no copia el WAR a ningún lado).

1. **Opcion recomendada — Run Configuration Maven** (una vez creada, es solo un click):
   - `Run → Edit Configurations...`
   - Click `+` → `Maven`
   - Name: `personalfinances release`
   - Working directory: la carpeta raíz del proyecto
   - Command line: `package -DskipTests -Dwildfly.deploy.dir=<standalone/deployments del WildFly de producción>`
   - Aceptar
   - A partir de ahora, para release ejecutás esa configuración (el botón de Run con el nombre `personalfinances release` seleccionado)

2. **Maven tool window (una vez, sin crear configuración)**:
   - Abrir la ventana `Maven` (View → Tool Windows → Maven, o el costado derecho)
   - Click en el ícono `Execute Maven Goal` (una `m` con una flechita, o `Ctrl+Alt+Shift+X` → `Ctrl+Alt+Shift+X` de vuelta para goals)
   - Escribir: `package -DskipTests -Dwildfly.deploy.dir=<ruta>`
   - Enter

Con cualquiera de las dos, el perfil `release` se activa por el `-DskipTests`, se excluye el `-ds.xml` del WAR, y el `maven-antrun-plugin` copia el WAR a la carpeta de producción si y solo si definiste `wildfly.deploy.dir`.

---

## 4. Recomendaciones

- No usar contraseñas iguales al nombre del usuario ni de la base.
- Mantener las contraseñas en un archivo `.env` si se usan herramientas como Docker o un gestor de secretos.
- No usar `ddl-auto=create` en producción (peligroso).
- Hacer backups periódicos.
- **No versionar rutas de tu máquina**: installs de MySQL/WildFly, carpetas de deployments y
  nombres de bases van por system property (`standalone.xml`) o por línea de comandos
  (`-Dwildfly.deploy.dir=...`). Criterio: si el valor depende de dónde corre, no va en el repo.


