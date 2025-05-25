import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class WebSocketClient extends Thread {
    private Socket socketWebSocket;
    private Socket socketChatTCP;
    private WebSocketServer servidor;
    private BufferedReader entradaWebSocket;
    private OutputStream salidaWebSocket;
    private BufferedReader entradaChat;
    private PrintWriter salidaChat;
    private String nombreUsuario;
    private boolean activo;
    
    public WebSocketClient(Socket socketWebSocket, WebSocketServer servidor) {
        this.socketWebSocket = socketWebSocket;
        this.servidor = servidor;
        this.activo = true;
        
        try {
            // Configurar streams WebSocket
            entradaWebSocket = new BufferedReader(
                new InputStreamReader(socketWebSocket.getInputStream())
            );
            salidaWebSocket = socketWebSocket.getOutputStream();
            
            // Conectar al servidor TCP de chat
            conectarAChatTCP();
            
        } catch (IOException e) {
            System.err.println("Error inicializando WebSocketClient: " + e.getMessage());
            cerrarConexiones();
        }
    }
    
    private void conectarAChatTCP() throws IOException {
        socketChatTCP = new Socket("localhost", 12345);
        entradaChat = new BufferedReader(new InputStreamReader(socketChatTCP.getInputStream()));
        salidaChat = new PrintWriter(socketChatTCP.getOutputStream(), true);
        
        System.out.println("Conectado al servidor TCP de chat");
        
        // Iniciar hilo para escuchar mensajes del servidor TCP
        new Thread(this::escucharChatTCP).start();
    }
    
    @Override
    public void run() {
        try {
            // Leer solicitud de nombre de usuario del servidor TCP
            String solicitudNombre = entradaChat.readLine();
            if (solicitudNombre != null) {
                enviarMensajeWebSocket(solicitudNombre);
            }
            
            // Escuchar mensajes WebSocket del cliente
            byte[] buffer = new byte[1024];
            InputStream input = socketWebSocket.getInputStream();
            
            while (activo) {
                int bytesLeidos = input.read(buffer);
                if (bytesLeidos == -1) break;
                
                String mensaje = decodificarMensajeWebSocket(buffer, bytesLeidos);
                if (mensaje != null && !mensaje.isEmpty()) {
                    manejarMensajeWebSocket(mensaje);
                }
            }
            
        } catch (IOException e) {
            if (activo) {
                System.err.println("Error en WebSocketClient: " + e.getMessage());
            }
        } finally {
            cerrarConexiones();
        }
    }
    
    private void manejarMensajeWebSocket(String mensaje) {
        try {
            // Si es el primer mensaje, es el nombre de usuario
            if (nombreUsuario == null) {
                nombreUsuario = mensaje.trim();
                salidaChat.println(nombreUsuario);
                System.out.println("Usuario WebSocket: " + nombreUsuario);
            } else {
                // Enviar mensaje al servidor TCP
                salidaChat.println(mensaje);
            }
        } catch (Exception e) {
            System.err.println("Error manejando mensaje WebSocket: " + e.getMessage());
        }
    }
    
    private void escucharChatTCP() {
        try {
            String mensaje;
            while (activo && (mensaje = entradaChat.readLine()) != null) {
                enviarMensajeWebSocket(mensaje);
            }
        } catch (IOException e) {
            if (activo) {
                System.err.println("Error escuchando chat TCP: " + e.getMessage());
            }
        }
    }
    
    public void enviarMensaje(String mensaje) {
        enviarMensajeWebSocket(mensaje);
    }
    
    private void enviarMensajeWebSocket(String mensaje) {
        try {
            byte[] mensajeBytes = mensaje.getBytes("UTF-8");
            byte[] frame = codificarMensajeWebSocket(mensajeBytes);
            salidaWebSocket.write(frame);
            salidaWebSocket.flush();
        } catch (IOException e) {
            System.err.println("Error enviando mensaje WebSocket: " + e.getMessage());
            cerrarConexiones();
        }
    }
    
    private byte[] codificarMensajeWebSocket(byte[] mensaje) {
        int longitud = mensaje.length;
        byte[] frame;
        
        if (longitud < 126) {
            frame = new byte[2 + longitud];
            frame[0] = (byte) 0x81; // Texto frame final
            frame[1] = (byte) longitud;
            System.arraycopy(mensaje, 0, frame, 2, longitud);
        } else if (longitud < 65536) {
            frame = new byte[4 + longitud];
            frame[0] = (byte) 0x81;
            frame[1] = 126;
            frame[2] = (byte) (longitud >> 8);
            frame[3] = (byte) (longitud & 0xFF);
            System.arraycopy(mensaje, 0, frame, 4, longitud);
        } else {
            frame = new byte[10 + longitud];
            frame[0] = (byte) 0x81;
            frame[1] = 127;
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) (longitud >> (8 * (7 - i)));
            }
            System.arraycopy(mensaje, 0, frame, 10, longitud);
        }
        
        return frame;
    }
    
    private String decodificarMensajeWebSocket(byte[] buffer, int longitud) {
        if (longitud < 2) return null;
        
        boolean masked = (buffer[1] & 0x80) != 0;
        int payloadLength = buffer[1] & 0x7F;
        int offset = 2;
        
        if (payloadLength == 126) {
            if (longitud < 4) return null;
            payloadLength = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
            offset = 4;
        } else if (payloadLength == 127) {
            if (longitud < 10) return null;
            // Para simplificar, asumimos que no excederá int max
            payloadLength = (int) (((long)(buffer[6] & 0xFF) << 24) |
                                 ((long)(buffer[7] & 0xFF) << 16) |
                                 ((long)(buffer[8] & 0xFF) << 8) |
                                 (buffer[9] & 0xFF));
            offset = 10;
        }
        
        if (masked) {
            if (longitud < offset + 4 + payloadLength) return null;
            byte[] mask = new byte[4];
            System.arraycopy(buffer, offset, mask, 0, 4);
            offset += 4;
            
            byte[] payload = new byte[payloadLength];
            for (int i = 0; i < payloadLength; i++) {
                payload[i] = (byte) (buffer[offset + i] ^ mask[i % 4]);
            }
            
            return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            if (longitud < offset + payloadLength) return null;
            return new String(buffer, offset, payloadLength, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    private void cerrarConexiones() {
        activo = false;
        servidor.removerCliente(socketWebSocket);
        
        try {
            if (salidaChat != null) {
                salidaChat.println("/salir");
                salidaChat.close();
            }
            if (entradaChat != null) entradaChat.close();
            if (socketChatTCP != null) socketChatTCP.close();
            if (salidaWebSocket != null) salidaWebSocket.close();
            if (entradaWebSocket != null) entradaWebSocket.close();
            if (socketWebSocket != null) socketWebSocket.close();
        } catch (IOException e) {
            System.err.println("Error cerrando conexiones: " + e.getMessage());
        }
    }
    
    public Socket getSocket() {
        return socketWebSocket;
    }
    
    public String getNombreUsuario() {
        return nombreUsuario;
    }
}