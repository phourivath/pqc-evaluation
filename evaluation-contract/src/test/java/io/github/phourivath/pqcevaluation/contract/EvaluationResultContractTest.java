package io.github.phourivath.pqcevaluation.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

class EvaluationResultContractTest {

  @Test
  void schemaIsPackagedWithTheContract() {
    try (InputStream schema =
        EvaluationResultContractTest.class.getResourceAsStream("/evaluation-result.schema.json")) {
      assertThat(schema).isNotNull();
    } catch (java.io.IOException exception) {
      throw new AssertionError("Unable to close schema resource", exception);
    }
  }
}
