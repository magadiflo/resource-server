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

## Creando controlador con endpoints segurizados

Creamos dos endpoints, donde el endpoint `/admin` podrá ser accedido únicamente por usuarios que tengan el rol de
`ROLE_ADMIN`, esto es gracias a que dicho endpoint lo anotamos con `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`.
Por otro lado, el endpoint `/user`, únicamente podrá ser accedido por usuarios que estén autenticados,
independientemente del rol que tengan, ya que ese endpoint no lo anotamos con el `@PreAuthorize(...)` pero en la clase
de configuración `ResourceServerConfig` dijimos que todas las solicitudes debían estar autenticadas
`authorize.anyRequest().authenticated()`:

````java

@RestController
@RequestMapping(path = "/api/v1/resources")
public class ResourceController {
    @GetMapping(path = "/user")
    public ResponseEntity<MessageDTO> user(Authentication authentication) {
        return ResponseEntity.ok(new MessageDTO("Hola user, " + authentication.getName()));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping(path = "/admin")
    public ResponseEntity<MessageDTO> admin(Authentication authentication) {
        return ResponseEntity.ok(new MessageDTO("Hola admin, " + authentication.getName()));
    }
}
````

Finalmente, el controlador anterior usa un DTO al que le llamamos `MessageDTO`:

````java
public record MessageDTO(String message) {
}
````

## Accediendo al Resource Server con usuario registrado en el Authorization Server

Primero, es importante tener levantado el `Servidor de Autorización` ya que nuestro servidor de recursos se comunica con
él. Luego, utilizando la página [oauthdebugger/debug](https://oauthdebugger.com/debug) solicitamos un
`Código de Autorización`, para eso nos logueamos con el usuario `user` y pass `12345`. Luego de obtener el código de
autorización llamamos al endpoint para solicitar un `access_token`:

````bash
curl -v -X POST -u front-end-app:secret-key -d "grant_type=authorization_code&client_id=front-end-app&redirect_uri=https://oauthdebugger.com/debug&code_verifier=KO8EAHEMWGAhUkGtuH5izR3cgCFSZFyEOIwZFNtyxx0&code=b9f4ww-yGcJ-WnQm64A17MXWkr2of9I2yjCvn2lG-rHjfyMOMkF13CLEFyg34rjE4MhVHjEFkhH8I3w1d87AatmiqbB6UTgi8GuOgwIKiuSsFi_NApCGmSapWWRT-zjY" http://localhost:9000/oauth2/token | jq

> POST /oauth2/token HTTP/1.1
> Host: localhost:9000
> Authorization: Basic ZnJvbnQtZW5kLWFwcDpzZWNyZXQta2V5
> Content-Type: application/x-www-form-urlencoded
>
< HTTP/1.1 200
<
{
  "access_token": "eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIiwiYXVkIjoiZnJvbnQtZW5kLWFwcCIsIm5iZiI6MTY5NjI3MjEwMCwic2NvcGUiOlsib3BlbmlkIl0sInJvbGVzIjpbIlJPTEVfVVNFUiJdLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjkwMDAiLCJleHAiOjE2OTYyNzI0MDAsInRva2VuX3R5cGUiOiJhY2Nlc3MgdG9rZW4iLCJpYXQiOjE2OTYyNzIxMDAsInVzZXJuYW1lIjoidXNlciJ9.oGVGbzp6it_Zc8ZgofTADqACpPB6IbzpeuRlT0nLLe5xxjZ4oWklzIpUmMQ4KCV5VTINrUjjP0uAgF9lC1VVFQj8ihulrz-hJrZI4qzhwTL2WXU4UiCNi7azdYuJ0BqSZdoR6nFEnOGaxA7o5j37sRFpGlWWhDGAaoZ-vMOXWihZE-X4XuVjuITGJytzeIzy_zHavbpJi0r6V414ZgTlQGTTSHJKf-deVqWdmrxyh0ejL38Wwz2u3NDJ2cJ4IBTid8OeffgJrwUNx05eWtgZ3M6iLiItrV1FWcmVBr-knUcpg_npi4n0zVeVJOcT6iJzTyIFWq8Vz_xhhG71AiX6ug",
  "refresh_token": "BxvnvMvyQB5f6wNkEKb7dKq2dTU7xw2ZMXhW_TZpxMbz4Kfs-Ig6kFIFBSBGGFHGdZ-s2709T5W6APVpQKKTXhSvmxsdVhgEDKIkmGl_03f37_YpA6HBoGoYec3TqWJ0",
  "scope": "openid",
  "id_token": "eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIiwiYXVkIjoiZnJvbnQtZW5kLWFwcCIsImF6cCI6ImZyb250LWVuZC1hcHAiLCJhdXRoX3RpbWUiOjE2OTYyNzIwMTMsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMCIsImV4cCI6MTY5NjI3MzkwMCwidG9rZW5fdHlwZSI6ImlkIHRva2VuIiwiaWF0IjoxNjk2MjcyMTAwLCJub25jZSI6Inc3aXl6em9yNmdhIiwic2lkIjoiTEV6WVZtLUxKamJPSEwyYTJoT2o0NjRYVFQxM0xmVVRkRy1FRDF0bHktdyJ9.B37t9cOQ7YFqg4Ig_7_KgjMuP-_dUFMn1vImIKhAQI22thh4oKGSqrxVaNXh-fIn4HKx1jMgaPD8s00tlawMujHdINR1OnT644Yr_42WUOhXuWPFF_deKx22YDLfla2gDUZyV95QsbW--ZnSXb9DW3wgqFpKo11HZdSMcBEe_apFIVaUoLtw1OPt1QFKZP5dNsZ2iulaks54iuN2lDZVE1N859IqhwNiKjcTjGy89KM-ysvPQFI14rfp_mnIvhrcodod-NcX3AEq4giYZL0hro3LILa4DyC5padPwU7uKhYLA43dlJ-F2rr7HUKQExNOSnce1_DzBfx2AtdQHD0epg",
  "token_type": "Bearer",
  "expires_in": 300
}
````

Listo, ahora que obtuvimos el `access_token` para el usuario `user`, ejecutamos nuestra aplicación `Resource Server` y
realizamos una petición a los dos endpoints del servidor:

No podemos acceder al endpoint `/admin` con el usuario `user` ya que este solo tiene el rol de `ROLE_USER` y se
requiere para este endpoint el rol `ROLE_ADMIN`, por lo que el servidor responderá con un `403 Forbidden`:

````bash
curl -v -H "Authorization: Bearer eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIiwiYXVkIjoiZnJvbnQtZW5kLWFwcCIsIm5iZiI6MTY5NjI3MjEwMCwic2NvcGUiOlsib3BlbmlkIl0sInJvbGVzIjpbIlJPTEVfVVNFUiJdLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjkwMDAiLCJleHAiOjE2OTYyNzI0MDAsInRva2VuX3R5cGUiOiJhY2Nlc3MgdG9rZW4iLCJpYXQiOjE2OTYyNzIxMDAsInVzZXJuYW1lIjoidXNlciJ9.oGVGbzp6it_Zc8ZgofTADqACpPB6IbzpeuRlT0nLLe5xxjZ4oWklzIpUmMQ4KCV5VTINrUjjP0uAgF9lC1VVFQj8ihulrz-hJrZI4qzhwTL2WXU4UiCNi7azdYuJ0BqSZdoR6nFEnOGaxA7o5j37sRFpGlWWhDGAaoZ-vMOXWihZE-X4XuVjuITGJytzeIzy_zHavbpJi0r6V414ZgTlQGTTSHJKf-deVqWdmrxyh0ejL38Wwz2u3NDJ2cJ4IBTid8OeffgJrwUNx05eWtgZ3M6iLiItrV1FWcmVBr-knUcpg_npi4n0zVeVJOcT6iJzTyIFWq8Vz_xhhG71AiX6ug" http://localhost:8080/api/v1/resources/admin | jq

>
< HTTP/1.1 403
< WWW-Authenticate: Bearer error="insufficient_scope", error_description="The request requires higher privileges than provided by the access token.", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1"
<
````

En el siguiente ejemplo vemos que sí podemos acceder al endpoint `/user` ya que solo se requiere estar autenticado:

````bash
curl -v -H "Authorization: Bearer eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyIiwiYXVkIjoiZnJvbnQtZW5kLWFwcCIsIm5iZiI6MTY5NjI3MjEwMCwic2NvcGUiOlsib3BlbmlkIl0sInJvbGVzIjpbIlJPTEVfVVNFUiJdLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjkwMDAiLCJleHAiOjE2OTYyNzI0MDAsInRva2VuX3R5cGUiOiJhY2Nlc3MgdG9rZW4iLCJpYXQiOjE2OTYyNzIxMDAsInVzZXJuYW1lIjoidXNlciJ9.oGVGbzp6it_Zc8ZgofTADqACpPB6IbzpeuRlT0nLLe5xxjZ4oWklzIpUmMQ4KCV5VTINrUjjP0uAgF9lC1VVFQj8ihulrz-hJrZI4qzhwTL2WXU4UiCNi7azdYuJ0BqSZdoR6nFEnOGaxA7o5j37sRFpGlWWhDGAaoZ-vMOXWihZE-X4XuVjuITGJytzeIzy_zHavbpJi0r6V414ZgTlQGTTSHJKf-deVqWdmrxyh0ejL38Wwz2u3NDJ2cJ4IBTid8OeffgJrwUNx05eWtgZ3M6iLiItrV1FWcmVBr-knUcpg_npi4n0zVeVJOcT6iJzTyIFWq8Vz_xhhG71AiX6ug" http://localhost:8080/api/v1/resources/user | jq

>
< HTTP/1.1 200
<
{
  "message": "Hola user, user"
}
````

Ahora queremos acceder usando el usuario `admin`, pero debemos hacer `LogOut` de la sessión anterior, sino no nos
mostrará el formulario de Login:

![1-logout-authorization-server](./assets/1-logout-authorization-server.png)

A continuación generamos el `access_token` para el usuario `admin`:

````bash
curl -v -X POST -u front-end-app:secret-key -d "grant_type=authorization_code&client_id=front-end-app&redirect_uri=https://oauthdebugger.com/debug&code_verifier=Oyu0JeCDoTDgAGt7tbDRqpVxjgALce2Y2QZLYAYXC2T&code=wDzIFa-92coGRbpymHT1pkItE9J-om1GJ2PZmglSpKcbCJfrS82KiEbdCm_ZtZQJlBbF62wrQVa0SjZzj86XYd6WSKgiPST3SC8AHqQDdDIQ7H8A8wJUVF9zybU26hzW" http://localhost:9000/oauth2/token | jq

< HTTP/1.1 200
<
{
  "access_token": "eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImF1ZCI6ImZyb250LWVuZC1hcHAiLCJuYmYiOjE2OTYyNzI5ODksInNjb3BlIjpbIm9wZW5pZCJdLCJyb2xlcyI6WyJST0xFX1VTRVIiLCJST0xFX0FETUlOIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMCIsImV4cCI6MTY5NjI3MzI4OSwidG9rZW5fdHlwZSI6ImFjY2VzcyB0b2tlbiIsImlhdCI6MTY5NjI3Mjk4OSwidXNlcm5hbWUiOiJhZG1pbiJ9.bPoDb2tND6nzlUiY_oMUZJRBrXNg1WHa2G0-U5fb3YD2CM-o67h3CSAERcDAZovuX0cCTrgv9_ZUqVPiUYv5YdYs8Pr4R6u95GapYcG4WTt978IqoAcofs2nhZyMeTZxA5DZkKgf2kzG6tLUjMrInXRONwmgAvHoj-f3NPumLxNtFdWHQAn53YxMEcZEplCZoWhFil5w4_Mg0n5a_OLpOAxpLOuJx6f11EfEaciWeNsvUt2rlO_H8EAZLihDlyvHuswVrKwCh0qAbQQkofuecqRDANj-qrfYeA8CW_CmKzBhaKS4Res-mtlFO8oKcln7aO66mK7szCWnkhbJFPmkGQ",
  "refresh_token": "DNw0dJqYiPSUkrdbfn3GuPBkntzg7ReiV1cmViAVL_P4hUVvAs1Dz2iI0M4LEnyoZwX2FIcu5Wlj9INs9TAfH42Qlp3MEUgexYcbDjjVqeeBxXJbJbTVy1eQDBJGav65",
  "scope": "openid",
  "id_token": "eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImF1ZCI6ImZyb250LWVuZC1hcHAiLCJhenAiOiJmcm9udC1lbmQtYXBwIiwiYXV0aF90aW1lIjoxNjk2MjcyOTU0LCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjkwMDAiLCJleHAiOjE2OTYyNzQ3ODksInRva2VuX3R5cGUiOiJpZCB0b2tlbiIsImlhdCI6MTY5NjI3Mjk4OSwibm9uY2UiOiJleWJldjVzbW1tYiIsInNpZCI6Ik14NWlYRkJ3aXBpd2MzYmF3OGxVX3pNQ3B5OVVkSEpjQUhFY25seGhHeDgifQ.aQhfgdt2eX2hNmhKyjOa1t_mxOBBlqP11OgcqHvo93b3dhTbUq9TeL4bIrswAoA9h5xDkRXsW4U-UYszw7zKu8DMt-PhmkcsLiGNgHduWguUQBeEwpkib1jqVjJbdJBhyV1bR56dCkA89kKJuJ8xqcfO7POnST1qc4Ic-SJOLiMNTzpm3mzj33Gc11q1mIikv9urG0ueeFocJIZ1oOq6WyviHclQbnUbQxKTfmRqnvtPAo9IH1A5XUoN9gVHc9FIcg9c1dUR9kQDdaVo3Z_BXwBx0BCoAq2BUF_Gpn3BIAV77SCerDJ6EqPuEwq8vtuxKKqTzF4Q7sNi_kxweKicWQ",
  "token_type": "Bearer",
  "expires_in": 300
}
````

y procedemos a acceder al endpoint `/admin`; vemos que sí se puede, ya que él si tiene el rol `ROLE_ADMIN`:

````bash
curl -v -H "Authorization: Bearer eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImF1ZCI6ImZyb250LWVuZC1hcHAiLCJuYmYiOjE2OTYyNzI5ODksInNjb3BlIjpbIm9wZW5pZCJdLCJyb2xlcyI6WyJST0xFX1VTRVIiLCJST0xFX0FETUlOIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMCIsImV4cCI6MTY5NjI3MzI4OSwidG9rZW5fdHlwZSI6ImFjY2VzcyB0b2tlbiIsImlhdCI6MTY5NjI3Mjk4OSwidXNlcm5hbWUiOiJhZG1pbiJ9.bPoDb2tND6nzlUiY_oMUZJRBrXNg1WHa2G0-U5fb3YD2CM-o67h3CSAERcDAZovuX0cCTrgv9_ZUqVPiUYv5YdYs8Pr4R6u95GapYcG4WTt978IqoAcofs2nhZyMeTZxA5DZkKgf2kzG6tLUjMrInXRONwmgAvHoj-f3NPumLxNtFdWHQAn53YxMEcZEplCZoWhFil5w4_Mg0n5a_OLpOAxpLOuJx6f11EfEaciWeNsvUt2rlO_H8EAZLihDlyvHuswVrKwCh0qAbQQkofuecqRDANj-qrfYeA8CW_CmKzBhaKS4Res-mtlFO8oKcln7aO66mK7szCWnkhbJFPmkGQ" http://localhost:8080/api/v1/resources/admin | jq

>
< HTTP/1.1 200
<
{
  "message": "Hola admin, admin"
}
````

Lo mismo pasa si accedemos con `admin` al endpoint `/user`, ya que aquí solo se requiere que esté autenticado:

````bash
curl -v -H "Authorization: Bearer eyJraWQiOiIyN2MxMzk2Ny0wNDIxLTRmMzgtOWJhYy1iNDM4MDVkNmY5OGUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImF1ZCI6ImZyb250LWVuZC1hcHAiLCJuYmYiOjE2OTYyNzI5ODksInNjb3BlIjpbIm9wZW5pZCJdLCJyb2xlcyI6WyJST0xFX1VTRVIiLCJST0xFX0FETUlOIl0sImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMCIsImV4cCI6MTY5NjI3MzI4OSwidG9rZW5fdHlwZSI6ImFjY2VzcyB0b2tlbiIsImlhdCI6MTY5NjI3Mjk4OSwidXNlcm5hbWUiOiJhZG1pbiJ9.bPoDb2tND6nzlUiY_oMUZJRBrXNg1WHa2G0-U5fb3YD2CM-o67h3CSAERcDAZovuX0cCTrgv9_ZUqVPiUYv5YdYs8Pr4R6u95GapYcG4WTt978IqoAcofs2nhZyMeTZxA5DZkKgf2kzG6tLUjMrInXRONwmgAvHoj-f3NPumLxNtFdWHQAn53YxMEcZEplCZoWhFil5w4_Mg0n5a_OLpOAxpLOuJx6f11EfEaciWeNsvUt2rlO_H8EAZLihDlyvHuswVrKwCh0qAbQQkofuecqRDANj-qrfYeA8CW_CmKzBhaKS4Res-mtlFO8oKcln7aO66mK7szCWnkhbJFPmkGQ" http://localhost:8080/api/v1/resources/user | jq

< HTTP/1.1 200
<
{
  "message": "Hola user, admin"
}
````

Finalmente, qué pasa si tratamos de acceder a cualquiera de los dos endpoints sin enviarle un `access_token`, pues
el servidor nos responderá con un `401 Unauthorizaed`:

````bash
curl -v http://localhost:8080/api/v1/resources/user | jq

< HTTP/1.1 401
< Set-Cookie: JSESSIONID=7C0A03A3750B3BBE317C58C4A6E34CEC; Path=/; HttpOnly
< WWW-Authenticate: Bearer
````

## Accediendo al Resource Server con Social Login de Google

En el `Authorization Server` **CAPÍTULO 5**, se implementó la funcionalidad de poder autenticarse usando el
**Social Login de google**. En este apartado verificaremos dicha funcionalidad y haremos algunos ajustes a nuestros
endpoints.

Teniendo en ejecución los dos proyectos **(Authorization Server y Resource Server)** debemos obtener un `access_token`
para un usuario que se loguee con su cuenta de google. Para eso, realizamos el flujo de siempre, pero esta vez
accediendo con nuestra cuenta de google para obtener un `Authorization Code`:

![2-flow-authorization-code](./assets/2-flow-authorization-code.png)

Una vez obtenido el **authorization code**, realizamos la petición para solicitar un `access_token`:

````bash
curl -v -X POST -u front-end-app:secret-key -d "grant_type=authorization_code&client_id=front-end-app&redirect_uri=https://oauthdebugger.com/debug&code_verifier=kQb9sK4xY9dEiQt7EOABhQD15wtb8g4wujR4ZHsEbdK&code=tFgEv092rQCnqa9tK6tfSI8Q_wVODkNLBCoJ442LQFtmcdPb6ZmNonpo6ZZ7daJOYfuDPkXhgHmWh39bB_wzrMRiYW2yGNnMuM8exq7tRoKIIs41Em7c_bRb6IRYxvJd" http://localhost:9000/oauth2/token | jq

> POST /oauth2/token HTTP/1.1
> Host: localhost:9000
> Authorization: Basic ZnJvbnQtZW5kLWFwcDpzZWNyZXQta2V5
> Content-Type: application/x-www-form-urlencoded
>
< HTTP/1.1 200
<
{
  "access_token": "eyJraWQiOiI4MTEzYjVjMC1lMWYxLTQxNDYtOGRmZi1iOGIyNDY5MDVlYWUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJtYWdhZGlmbG9AZ21haWwuY29tIiwiYXVkIjoiZnJvbnQtZW5kLWFwcCIsIm5iZiI6MTY5NjQzNDc2Nywic2NvcGUiOlsib3BlbmlkIl0sInJvbGVzIjpbIk9JRENfVVNFUiIsIlNDT1BFX29wZW5pZCIsIlNDT1BFX2h0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL2F1dGgvdXNlcmluZm8ucHJvZmlsZSIsIlNDT1BFX2h0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL2F1dGgvdXNlcmluZm8uZW1haWwiXSwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDAwIiwiZXhwIjoxNjk2NDM1MDY3LCJ0b2tlbl90eXBlIjoiYWNjZXNzIHRva2VuIiwiaWF0IjoxNjk2NDM0NzY3LCJ1c2VybmFtZSI6Im1hZ2FkaWZsb0BnbWFpbC5jb20ifQ.WLYqpVnx1ygLoRxxEVOS5G54xXPyeBpidkRI0Pjz344WU-xqSj59UIwzX9MSB087M6OBvP5paRYNRmSCt2hItdGKXwsXw40x27BuQmnjtJnfdQANC0giDXhpfU8CH6Uf4MBMbJJqyy_TlehGE125ZMUQDQadGcbXnqFE1H0iSWmvAiYc5DqVDvlJQE5NfFEiMZEue4Gndcx33oJVXB5arDgF4pq6xbLUqqwQHNYRoh9kmlilOCEdivMjr8BhgzQK92cK6IJwQ-Chetv1R1AJ2YPcJfPxSfKDmiWbZUdmUGJejolB13QajrnL7etFeh6ojku1-8HBYQ7heDiMZWunkA",
  "refresh_token": "Y-FkJYjN-r66YaDG6Wix3dhAJ1JxtTlCS3Fu4h1YXRbDMSkSdDARaTM1_XloWsLtAvkxAHfyX55Wt3mKqxqYZ2Rh_Dh4wdIKeWwn4RcI9se10MTvKMlaked_aNBtxnt5",
  "scope": "openid",
  "id_token": "eyJraWQiOiI4MTEzYjVjMC1lMWYxLTQxNDYtOGRmZi1iOGIyNDY5MDVlYWUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJtYWdhZGlmbG9AZ21haWwuY29tIiwiYXVkIjoiZnJvbnQtZW5kLWFwcCIsImF6cCI6ImZyb250LWVuZC1hcHAiLCJhdXRoX3RpbWUiOjE2OTY0MzQ2MTksImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6OTAwMCIsImV4cCI6MTY5NjQzNjU2NywidG9rZW5fdHlwZSI6ImlkIHRva2VuIiwiaWF0IjoxNjk2NDM0NzY3LCJub25jZSI6Imdid3F0YTIzNDlxIiwic2lkIjoiNEYtUFVKdUpneTNXNFFQWDJSWjVjUXp4RHFfQnJHSk1RNWJyZDZpWVdXWSJ9.OIjAEwNzRlXjf_QvPaqzGVZAdyaAp0kVtbAArr14FmB1VU9bLvtnQRz2dyxgs6zBd11pajONwFMuHQTyixSBfAkn2k18_b_eX8-mhUKuNYPD11rYfwWKv2pCy4RWxK_h8PoJALhxYYKvxs0ANcdgz3GsdbuM9QOZkTtE14_6Wy1C7waIZVT90kmMBMOqETWSdjbEU-x4Q2_WBZC_U7W69lVwW9uECdD2S_nJCAuJoYZCRCqpzF2t4WWKpEUqaFMGmZA54XUIp3ZASSbQySW0beWu56v7WvDQ8a08qzZ828UX90uRhn2DidTDeAq498iH_53KKb0c_-0NJjSZdwfbmQ",
  "token_type": "Bearer",
  "expires_in": 299
}
````

Con el `access_token` obtenido realizamos una solicitud al endpoint del **servidor de recurso**:

````bash
 curl -v -H "Authorization: Bearer eyJraWQiOiI4MTEzYjVjMC1lMWYxLTQxNDYtOGRmZi1iOGIyNDY5MDVlYWUiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJtYWdhZGlmbG9AZ21haWwuY29tIiwiYXVkIjoiZnJvbnQtZW5kLWFwcCIsIm5iZiI6MTY5NjQzNDc2Nywic2NvcGUiOlsib3BlbmlkIl0sInJvbGVzIjpbIk9JRENfVVNFUiIsIlNDT1BFX29wZW5pZCIsIlNDT1BFX2h0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL2F1dGgvdXNlcmluZm8ucHJvZmlsZSIsIlNDT1BFX2h0dHBzOi8vd3d3Lmdvb2dsZWFwaXMuY29tL2F1dGgvdXNlcmluZm8uZW1haWwiXSwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDAwIiwiZXhwIjoxNjk2NDM1MDY3LCJ0b2tlbl90eXBlIjoiYWNjZXNzIHRva2VuIiwiaWF0IjoxNjk2NDM0NzY3LCJ1c2VybmFtZSI6Im1hZ2FkaWZsb0BnbWFpbC5jb20ifQ.WLYqpVnx1ygLoRxxEVOS5G54xXPyeBpidkRI0Pjz344WU-xqSj59UIwzX9MSB087M6OBvP5paRYNRmSCt2hItdGKXwsXw40x27BuQmnjtJnfdQANC0giDXhpfU8CH6Uf4MBMbJJqyy_TlehGE125ZMUQDQadGcbXnqFE1H0iSWmvAiYc5DqVDvlJQE5NfFEiMZEue4Gndcx33oJVXB5arDgF4pq6xbLUqqwQHNYRoh9kmlilOCEdivMjr8BhgzQK92cK6IJwQ-Chetv1R1AJ2YPcJfPxSfKDmiWbZUdmUGJejolB13QajrnL7etFeh6ojku1-8HBYQ7heDiMZWunkA" http://localhost:8080/api/v1/resources/user | jq

>
< HTTP/1.1 200
<
{
  "message": "Hola user, magadiflo@gmail.com"
}
````

Como observamos en el resultado anterior pudimos ingresar al endpoint `/user`, ya que con la configuración actual del
resource server, este endpoint requiere únicamente que el usuario esté autenticado. Veamos qué contenido tiene el
`access_token` utilizado:

![3-jwt-decoded-social-login](./assets/3-jwt-decoded-social-login.png)

Vemos que uno de los roles que está presente en el **JWT** para el usuario que se logueó con su cuenta de google es
`OIDC_USER` por lo que usaremos ese rol para asegurar el endpoint `/user`:

````java

@RestController
@RequestMapping(path = "/api/v1/resources")
public class ResourceController {
    @PreAuthorize("hasAnyAuthority('OIDC_USER')")
    @GetMapping(path = "/user")
    public ResponseEntity<MessageDTO> user(Authentication authentication) {
        return ResponseEntity.ok(new MessageDTO("Hola user, " + authentication.getName()));
    }
    /*other method*/
}
````

Listo, si volvemos a ejecutar la aplicación y realizamos todo el flujo con el inicio de sesión de google hasta acceder
al endpoint `/user`, que ahora está restringido al rol `OIDC_USER`, veremos que todo sigue funcionando correctamente.

---

# CAPÍTULO 10: Accediendo al Resource Server desde cliente Angular - Agregando CORS

---

Este capítulo le corresponde a la aplicación cliente de Angular, pero necesitamos modificar el proyecto agregando el
`CORS` para que el cliente de Angular funcione sin problemas.

Creamos una clase de configuración, similar a la que creamos en el `Authorization Server` donde expondremos un bean de
cors:

````java

@Configuration
public class ApplicationConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(Collections.singletonList("http://localhost:4200"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization",
                "Access-Control-Allow-Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers",
                "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization",
                "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return new CorsFilter(source);
    }
}
````

Finalmente, en el método del `SecurityFilterChain` habilitamos el cors:

````java

@EnableMethodSecurity()
@EnableWebSecurity
@Configuration
public class ResourceServerConfig {
    /* other code */

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults());
        /* other code */
    }
    /* other code */
}
````
