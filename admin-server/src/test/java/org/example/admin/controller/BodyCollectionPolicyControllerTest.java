package org.example.admin.controller;

import org.example.admin.domain.BodyCollectionPolicy;
import org.example.admin.service.BodyCollectionPolicyService;
import org.example.admin.service.DuplicatePolicyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BodyCollectionPolicyController.class)
class BodyCollectionPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BodyCollectionPolicyService policyService;

    @Test
    @DisplayName("GET /api/policies → 전체 정책 목록")
    void list() throws Exception {
        given(policyService.findAll()).willReturn(List.of(
                BodyCollectionPolicy.builder().pathPattern("/server-a/**").build()
        ));

        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pathPattern").value("/server-a/**"));
    }

    @Test
    @DisplayName("POST /api/policies → 새 정책 등록")
    void create() throws Exception {
        given(policyService.create("/server-b/**")).willReturn(
                BodyCollectionPolicy.builder().pathPattern("/server-b/**").build()
        );

        mockMvc.perform(post("/api/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPattern\":\"/server-b/**\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pathPattern").value("/server-b/**"));
    }

    @Test
    @DisplayName("PATCH /api/policies/{id}/toggle → 활성화 토글")
    void toggle() throws Exception {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**").build();
        policy.toggleEnabled();
        given(policyService.toggle(1L)).willReturn(policy);

        mockMvc.perform(patch("/api/policies/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("DELETE /api/policies/{id} → 정책 삭제")
    void deletePolicy() throws Exception {
        mockMvc.perform(delete("/api/policies/1"))
                .andExpect(status().isNoContent());

        verify(policyService).delete(1L);
    }

    @Test
    @DisplayName("중복 pathPattern 등록 → 409 Conflict")
    void create_duplicate_conflict() throws Exception {
        given(policyService.create(any())).willThrow(new DuplicatePolicyException("duplicate"));

        mockMvc.perform(post("/api/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPattern\":\"/server-a/**\"}"))
                .andExpect(status().isConflict());
    }
}
