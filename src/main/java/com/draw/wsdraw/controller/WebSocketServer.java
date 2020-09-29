package com.draw.wsdraw.controller;

import com.draw.wsdraw.model.User;
import com.draw.wsdraw.utils.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@ServerEndpoint("/webSocket/{roomId}/usrname/{name}")
@Component
public class WebSocketServer {
    // 主要是存 roomid的
    private static ConcurrentHashMap<String, List<WebSocketServer>> webSocketMap =
            new ConcurrentHashMap<>(3);

    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;
    private User user;
    //接收roomId
    private String roomId = "";

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId, @PathParam("name") String name) {
        if (roomId == null || roomId.isEmpty()) return;
        this.session = session;
        this.roomId = roomId;
        this.user = new User();
        this.user.setId(session.getId());
        this.user.setName(name);
        addSocketServer2Map(this);
        try {
            sendMessage(session, "连接成功", true);
        } catch (IOException e) {
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        List<WebSocketServer> wssList = webSocketMap.get(roomId);
        if (wssList != null) {
            for (WebSocketServer item : wssList) {
                if (item.session.getId().equals(session.getId())) {
                    wssList.remove(item);
                    if (wssList.isEmpty()) {
                        webSocketMap.remove(roomId);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        JsonNode json = new JsonMapper().fromJson(message, JsonNode.class);
        //群发消息
        String msg = filterMessage(message);
        if (msg != null) {
            sendInfo(msg, json.get("roomId").asText(), session);
        }
    }

    /**
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    private String filterMessage(String message) {
        if (message == null || message.isEmpty()) return null;
        if ("undefined".equals(message)) return null;
        return message;
    }

    /**
     * 群发自定义消息
     */
    public static void sendInfo(String message, @PathParam("roomId") String roomId, Session session) {
        log.info("roomid={}, sessionid={}, content={}", roomId, session.getId(), message);
        JsonNode msg = new JsonMapper().fromJson(message, JsonNode.class);

        if (roomId == null || roomId.isEmpty() || session == null) return;
        List<WebSocketServer> wssList = webSocketMap.get(roomId);
        for (WebSocketServer item : wssList) {
            try {
                if (session.getId().equals(item.session.getId())) {
                    item.sendMessage(session, "已收到信息", true);
                }else {
                    item.sendMessage(item.session, item.user.getName()+":"+msg.get("chatContent").asText(), true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public String getClientMessage(String message) {
        if (message == null || message.isEmpty()) return null;
        String[] split = message.split("\n\n");
        if (split.length == 2) {
            if (split[1] != null && !split[1].isEmpty()) {
                return split[1];
            }
        }
        return null;
    }

    public static synchronized void addSocketServer2Map(WebSocketServer wss) {
        if (wss != null) {
            List<WebSocketServer> wssList = webSocketMap.get(wss.roomId);
            if (wssList == null) {
                wssList = new ArrayList<>(6);
                webSocketMap.put(wss.roomId, wssList);
            }
            wssList.add(wss);
            log.info("WebSocketServer={}", wss);
        }
    }

    /**
     * 实现服务器主动发送消息
     */
    public void sendMessage(Session session, String message, boolean isDirect) throws IOException {
        session.getBasicRemote().sendText(isDirect ? message :getClientMessage(message));
    }

}
