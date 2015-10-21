package element.fire.cinder;

import java.io.IOException;

import org.kurento.client.EndOfStreamEvent;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

public class RecordedBroadcastPipeline {
	private static final Logger log = LoggerFactory.getLogger(RecordedBroadcastPipeline.class);
	
	public static final String RECORDING_PATH = "file:///tmp/";
	public static final String RECORDING_EXT = ".webm";
	
	private final MediaPipeline mediaPipeline;
	private final WebRtcEndpoint webRtcEndpoint;
	private final PlayerEndpoint playerEndpoint;
	
	public RecordedBroadcastPipeline(KurentoClient kurento, String broadcastTitle, final WebSocketSession session){
		log.info("Creating RecordedBroadcast pipeline");
		
		//Create the media pipeline
		mediaPipeline = kurento.createMediaPipeline();
		
		//Create the broadcaster pipeline
		webRtcEndpoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
		
		//Create the playing endpoint for the broadcast
		playerEndpoint = new PlayerEndpoint.Builder(mediaPipeline, RECORDING_PATH + broadcastTitle + RECORDING_EXT).build();
		
		//Connect the endpoints together
		playerEndpoint.connect(webRtcEndpoint);
		
		// Player listeners
		playerEndpoint.addErrorListener(new EventListener<ErrorEvent>() {
			@Override
			public void onEvent(ErrorEvent event) {
				log.info("ErrorEvent: {}", event.getDescription());
				sendEndOfStream(session);
			}
		});
		playerEndpoint.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
			@Override
			public void onEvent(EndOfStreamEvent event) {
				log.info("End of stream reached");
				sendEndOfStream(session);
			}
		});
	}
	
	public void sendEndOfStream(WebSocketSession session){
		try{
			JsonObject response = new JsonObject();
			response.addProperty("id", "playEnd");
			session.sendMessage(new TextMessage(response.toString()));
		}catch(IOException e){
			log.error("Error sending playEndOfStream message", e);
		}
		mediaPipeline.release();
	}
	
	public void startPlaying(){
		playerEndpoint.play();
	}
	
	public String generateSdpAnswer(String sdpOffer) {
		return webRtcEndpoint.processOffer(sdpOffer);
	}

	public MediaPipeline getPipeline() {
		return mediaPipeline;
	}

	public WebRtcEndpoint getWebRtc() {
		return webRtcEndpoint;
	}
	
	public WebRtcEndpoint buildRecordedViewerEndpoint(){
		return (new WebRtcEndpoint.Builder(mediaPipeline).build());
	}
}
