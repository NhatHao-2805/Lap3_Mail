package client;

import common.Message;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class BroadcastHandler {
    public static final int BROADCAST_PORT = 8890;
    public static final int SERVER_DISCOVERY_PORT = 8889;

    private DatagramSocket broadcastSocket;
    private boolean isListening = false;
    private Consumer<Message> messageConsumer;

    public BroadcastHandler(Consumer<Message> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    public void startListening() {
        if (isListening) return;
        isListening = true;

        new Thread(() -> {
            try {
                broadcastSocket = new DatagramSocket(null);
                broadcastSocket.setReuseAddress(true);
                broadcastSocket.bind(new InetSocketAddress(BROADCAST_PORT));
                byte[] buffer = new byte[65507];

                while (isListening) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    broadcastSocket.receive(packet);

                    try (ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                         ObjectInputStream ois = new ObjectInputStream(bais)) {
                        Object obj = ois.readObject();
                        if (obj instanceof Message) {
                            Message msg = (Message) obj;
                            if (messageConsumer != null) {
                                messageConsumer.accept(msg);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                if (isListening) {
                    System.err.println("[UDP Broadcast] Lỗi lắng nghe: " + e.getMessage());
                }
            }
        }).start();
    }

    public void sendBroadcastMessage(Message msg) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                    oos.writeObject(msg);
                    oos.flush();
                }
                byte[] data = baos.toByteArray();
                InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
                DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, BROADCAST_PORT);
                socket.send(packet);
            } catch (Exception e) {
                System.err.println("[UDP Broadcast] Lỗi gửi: " + e.getMessage());
            }
        }).start();
    }

    public static void discoverServer(Consumer<String> onFoundCallback, Runnable onTimeoutCallback) {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.setSoTimeout(2500); // 2.5s timeout

                byte[] sendData = "DISCOVER_CHAT_SERVER".getBytes();
                DatagramPacket sendPacket = new DatagramPacket(
                        sendData, sendData.length, InetAddress.getByName("255.255.255.255"), SERVER_DISCOVERY_PORT
                );
                socket.send(sendPacket);

                byte[] recvBuf = new byte[1024];
                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(recvPacket);

                String response = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
                if (response.startsWith("CHAT_SERVER_OFFER")) {
                    String serverIp = recvPacket.getAddress().getHostAddress();
                    if (onFoundCallback != null) onFoundCallback.accept(serverIp);
                    return;
                }
            } catch (SocketTimeoutException e) {
                if (onTimeoutCallback != null) onTimeoutCallback.run();
            } catch (Exception e) {
                if (onTimeoutCallback != null) onTimeoutCallback.run();
            }
        }).start();
    }

    public void stop() {
        isListening = false;
        if (broadcastSocket != null && !broadcastSocket.isClosed()) {
            broadcastSocket.close();
        }
    }
}
