package element.fire.cinder;

import java.io.IOException;

import org.kurento.client.IceCandidate;
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
	private RecorderEndpoint recorderEndpoint;

	public UserSession(WebSocketSession session) {
		this.session = session;
	}

	public WebSocketSession getSession() {
		return session;
	}

	public void sendMessage(JsonObject message) throws IOException {
		log.debug("Sending message from user with session Id '{}': {}",session.getId(), message);
		session.sendMessage(new TextMessage(message.toString()));
	}

	public WebRtcEndpoint getWebRtcEndpoint() {
		return webRtcEndpoint;
	}
	
	public RecorderEndpoint getRecorderEndpoint() {
		return recorderEndpoint;
	}
	
	public void setRecorderEndpoint(RecorderEndpoint recorderEndpoint) {
		this.recorderEndpoint = recorderEndpoint;
	}

	public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
		this.webRtcEndpoint = webRtcEndpoint;
	}

	public void addCandidate(IceCandidate i) {
		webRtcEndpoint.addIceCandidate(i);
	}
}

