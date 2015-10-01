package element.fire.cinder;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.OnIceCandidateEvent;
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

	private final ConcurrentHashMap<String, UserSession> viewers = new ConcurrentHashMap<String, UserSession>();

	@Autowired
	private KurentoClient kurento;

	private MediaPipeline broadcastPipeline;
	private MediaPipeline playRecordingPipeline;
	private UserSession presenterUserSession;

	public static final String RECORDING_PATH = "file:///tmp/";
	public static final String RECORDING_EXT = ".webm";

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
		log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);

		switch (jsonMessage.get("id").getAsString()) {
			case "presenter":
				try {
					presenter(session, jsonMessage);
				} catch (Throwable t) {
					stop(session);
					log.error(t.getMessage(), t);
					JsonObject response = new JsonObject();
					response.addProperty("id", "presenterResponse");
					response.addProperty("response", "rejected");
					response.addProperty("message", t.getMessage());
					session.sendMessage(new TextMessage(response.toString()));
				}
				break;
			case "viewer":
				try {
					viewer(session, jsonMessage);
				} catch (Throwable t) {
					stop(session);
					log.error(t.getMessage(), t);
					JsonObject response = new JsonObject();
					response.addProperty("id", "viewerResponse");
					response.addProperty("response", "rejected");
					response.addProperty("message", t.getMessage());
					session.sendMessage(new TextMessage(response.toString()));
				}
				break;
			case "onIceCandidate": {
				JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
	
				UserSession user = null;
				if (presenterUserSession.getSession() == session) {
					user = presenterUserSession;
				} else {
					user = viewers.get(session.getId());
				}
				if (user != null) {
					IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
							candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
					user.addCandidate(cand);
				}
				break;
			}
			case "stop":
				stop(session);
				break;
			default:
				break;
		}
	}

	private synchronized void presenter(final WebSocketSession session, JsonObject jsonMessage) throws IOException {
		//If nobody is presenting, start a presenter user session
		if (presenterUserSession == null) {
			presenterUserSession = new UserSession(session);

			//Create the media pipeline for the data to go through
			broadcastPipeline = kurento.createMediaPipeline();
			
			//Set endpoints that the recorder will be using
			presenterUserSession.setWebRtcEndpoint(new WebRtcEndpoint.Builder(broadcastPipeline).build());
			presenterUserSession.setRecorderEndpoint(new RecorderEndpoint.Builder(broadcastPipeline, RECORDING_PATH + "test" + RECORDING_EXT).build());

			WebRtcEndpoint presenterWebRtc = presenterUserSession.getWebRtcEndpoint();
			RecorderEndpoint presenterRecorder = presenterUserSession.getRecorderEndpoint();

			presenterWebRtc.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

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

			String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
			String sdpAnswer = presenterWebRtc.processOffer(sdpOffer);

			JsonObject response = new JsonObject();
			response.addProperty("id", "presenterResponse");
			response.addProperty("response", "accepted");
			response.addProperty("sdpAnswer", sdpAnswer);

			synchronized (session) {
				presenterUserSession.sendMessage(response);
			}
			presenterWebRtc.gatherCandidates();
			
			presenterRecorder.record();

		} else {
			JsonObject response = new JsonObject();
			response.addProperty("id", "presenterResponse");
			response.addProperty("response", "rejected");
			response.addProperty("message", "Another user is currently acting as sender. Try again later ...");
			session.sendMessage(new TextMessage(response.toString()));
		}
	}

	private synchronized void viewer(final WebSocketSession session, JsonObject jsonMessage) throws IOException {
		if (presenterUserSession == null || presenterUserSession.getWebRtcEndpoint() == null) {
			JsonObject response = new JsonObject();
			response.addProperty("id", "viewerResponse");
			response.addProperty("response", "rejected");
			response.addProperty("message", "No active sender now. Become sender or . Try again later ...");
			session.sendMessage(new TextMessage(response.toString()));
		} 
		else {
			if (viewers.containsKey(session.getId())) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "viewerResponse");
				response.addProperty("response", "rejected");
				response.addProperty("message",
						"You are already viewing in this session. Use a different browser to add additional viewers.");
				session.sendMessage(new TextMessage(response.toString()));
				return;
			}
			UserSession viewer = new UserSession(session);
			viewers.put(session.getId(), viewer);

			String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();

			WebRtcEndpoint nextWebRtc = new WebRtcEndpoint.Builder(broadcastPipeline).build();

			nextWebRtc.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

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

			viewer.setWebRtcEndpoint(nextWebRtc);
			presenterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
			String sdpAnswer = nextWebRtc.processOffer(sdpOffer);

			JsonObject response = new JsonObject();
			response.addProperty("id", "viewerResponse");
			response.addProperty("response", "accepted");
			response.addProperty("sdpAnswer", sdpAnswer);

			synchronized (session) {
				viewer.sendMessage(response);
			}
			nextWebRtc.gatherCandidates();
		}
	}

	private synchronized void stop(WebSocketSession session) throws IOException {
		String sessionId = session.getId();
		if (presenterUserSession != null && presenterUserSession.getSession().getId().equals(sessionId)) {
			for (UserSession viewer : viewers.values()) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "stopCommunication");
				viewer.sendMessage(response);
			}

			log.info("Releasing media pipeline");
			if (broadcastPipeline != null) {
				broadcastPipeline.release();
			}
			broadcastPipeline = null;
			presenterUserSession = null;
		} else if (viewers.containsKey(sessionId)) {
			if (viewers.get(sessionId).getWebRtcEndpoint() != null) {
				viewers.get(sessionId).getWebRtcEndpoint().release();
			}
			viewers.remove(sessionId);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		stop(session);
	}

}

