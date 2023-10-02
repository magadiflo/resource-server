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

## Clase de configuración del Resource Server

Creamos la clase de configuración del **Resource Server** y más abajo explicamos cómo extraer el `claims roles` que
agregamos en el **Servidor de Autorización**:

````java

@EnableMethodSecurity //prePostEnabled = true (default)
@EnableWebSecurity
@Configuration
public class ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(JwtDecoders.fromIssuerLocation(this.issuerUri)))
                )
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // roles, debe ser el mismo nombre que le pusimos en el authorization server cuando agregamos el claim "roles" al access_token
        jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        // Lo ponemos vacío "", porque nuestros roles ya tienen "ROLE_" como prefijo: ROLE_ADMIN, ROLE_USER
        // Si no lo seteamos a vacío "", por defecto le concatenará "SCOPE_"
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

}
````

**DONDE**

- `@EnableMethodSecurity`, habilita la seguridad del método en Spring Security. Esta anotación trae por defecto
  el `prePostEnabled = true`. En resumen, con la anotación `@EnableMethodSecurity` y con la propiedad `prePostEnabled`
  en `true` podemos hacer uso de la anotación `@PreAuthorize("...")` para segurizar los métodos. También hay otras
  propiedades como el `PostAuthorize, PreFilter y PostFilter`, pero para nuestro caso, solo requerimos usar
  el `PreAuthorize`.
- `@EnableWebSecurity`, nos permite personalizar la configuración de seguridad de la aplicación.

### Configurando la seguridad de nuestros endpoint

El primer `@Bean` que configuramos es el `SecurityFilterChain` donde definimos que todas las solicitudes requieren sí o
sí estar autenticados para poder acceder a algún endpoint. Además, estamos usando el método `oauth2ResourceServer()`
con el que configuramos la compatibilidad con el **Resource Server 2.0**, e internamente estamos configurando
un convertidor de autenticación JWT Personalizado.

### Extracción Manual de Authorities (Roles)

Spring Security incluye `JwtAuthenticationConverter`, que se encarga de **convertir un Jwt en una autenticación.**
Por defecto, Spring Security conectará el JwtAuthenticationProvider con una instancia por defecto de
JwtAuthenticationConverter.

Como parte de la configuración de un JwtAuthenticationConverter, puede suministrar un convertidor subsidiario para
**pasar de Jwt a una Colección de Granted Authorities**

Digamos que su **Servidor de Autorización** comunica authorities (o roles en nuestro caso) en un **claim personalizado**
llamado `roles`. En ese caso, puedes **configurar el claim** que JwtAuthenticationConverter debe **inspeccionar**,
esa configuración lo podemos ver en el método `jwtAuthenticationConverter()`.