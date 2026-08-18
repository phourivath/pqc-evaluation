package io.github.phourivath.pqcevaluation;

import io.github.phourivath.pqcevaluation.runner.RunnerExecutionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RunnerExecutionProperties.class)
public class PqcEvaluationApplication {

  public static void main(String[] args) {
    SpringApplication.run(PqcEvaluationApplication.class, args);
  }
}
