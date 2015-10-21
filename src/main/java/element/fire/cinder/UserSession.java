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
	private WebRtcEndpoint webRtcEndpoint;
	private final List<IceCandidate> iceCandidateList = new ArrayList<IceCandidate>();

	public UserSession(WebSocketSession session){
		this.session = session;
	}

	public WebSocketSession getSession(){
		return session;
	}

	public void sendMessage(JsonObject message) throws IOException{
		log.error("Sending message from user with session Id '{}': {}",session.getId(), message);
		session.sendMessage(new TextMessage(message.toString()));
	}

	public WebRtcEndpoint getWebRtcEndpoint(){
		return webRtcEndpoint;
	}

	public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint){
		this.webRtcEndpoint = webRtcEndpoint;
	}

	public void addCandidate(IceCandidate iceCandidate) {
		//If the webrtc endpoint is available, add the ice candidate
		if(this.webRtcEndpoint != null){
			webRtcEndpoint.addIceCandidate(iceCandidate);
		}
		//Otherwise add it to the list of "to be" added
		else{
			iceCandidateList.add(iceCandidate);
		}
			
	}
}

