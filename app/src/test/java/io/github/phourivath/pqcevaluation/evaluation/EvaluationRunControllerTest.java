package io.github.phourivath.pqcevaluation.evaluation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EvaluationRunControllerTest {

  private static final String VALID_RESULT =
      """
      {
        "schemaVersion": "1.0",
        "runId": "api-run",
        "generatedAt": "2026-08-18T00:00:00Z",
        "implementation": {
          "id": "jdk-sun-ml-dsa",
          "displayName": "JDK SUN ML-DSA",
          "version": "25.0.4",
          "engineLineageId": "jdk-sun",
          "distribution": "java.base",
          "license": "GPL",
          "assuranceStatus": "platform"
        },
        "runtime": {
          "javaVersion": "25.0.4",
          "javaVendor": "OpenJDK",
          "osName": "Linux",
          "osVersion": "1",
          "architecture": "amd64",
          "buildProperties": {}
        },
        "parameterSets": [{
          "parameterSet": "ML-DSA-44",
          "securityLevel": 2,
          "rawPublicKeyBytes": 1312,
          "rawPrivateSeedBytes": 32,
          "rawPrivateExpandedBytes": 2560,
          "rawSignatureBytes": 2420,
          "capabilities": [],
          "representations": []
        }],
        "checks": [{
          "id": "key-generation",
          "parameterSet": "ML-DSA-44",
          "category": "correctness",
          "status": "pass",
          "message": "ok"
        }],
        "interoperability": [],
        "warnings": []
      }
      """;

  @Autowired private MockMvc mockMvc;

  @Test
  void importsAndReadsEvaluationRun() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/evaluation-runs/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_RESULT))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.runId").value("api-run"));

    mockMvc
        .perform(
            post("/api/v1/evaluation-runs/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_RESULT))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/evaluation-runs/api-run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.implementation.id").value("jdk-sun-ml-dsa"));

    mockMvc
        .perform(get("/api/v1/comparisons"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].checksPassed").value(1));
  }

  @Test
  void returnsProblemDetailsForInvalidAndMissingRuns() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/evaluation-runs/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\":\"1.0\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid evaluation result"));

    mockMvc
        .perform(get("/api/v1/evaluation-runs/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Evaluation run not found"));
  }

  @Test
  void exposesRunnerCatalogAndRejectsGatedProviderExecution() throws Exception {
    mockMvc
        .perform(get("/api/v1/runners"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[3].lifecycle").value("IMPLEMENTED"))
        .andExpect(jsonPath("$[1].availability").value("GATED"));

    mockMvc
        .perform(post("/api/v1/runners/bc-fips/executions"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("unavailable"));
  }
}
