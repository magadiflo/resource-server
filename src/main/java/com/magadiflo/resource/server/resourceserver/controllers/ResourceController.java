package com.magadiflo.resource.server.resourceserver.controllers;

import com.magadiflo.resource.server.resourceserver.dtos.MessageDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/resources")
public class ResourceController {
    @PreAuthorize("hasAnyAuthority('OIDC_USER')")
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
