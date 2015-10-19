package element.fire.cinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

	private BroadcastPipeline broadcastPipeline;
	private RecordedBroadcastPipeline previousBroadcastPipeline;
	private UserSession broadcasterUserSession;
	
	private final ConcurrentHashMap<String, UserSession> viewerUserSessions = new ConcurrentHashMap<String, UserSession>();

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception{
		JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
		log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);

		switch (jsonMessage.get("id").getAsString()){
			case "broadcaster":
					broadcaster(session, jsonMessage);
				break;
			case "viewer":
					viewBroadcast(session, jsonMessage);
				break;
			case "playRecording":
					playRecording(session, jsonMessage);
			case "onIceCandidate":
				JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
	
				UserSession user = null;
				if (broadcasterUserSession.getSession() == session){
					user = broadcasterUserSession;
				} 
				else{
					user = viewerUserSessions.get(session.getId());
				}
				if (user != null) {
					IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
							candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
					user.addCandidate(cand);
				}
				break;
			case "stop":
				stop(session);
				break;
			default:
				break;
		}
	}
	
	private synchronized void playRecording(final WebSocketSession session, JsonObject jsonMessage) throws IOException {
//		if (false) {
//			JsonObject response = new JsonObject();
//			response.addProperty("id", "playResponse");
//			response.addProperty("response", "rejected");
//			response.addProperty("message", "Broadcast is live...");
//			session.sendMessage(new TextMessage(response.toString()));
//		}
//		else{
//			JsonObject response = new JsonObject();
//			response.addProperty("id", "playResponse");
//			
//			// Media pipeline
//			playRecordingPipeline = kurento.createMediaPipeline();
//
//			// Media Elements (WebRtcEndpoint, PlayerEndpoint)
//			WebRtcEndpoint webRtc = new WebRtcEndpoint.Builder(playRecordingPipeline).build();
//			PlayerEndpoint player = new PlayerEndpoint.Builder(playRecordingPipeline, RECORDING_PATH + "test" + RECORDING_EXT).build();
//			
//			String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
//
////			session.setWebRtcEndpoint(webRtc));
//
//			webRtc.addOnIceCandidateListener(
//					new EventListener<OnIceCandidateEvent>() {
//
//						@Override
//						public void onEvent(OnIceCandidateEvent event) {
//							JsonObject response = new JsonObject();
//							response.addProperty("id", "iceCandidate");
//							response.add("candidate", JsonUtils
//									.toJsonObject(event.getCandidate()));
//							try {
//								synchronized (session) {
//									session.sendMessage(
//												new TextMessage(response
//														.toString()));
//								}
//							} catch (IOException e) {
//								log.debug(e.getMessage());
//							}
//						}
//					});
//
//			String sdpAnswer = webRtc.processOffer(sdpOffer);
//			
//			response.addProperty("response", "accepted");
//
//			response.addProperty("sdpAnswer", sdpAnswer);
//
//
//			// Connection
//			player.connect(webRtc);
//
//			// Player listeners
//			player.addErrorListener(new EventListener<ErrorEvent>() {
//				@Override
//				public void onEvent(ErrorEvent event) {
//					log.info("ErrorEvent: {}", event.getDescription());
//					try {
//						JsonObject response = new JsonObject();
//						response.addProperty("id", "playEnd");
//						session.sendMessage(new TextMessage(response.toString()));
//					} catch (IOException e) {
//						log.error("Error sending playEndOfStream message", e);
//					}
//
//					// Release pipeline
//					playRecordingPipeline.release();
//				}
//			});
//			player.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
//				@Override
//				public void onEvent(EndOfStreamEvent event) {
//					try {
//						JsonObject response = new JsonObject();
//						response.addProperty("id", "playEnd");
//						session.sendMessage(new TextMessage(response.toString()));
//					} catch (IOException e) {
//						log.error("Error sending playEndOfStream message", e);
//					}
//
//					// Release pipeline
//					playRecordingPipeline.release();
//				}
//			});
//			
//			session.sendMessage(new TextMessage(response.toString()));
//			playMediaPipeline.play();
//			pipelines.put(session.getSessionId(),
//					playMediaPipeline.getPipeline());
//			synchronized (session.getSession()) {
//				session.sendMessage(response);
//			}
//
//			playMediaPipeline.getWebRtc().gatherCandidates();
//		}
	}

	private synchronized void broadcaster(final WebSocketSession session, JsonObject jsonMessage) throws IOException{
		//If nobody is broadcasting, start a broadcaster user session
		if (broadcasterUserSession == null){
			
			//1. Media logic
			broadcastPipeline = new BroadcastPipeline(kurento, "test-broadcast");
			
			//2. User session
			broadcasterUserSession = new UserSession(session);
			broadcasterUserSession.setWebRtcEndpoint(broadcastPipeline.getWebRtcEndpoint());
			
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
			startBroadcast.addProperty("sdpAnswer", broadcastSdpAnswer);
			
			synchronized (broadcasterUserSession){
				session.sendMessage(new TextMessage(startBroadcast.toString()));
			}
			
			broadcastPipeline.getWebRtcEndpoint().gatherCandidates();
			
			broadcastPipeline.startRecording();

		} else {
			JsonObject response = new JsonObject();
			response.addProperty("id", "presenterResponse");
			response.addProperty("response", "rejected");
			response.addProperty("message", "Another user is currently acting as sender. Try again later ...");
			session.sendMessage(new TextMessage(response.toString()));
		}
	}

	private synchronized void viewBroadcast(final WebSocketSession session, JsonObject jsonMessage) throws IOException{
		if (broadcasterUserSession == null || broadcastPipeline == null){
			JsonObject response = new JsonObject();
			response.addProperty("id", "viewBroadcast");
			response.addProperty("response", "rejected");
			response.addProperty("message", "No broadcast is live right now...");
			session.sendMessage(new TextMessage(response.toString()));
		}
		else{
			if(viewerUserSessions.containsKey(session.getId())){
				JsonObject response = new JsonObject();
				response.addProperty("id", "viewBroadcast");
				response.addProperty("response", "rejected");
				response.addProperty("message","You are already viewing this broadcast");
				session.sendMessage(new TextMessage(response.toString()));
				return;				
			}
			log.info("Starting to view broadcast");
			
			//1. Create WebRtc Endpoint
			WebRtcEndpoint newViewerWebRtc = broadcastPipeline.buildViewerEndpoint();
			
			//2. User Logic
			UserSession viewer = new UserSession(session);
			viewerUserSessions.put(session.getId(), viewer);
			
			//3. SDP negotiation
			String viewerSdpOffer = jsonMessage.get("sdpOffer").getAsString();
			
			//4. Gather ICE candidates
			newViewerWebRtc.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
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
			
			//Set viewer WebRtc Endpoint
			viewer.setWebRtcEndpoint(newViewerWebRtc);
			
			//Connect the viewer WebRtc to the Broadcaster WebRtc
			broadcasterUserSession.getWebRtcEndpoint().connect(newViewerWebRtc);
			
			String viewerSdpAnswer = newViewerWebRtc.processAnswer(viewerSdpOffer);
			
			JsonObject response = new JsonObject();
			response.addProperty("id", "viewerResponse");
			response.addProperty("response", "accepted");
			response.addProperty("sdpAnswer", viewerSdpAnswer);

			synchronized (session) {
				viewer.sendMessage(response);
			}
			newViewerWebRtc.gatherCandidates();
		}
//		else {
//			if (viewers.containsKey(session.getId())) {
//				JsonObject response = new JsonObject();
//				response.addProperty("id", "viewBroadcast");
//				response.addProperty("response", "rejected");
//				response.addProperty("message",
//						"You are already viewing in this session. Use a different browser to add additional viewers.");
//				session.sendMessage(new TextMessage(response.toString()));
//				return;
//			}
//			UserSession viewer = new UserSession(session);
//			viewers.put(session.getId(), viewer);
//
//			String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
//
//			WebRtcEndpoint nextWebRtc = new WebRtcEndpoint.Builder(broadcastPipeline).build();
//
//			nextWebRtc.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
//
//				@Override
//				public void onEvent(OnIceCandidateEvent event) {
//					JsonObject response = new JsonObject();
//					response.addProperty("id", "iceCandidate");
//					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
//					try {
//						synchronized (session) {
//							session.sendMessage(new TextMessage(response.toString()));
//						}
//					} catch (IOException e) {
//						log.debug(e.getMessage());
//					}
//				}
//			});
//
//			viewer.setWebRtcEndpoint(nextWebRtc);
//			broadcasterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
//			String sdpAnswer = nextWebRtc.processOffer(sdpOffer);
//
//			JsonObject response = new JsonObject();
//			response.addProperty("id", "viewBroadcast");
//			response.addProperty("response", "accepted");
//			response.addProperty("sdpAnswer", sdpAnswer);
//
//			synchronized (session) {
//				viewer.sendMessage(response);
//			}
//			nextWebRtc.gatherCandidates();
//		}
	}

	private synchronized void stop(WebSocketSession session) throws IOException {
		log.info("Complete stop called");
		broadcastPipeline.stopRecording();
//		String sessionId = session.getId();
//		if (broadcasterUserSession != null && broadcasterUserSession.getSession().getId().equals(sessionId)) {
//			for (UserSession viewer : viewers.values()) {
//				JsonObject response = new JsonObject();
//				response.addProperty("id", "stopCommunication");
//				viewer.sendMessage(response);
//			}
//
//			log.info("Releasing broadcast pipeline");
//			if (broadcastPipeline != null) {
//				broadcastPipeline.release();
//			}
//			broadcastPipeline = null;
//			broadcasterUserSession = null;
//		} else if (viewers.containsKey(sessionId)) {
//			if (viewers.get(sessionId).getWebRtcEndpoint() != null) {
//				viewers.get(sessionId).getWebRtcEndpoint().release();
//			}
//			viewers.remove(sessionId);
//		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		stop(session);
	}

}

