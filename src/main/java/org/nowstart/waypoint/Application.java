package org.nowstart.waypoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableJpaAuditing
@SpringBootApplication(excludeName = {
        "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration"
})
@ConfigurationPropertiesScan
public class Application {

    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
