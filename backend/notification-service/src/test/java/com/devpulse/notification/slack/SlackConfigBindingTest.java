package com.devpulse.notification.slack;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SlackNotificationService and SlackOAuthController read
 * {@code devpulse.notification.slack.*}, while the deployment supplies plain
 * {@code SLACK_*} environment variables. Only application.yml connects the two.
 * When that block was missing every send was reported as "Slack is not
 * configured" and the team-message endpoint answered 502.
 */
public class SlackConfigBindingTest {

    private static Properties applicationYaml() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        return yaml.getObject();
    }

    @Test
    public void everySlackPropertyTheCodeReadsIsBoundToItsEnvironmentVariable() {
        Properties props = applicationYaml();

        assertEquals("${SLACK_WEBHOOK_URL:}", props.getProperty("devpulse.notification.slack.webhook-url"));
        assertEquals("${SLACK_BOT_TOKEN:}", props.getProperty("devpulse.notification.slack.bot-token"));
        assertEquals("${SLACK_CLIENT_ID:}", props.getProperty("devpulse.notification.slack.client-id"));
        assertEquals("${SLACK_CLIENT_SECRET:}", props.getProperty("devpulse.notification.slack.client-secret"));
    }
}
