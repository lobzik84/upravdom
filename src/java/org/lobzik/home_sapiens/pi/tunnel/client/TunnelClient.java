/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.lobzik.home_sapiens.pi.tunnel.client;

import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import org.json.JSONObject;
import org.lobzik.home_sapiens.pi.AppData;
import org.lobzik.home_sapiens.pi.BoxCommonData;
import org.lobzik.home_sapiens.pi.JSONInterface;

@ClientEndpoint
public class TunnelClient {

    Session session = null;
    String box_session_key = null;

    public TunnelClient(String endpointURI) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(endpointURI));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session session) {
        System.out.println("opening websocket");
        this.session = session;
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        System.out.println("closing websocket");
        this.session = null;
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a
     * client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        System.out.println("got message " + message);
        try {
            if (message != null && message.startsWith("{")) {
                JSONObject json = new JSONObject(message);
                if (json.has("box_session_key")) {
                    box_session_key = json.getString("box_session_key");
                    if ("do_login".equals(json.get("result"))) {
                        String challenge = json.getString("challenge");
                        String digest = challenge;//TODO generate digest
                        json = new JSONObject();
                        json.put("box_session_key", box_session_key);
                        json.put("digest", digest);
                        json.put("box_id", BoxCommonData.BOX_ID);
                        sendMessage(json.toString());
                    }
                }
                if (json.has("user_id") && json.has("action")) {
                    String action = json.getString("action");
                    int userId = json.getInt("user_id");
                    if (AppData.usersKeys.get(userId) == null) {
                        AppData.initUserPublicKey(userId); //TODO говнокод. надо переписать на нормальный lazy init в AppData
                    }
                    RSAPublicKey usersKey = AppData.usersKeys.get(userId);
                    if (usersKey == null) {
                        replyWithError("cannot find users public key userId=" + userId);
                        return;
                    }

                    switch (action) {
                        case "command":
                            if (userId > 0) {
                                JSONInterface.doUserCommand(json);
                            }
                            JSONObject reply = JSONInterface.getEncryptedParametersJSON(usersKey);
                            reply.put("result", "success");
                            reply.put("box_session_key", box_session_key);
                            sendMessage(reply.toString());
                            break;

                        case "get_capture":

                            reply = JSONInterface.getEncryptedCaptureJSON(usersKey);
                            reply.put("box_session_key", box_session_key);
                            reply.put("result", "success");

                            sendMessage(reply.toString());

                            break;

                        default:
                            reply = JSONInterface.getEncryptedParametersJSON(usersKey);
                            reply.put("box_session_key", box_session_key);
                            reply.put("result", "success");
                            sendMessage(reply.toString());

                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public void sendMessage(String message) throws Exception {

        this.session.getBasicRemote().sendText(message);
    }

    private void replyWithError(String message) throws Exception {
        JSONObject json = new JSONObject();
        json.put("result", "error");
        json.put("message", message);
        sendMessage(json.toString());
    }
}
