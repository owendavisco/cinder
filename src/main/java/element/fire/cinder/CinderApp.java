package element.fire.cinder;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableAutoConfiguration
public class CinderApp implements WebSocketConfigurer {

	final static String DEFAULT_KMS_WS_URI = "ws://192.168.56.101:8888/kurento";
	final static String DEFAULT_APP_SERVER_URL = "http://localhost:8080";

	@Bean
	public MessageHandler callHandler() {
		return new MessageHandler();
	}

	@Bean
	public KurentoClient kurentoClient() {
		return KurentoClient.create(System.getProperty("kms.ws.uri", DEFAULT_KMS_WS_URI));
	}

	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(callHandler(), "/call");
	}

	public static void main(String[] args) throws Exception {
		new SpringApplication(CinderApp.class).run(args);
	}

}

