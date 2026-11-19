package pclge2.core;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 联机聊天：局域网 UDP 广播。
 * 协议与 PCLGE2 Windows 版保持一致：[CHAT]昵称||消息
 */
public final class LanChat implements AutoCloseable {

    public static final int PORT = 25567;
    /** EasyTier 虚拟网段广播地址（与 Windows 版一致） */
    public static final String EASYTIER_BROADCAST = "10.114.114.255";
    public static final String GLOBAL_BROADCAST = "255.255.255.255";

    private DatagramSocket socket;
    private Thread receiver;
    private volatile boolean running;
    private String nickname = "玩家";

    /** 在线用户：昵称 -> 最后活跃时间 */
    private final Map<String, Long> peers = new ConcurrentHashMap<>();

    private Consumer<String> onMessage = s -> {
    };
    private BiConsumer<String, String> onChat = (user, msg) -> {
    };
    private Runnable onPeersChanged = () -> {
    };

    public void setOnChat(BiConsumer<String, String> onChat) {
        this.onChat = onChat;
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    public void setOnPeersChanged(Runnable r) {
        this.onPeersChanged = r;
    }

    public Set<String> peers() {
        prune();
        return new LinkedHashSet<>(peers.keySet());
    }

    private void prune() {
        long now = System.currentTimeMillis();
        peers.entrySet().removeIf(e -> now - e.getValue() > 60_000);
    }

    /** 启动监听（绑定端口失败会抛异常，例如端口被占用） */
    public void start(String nickname) throws IOException {
        this.nickname = nickname;
        socket = new DatagramSocket(PORT);
        socket.setBroadcast(true);
        socket.setSoTimeout(1000);
        running = true;
        receiver = new Thread(this::receiveLoop, "PCLGE2-LanChat");
        receiver.setDaemon(true);
        receiver.start();
        onMessage.accept("联机聊天已启动，端口 " + PORT);
    }

    private void receiveLoop() {
        byte[] buf = new byte[4096];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String text = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                handle(text, packet.getAddress());
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (running) {
                    onMessage.accept("接收失败：" + e.getMessage());
                }
            }
        }
    }

    private void handle(String text, InetAddress from) {
        if (!text.startsWith("[CHAT]")) {
            return;
        }
        String payload = text.substring("[CHAT]".length());
        int idx = payload.indexOf("||");
        if (idx < 0) {
            return;
        }
        String user = payload.substring(0, idx);
        String msg = payload.substring(idx + 2);
        peers.put(user, System.currentTimeMillis());
        onPeersChanged.run();
        onChat.accept(user, msg);
    }

    /** 广播一条聊天消息 */
    public void send(String message) throws IOException {
        if (socket == null) {
            throw new IOException("聊天尚未启动");
        }
        String text = "[CHAT]" + nickname + "||" + message;
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (String addr : new String[]{EASYTIER_BROADCAST, GLOBAL_BROADCAST}) {
            try {
                socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(addr), PORT));
            } catch (IOException ignored) {
            }
        }
        peers.put(nickname, System.currentTimeMillis());
        onPeersChanged.run();
    }

    /** 发送心跳，让同网段其他人能看到自己 */
    public void heartbeat() {
        try {
            send("");
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (receiver != null) {
            receiver.interrupt();
        }
    }
}
