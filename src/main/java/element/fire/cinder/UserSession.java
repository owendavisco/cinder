package element.fire.cinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kurento.client.IceCandidate;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

public class UserSession {

	private static final Logger log = LoggerFactory.getLogger(UserSession.class);

	private final WebSocketSession session;
	private WebRtcEndpoint webcamRtcEndpoint;
	private WebRtcEndpoint desktopRtcEndpoint;
	private final List<IceCandidate> iceCandidateList = new ArrayList<IceCandidate>();

	public UserSession(WebSocketSession session){
		this.session = session;
	}

	public WebSocketSession getSession(){
		return session;
	}
	
	public String getSessionId(){
		return session.getId();
	}

	public void sendMessage(JsonObject message) throws IOException{
		log.info("Sending message from user with session Id '{}': {}",session.getId(), message);
		session.sendMessage(new TextMessage(message.toString()));
	}

//	public WebRtcEndpoint getWebRtcEndpoint(){
//		log.info("Getting user session WebRtcEndpoint");
//		return webRtcEndpoint;
//	}
	
	public WebRtcEndpoint getWebcamRtcEndpoint(){
		return webcamRtcEndpoint;
	}
	
	public WebRtcEndpoint getDesktopRtcEndpoint(){
		return desktopRtcEndpoint;
	}

//	public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint){
//		log.info("Setting user session WebRtcEndpoint");
//		this.webRtcEndpoint = webRtcEndpoint;
//	}
	
	public void setWebcamRtcEndpoint(WebRtcEndpoint webRtcEndpoint){
		this.webcamRtcEndpoint = webRtcEndpoint;
	}
	
	public void setDesktopRtcEndpoint(WebRtcEndpoint webRtcEndpoint){
		this.desktopRtcEndpoint = webRtcEndpoint;
	}
	
	public void addDesktopCandidate(IceCandidate iceCandidate){
		log.info("Adding ICE candidate");
		//If the webrtc endpoint is available, add the ice candidate
		if(this.desktopRtcEndpoint != null){
			desktopRtcEndpoint.addIceCandidate(iceCandidate);
		}
		//Otherwise add it to the list of "to be" added
		else{
			iceCandidateList.add(iceCandidate);
		}		
	}
	
	public void addWebcamCandidate(IceCandidate iceCandidate){
		log.info("Adding ICE candidate");
		//If the webrtc endpoint is available, add the ice candidate
		if(this.webcamRtcEndpoint != null){
			webcamRtcEndpoint.addIceCandidate(iceCandidate);
		}
		//Otherwise add it to the list of "to be" added
		else{
			iceCandidateList.add(iceCandidate);
		}	
	}

//	public void addCandidate(IceCandidate iceCandidate) {
//		log.info("Adding ICE candidate");
//		//If the webrtc endpoint is available, add the ice candidate
//		if(this.webRtcEndpoint != null){
//			webRtcEndpoint.addIceCandidate(iceCandidate);
//		}
//		//Otherwise add it to the list of "to be" added
//		else{
//			iceCandidateList.add(iceCandidate);
//		}
//			
//	}
}

