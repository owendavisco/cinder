package element.fire.cinder;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
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

	static String KMS_WS_URI = "ws://localhost:8888/kurento";
	final static String DEFAULT_APP_SERVER_URL = "http://localhost:8080";

	@Bean
	public MessageHandler callHandler() {
		return new MessageHandler();
	}

	@Bean
	public KurentoClient kurentoClient() {
		return KurentoClient.create(System.getProperty("kms.ws.uri", KMS_WS_URI));
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(callHandler(), "/call");
	}

	public static void main(String[] args) throws Exception {
		Options options = new Options();
		
		//Add the option -kms or --kms_ws_uri followed by an argument
		//example: -kms "ws://192.168.1.119:8888/kurento"
		options.addOption(Option.builder("kms")
                                .longOpt("kms_ws_uri")
                                .hasArg()
                                .desc("Changes the default kurento media server ip").argName("kms")
                                .build());
		CommandLineParser parser = new DefaultParser();
		
		//Parse the command line for defined arguments
		CommandLine line = parser.parse(options, args);
		
		//Set the kurento media server uri to either the parsed argument or just localhost
		KMS_WS_URI = line.getOptionValue("kms") != null ? line.getOptionValue("kms") : "ws://localhost:8888/kurento";
		
		//Create the application
		new SpringApplication(CinderApp.class).run(args);
	}

}

