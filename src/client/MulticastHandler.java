package client;

import common.Message;
import common.MessageType;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class MulticastHandler {
    public static final String MULTICAST_ADDRESS = "230.0.0.1";
    public static final int MULTICAST_PORT = 4446;

    private MulticastSocket multicastSocket;
    private InetSocketAddress groupAddress;
    private NetworkInterface networkInterface;
    private boolean isListening = false;
    private Consumer<Message> messageConsumer;

    public MulticastHandler(Consumer<Message> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    public void startListening() {
        if (isListening) return;
        isListening = true;

        new Thread(() -> {
            try {
                multicastSocket = new MulticastSocket(MULTICAST_PORT);
                groupAddress = new InetSocketAddress(InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
                networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                if (networkInterface == null) {
                    networkInterface = NetworkInterface.getNetworkInterfaces().nextElement();
                }

                multicastSocket.joinGroup(groupAddress, networkInterface);
                byte[] buffer = new byte[65507];

                while (isListening) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);

                    try (ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                         ObjectInputStream ois = new ObjectInputStream(bais)) {
                        Object obj = ois.readObject();
                        if (obj instanceof Message) {
                            Message msg = (Message) obj;
                            if (messageConsumer != null) {
                                messageConsumer.accept(msg);
                            }
                        }
                    } catch (Exception e) {
                        // ignore malformed UDP packet
                    }
                }
            } catch (Exception e) {
                if (isListening) {
                    System.err.println("[UDP Multicast] Lỗi lắng nghe: " + e.getMessage());
                }
            }
        }).start();
    }

    public void sendMulticastMessage(Message msg) {
        new Thread(() -> {
            try (DatagramSocket senderSocket = new DatagramSocket()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                    oos.writeObject(msg);
                    oos.flush();
                }
                byte[] data = baos.toByteArray();
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(data, data.length, group, MULTICAST_PORT);
                senderSocket.send(packet);
            } catch (Exception e) {
                System.err.println("[UDP Multicast] Lỗi gửi tin: " + e.getMessage());
            }
        }).start();
    }

    public void stop() {
        isListening = false;
        try {
            if (multicastSocket != null && groupAddress != null && networkInterface != null) {
                multicastSocket.leaveGroup(groupAddress, networkInterface);
            }
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                multicastSocket.close();
            }
        } catch (Exception ignored) {}
    }
}
