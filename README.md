# Spring Boot 3 - OAuth2.0 Authorization Server y Resource Server - Angular

- Tutorial tomado del canal de youtube de Luigi Code.
- Documentación Oficial
  [OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

---

## Configuraciones iniciales

Se muestran las dependencias que se usarán para nuestro proyecto **Resource Server**:

````xml
<!--Spring Boot 3.1.4-->
<!--Java 17-->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
````

Agregamos algunas configuraciones en el **application.yml**. Nuestro **Resource Server** estará corriendo en el
**puerto por defecto 8080**. También agregamos dos configuraciones TRACE para que nos muestre información en consola.
Finalmente, en la última configuración definimos la url donde está corriendo nuestro **Authorization Server**:

````yml
server:
  port: 8080

logging:
  level:
    org.springframework.security: trace
    org.springframework.security.oauth2: trace

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9000
````
