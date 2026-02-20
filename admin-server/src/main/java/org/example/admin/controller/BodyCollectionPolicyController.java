package org.example.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.BodyCollectionPolicy;
import org.example.admin.service.BodyCollectionPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class BodyCollectionPolicyController {

    private final BodyCollectionPolicyService policyService;

    @GetMapping
    public List<BodyCollectionPolicy> list() {
        return policyService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BodyCollectionPolicy create(@RequestBody Map<String, String> body) {
        return policyService.create(body.get("pathPattern"));
    }

    @PatchMapping("/{id}/toggle")
    public BodyCollectionPolicy toggle(@PathVariable Long id) {
        return policyService.toggle(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        policyService.delete(id);
    }
}
