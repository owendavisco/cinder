package element.fire.cinder;

import org.kurento.client.Fraction;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaProfileSpecType;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.VideoCaps;
import org.kurento.client.VideoCodec;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BroadcastPipeline {
	private static final Logger log = LoggerFactory.getLogger(BroadcastPipeline.class);
	
	public static final String RECORDING_PATH = "file:///tmp/";
	public static final String RECORDING_EXT = ".webm";
	
	private final MediaPipeline mediaPipeline;
	private final WebRtcEndpoint webRtcEndpoint;
	private final RecorderEndpoint recorderEndpoint;
	
	public BroadcastPipeline(KurentoClient kurento, String broadcastTitle){
		log.info("Creating Broadcast pipeline");
		
		//Create the media pipeline
		mediaPipeline = kurento.createMediaPipeline();
		
		//Create the broadcaster pipeline
		webRtcEndpoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
		webRtcEndpoint.setMaxVideoRecvBandwidth(Integer.MAX_VALUE);
		webRtcEndpoint.setStunServerAddress("173.194.65.127");
		webRtcEndpoint.setStunServerPort(19302);
		
		//Create the recording endpoint for the broadcast
		recorderEndpoint = new RecorderEndpoint.Builder(mediaPipeline, RECORDING_PATH + broadcastTitle + RECORDING_EXT)
				.withMediaProfile(MediaProfileSpecType.WEBM).build();
		
		//Connect the endpoints together
		webRtcEndpoint.connect(recorderEndpoint);
	}
	
	public void startRecording(){
		try{
			recorderEndpoint.record();
			log.info("Started recording broadcast");
		}
		catch(Exception e){
			log.error("Something bad happended: + " + e.getMessage());
		}
	}
	
	public void stopRecording(){
		log.info("Stopping recording");
		recorderEndpoint.stop();
	}
	
	public MediaPipeline getMediaPipeline(){
		return mediaPipeline;
	}
	
	public String generateSdpAnswer(String sdpOffer){
		return webRtcEndpoint.processOffer(sdpOffer);
	}
	
	public WebRtcEndpoint getWebRtcEndpoint(){
		return webRtcEndpoint;
	}
	
	public WebRtcEndpoint buildViewerEndpoint(){
		return (new WebRtcEndpoint.Builder(mediaPipeline).build());
	}
}
