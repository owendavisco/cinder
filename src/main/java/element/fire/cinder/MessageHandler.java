package element.fire.cinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.EndOfStreamEvent;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class MessageHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
	private static final Gson gson = new GsonBuilder().create();

	@Autowired
	private KurentoClient kurento;

	private BroadcastPipeline broadcastWebcamPipeline, broadcastDesktopPipeline;
	private UserSession broadcasterUserSession;
	
	private final ConcurrentHashMap<String, UserSession> viewerUserSessions = new ConcurrentHashMap<String, UserSession>();

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception{
		JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
		log.info("Incoming message from session '{}': {}", session.getId(), jsonMessage);

		switch (jsonMessage.get("id").getAsString()){
			case "broadcast":
				if(jsonMessage.get("media").getAsString().equals("webcam"))
					broadcastWebcamPipeline = broadcast(session, jsonMessage, "webcam");
				else if(jsonMessage.get("media").getAsString().equals("desktop"))
					broadcastDesktopPipeline = broadcast(session, jsonMessage, "desktop");
				break;
			case "viewer":
				if(jsonMessage.get("media").getAsString().equals("webcam"))
					viewBroadcast(session, jsonMessage, broadcastWebcamPipeline, "webcam");
				else if(jsonMessage.get("media").getAsString().equals("desktop"))
					viewBroadcast(session, jsonMessage, broadcastDesktopPipeline, "desktop");
				break;
			case "player":
				if(jsonMessage.get("media").getAsString().equals("webcam"))
					playRecording(session, jsonMessage, "webcam");
				else if(jsonMessage.get("media").getAsString().equals("desktop"))
					playRecording(session, jsonMessage, "desktop");
				break;
			case "onIceCandidate":
				JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
	
				UserSession user = null;
				if (broadcasterUserSession !=  null && broadcasterUserSession.getSession() == session){
					user = broadcasterUserSession;
				} 
				else{
					user = viewerUserSessions.get(session.getId());
				}
				if (user != null) {
					IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
							candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
					if(jsonMessage.get("media").getAsString().equals("webcam"))
						user.addWebcamCandidate(cand);
					else
						user.addDesktopCandidate(cand);
				}
				break;
			case "stop":
				stop(session);
				break;
			default:
				break;
		}
	}
	
	private synchronized void playRecording(final WebSocketSession session, JsonObject jsonMessage, final String media) throws IOException {
		if (broadcasterUserSession !=  null) {
			JsonObject response = new JsonObject();
			response.addProperty("id", "playResponse");
			response.addProperty("response", "rejected");
			response.addProperty("message", "Broadcast is live...");
			session.sendMessage(new TextMessage(response.toString()));
		}
		else{
			//TODO: fix logic for multiple stream pipelines
//			if(viewerUserSessions.containsKey(session.getId())){
//				JsonObject response = new JsonObject();
//				response.addProperty("id", "playResponse");
//				response.addProperty("response", "rejected");
//				response.addProperty("message","You are already viewing this recording");
//				session.sendMessage(new TextMessage(response.toString()));
//				return;				
//			}
			
			//1. Media logic
			final RecordedBroadcastPipeline pipeline = new RecordedBroadcastPipeline(kurento, "test-broadcast-" + media, session);
			String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
			
			//2. Store user session
			UserSession viewer = viewerUserSessions.containsKey(session.getId()) ? viewerUserSessions.get(session.getId()) : new UserSession(session);
			viewerUserSessions.put(session.getId(), viewer);
			
			if(media.equals("webcam")){
				viewer.setWebcamRtcEndpoint(pipeline.getWebRtc());
			}
			else if(media.equals("desktop")){
				viewer.setDesktopRtcEndpoint(pipeline.getWebRtc());
			}

			//4. Gather ICE candidates
			pipeline.getWebRtc().addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
					response.addProperty("media", media);
					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
					try {
						synchronized (session) {
							session.sendMessage(new TextMessage(response.toString()));
						}
					} catch (IOException e) {
						log.debug(e.getMessage());
					}
				}
			});
			
			//5. SDP finish negotiation
			String sdpAnswer = pipeline.generateSdpAnswer(sdpOffer);

			JsonObject response = new JsonObject();
			response.addProperty("id", "playResponse");
			response.addProperty("response", "accepted");
			response.addProperty("media", media);
			response.addProperty("sdpAnswer", sdpAnswer);

			pipeline.startPlaying();
			synchronized (session) {
				viewer.sendMessage(response);
			}
			
			pipeline.getWebRtc().gatherCandidates();
		}
	}

	private synchronized BroadcastPipeline broadcast(final WebSocketSession session, JsonObject jsonMessage, final String media) throws IOException{
		//If nobody is broadcasting, start a broadcaster user session or if the broadcaster would like to add a media stream
		if (broadcasterUserSession == null || broadcasterUserSession.getSessionId().equals(session.getId())){
			
			//1. Media logic
			BroadcastPipeline broadcastPipeline = new BroadcastPipeline(kurento, "test-broadcast-" + media, UUID.randomUUID());
			
			if(broadcasterUserSession == null){
				//2. User session
				broadcasterUserSession = new UserSession(session);
			}
			if(media.equals("webcam"))
				broadcasterUserSession.setWebcamRtcEndpoint(broadcastPipeline.getWebRtcEndpoint());
			else if(media.equals("desktop"))
				broadcasterUserSession.setDesktopRtcEndpoint(broadcastPipeline.getWebRtcEndpoint());
			
			//3. SDP negotiation
			String broadcastSdpOffer = jsonMessage.get("sdpOffer").getAsString();
			String broadcastSdpAnswer = broadcastPipeline.generateSdpAnswer(broadcastSdpOffer);
			
			//4. Gather ICE candidates
			broadcastPipeline.getWebRtcEndpoint().addOnIceCandidateListener(
					new EventListener<OnIceCandidateEvent>() {

						@Override
						public void onEvent(OnIceCandidateEvent event) {
							JsonObject response = new JsonObject();
							response.addProperty("id", "iceCandidate");
							response.addProperty("media", media);
							response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
							try {
								synchronized (session) {
									session.sendMessage(new TextMessage(response.toString()));
								}
							} catch (IOException e) {
								log.debug(e.getMessage());
							}
						}
					});
			
			JsonObject startBroadcast = new JsonObject();
			
			startBroadcast.addProperty("id", "broadcasterResponse");
			startBroadcast.addProperty("response", "accepted");
			startBroadcast.addProperty("media", media);
			startBroadcast.addProperty("sdpAnswer", broadcastSdpAnswer);
			
			synchronized (broadcasterUserSession){
				session.sendMessage(new TextMessage(startBroadcast.toString()));
			}
			
			broadcastPipeline.getWebRtcEndpoint().gatherCandidates();
			
			broadcastPipeline.startRecording();
			
			return broadcastPipeline;

		} else {
			JsonObject response = new JsonObject();
			response.addProperty("id", "broadcasterResponse");
			response.addProperty("response", "rejected");
			response.addProperty("message", "Another user is currently acting as sender. Try again later ...");
			session.sendMessage(new TextMessage(response.toString()));
		}
		
		return null;
	}

	private synchronized void viewBroadcast(final WebSocketSession session, JsonObject jsonMessage, BroadcastPipeline pipeline, final String media) throws IOException{
		if (broadcasterUserSession == null || pipeline == null){
			JsonObject response = new JsonObject();
			response.addProperty("id", "viewerResponse");
			response.addProperty("response", "rejected");
			response.addProperty("message", "No broadcast is live right now...");
			session.sendMessage(new TextMessage(response.toString()));
		}
		else{
			//TODO: address multiple streams
//			if(viewerUserSessions.containsKey(session.getId())){
//				JsonObject response = new JsonObject();
//				response.addProperty("id", "viewerResponse");
//				response.addProperty("response", "rejected");
//				response.addProperty("message","You are already viewing this broadcast");
//				session.sendMessage(new TextMessage(response.toString()));
//				return;				
//			}
			log.info("Starting to view broadcast");

			UserSession viewer = viewerUserSessions.containsKey(session.getId()) ? viewerUserSessions.get(session.getId()) : new UserSession(session);
			//TODO: possibly make this better
			viewerUserSessions.put(session.getId(), viewer);

			String sdpOffer = jsonMessage.get("sdpOffer").getAsString();

			WebRtcEndpoint nextWebRtc = pipeline.buildViewerEndpoint();

			nextWebRtc.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
					response.addProperty("media", media);
					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
					try {
						synchronized (session) {
							session.sendMessage(new TextMessage(response.toString()));
						}
					} catch (IOException e) {
						log.debug(e.getMessage());
					}
				}
			});

			if(media.equals("webcam")){
				viewer.setWebcamRtcEndpoint(nextWebRtc);
				broadcasterUserSession.getWebcamRtcEndpoint().connect(nextWebRtc);
			}
			else if(media.equals("desktop")){
				viewer.setDesktopRtcEndpoint(nextWebRtc);
				broadcasterUserSession.getDesktopRtcEndpoint().connect(nextWebRtc);
			}
			String sdpAnswer = nextWebRtc.processOffer(sdpOffer);

			JsonObject response = new JsonObject();
			response.addProperty("id", "viewerResponse");
			response.addProperty("response", "accepted");
			response.addProperty("media", media);
			response.addProperty("sdpAnswer", sdpAnswer);

			synchronized (session) {
				viewer.sendMessage(response);
			}
			nextWebRtc.gatherCandidates();
		}
	}

	private synchronized void stop(WebSocketSession session) throws IOException{
		log.info("Stop called");
		String sessionId = session.getId();
		if(broadcasterUserSession != null && broadcasterUserSession.getSessionId().equals(sessionId)){
			for (UserSession viewer : viewerUserSessions.values()){
				JsonObject response = new JsonObject();
				response.addProperty("id", "stopCommunication");
				viewer.sendMessage(response);
			}

			log.info("Stop Recording and release broadcast pipeline");
			if(broadcastWebcamPipeline != null){
				broadcastWebcamPipeline.stopRecording();
				broadcastWebcamPipeline.getMediaPipeline().release();
			}
			if(broadcastDesktopPipeline != null){
				broadcastDesktopPipeline.stopRecording();
				broadcastDesktopPipeline.getMediaPipeline().release();	
			}
			broadcastWebcamPipeline = null;
			broadcastDesktopPipeline = null;
			broadcasterUserSession = null;
		}
		else if(viewerUserSessions.containsKey(sessionId)) {
			//TODO: fix
			if(viewerUserSessions.get(sessionId).getWebcamRtcEndpoint() != null){
				viewerUserSessions.get(sessionId).getWebcamRtcEndpoint().release();
			}
			viewerUserSessions.remove(sessionId);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		stop(session);
	}

}

