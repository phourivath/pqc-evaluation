package io.github.phourivath.pqcevaluation.runner

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.phourivath.pqcevaluation.contract.EvaluationResult
import java.nio.file.Files
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EvaluationRunnerTest {
    @Test
    fun emitsAContractResultForEveryParameterSet() {
        val output = Files.createTempFile("bc-kotlin-runner", ".json")
        EvaluationRunner(output).run()

        val result =
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .readValue(output.toFile(), EvaluationResult::class.java)

        assertThat(result.schemaVersion()).isEqualTo("1.0")
        assertThat(result.implementation().id()).isEqualTo("bc-ml-dsa")
        assertThat(result.implementation().displayName())
            .isEqualTo("Bouncy Castle Base ML-DSA (Kotlin/JVM)")
        assertThat(result.implementation().version()).isEqualTo("1.85.2")
        assertThat(result.implementation().engineLineageId()).isEqualTo("bouncycastle-java")
        assertThat(result.runtime().buildProperties())
            .containsEntry("language", "kotlin")
            .containsEntry("api", "java-jca-from-kotlin")
        assertThat(result.parameterSets().map { it.parameterSet() })
            .containsExactly("ML-DSA-44", "ML-DSA-65", "ML-DSA-87")
        assertThat(result.parameterSets())
            .allSatisfy { parameterSet ->
                assertThat(parameterSet.rawPrivateSeedBytes()).isEqualTo(32)
                assertThat(parameterSet.capabilities())
                    .extracting<String> { it.operation() }
                    .contains("key-generation", "sign", "verify", "context")
            }
        assertThat(result.checks()).noneMatch { it.status() == "fail" }
        assertThat(Files.readString(output)).doesNotContain("Private bytes:")
    }
}
