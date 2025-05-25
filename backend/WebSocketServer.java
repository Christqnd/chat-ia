import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.util.Base64;

public class WebSocketServer {
    private int puerto;
    private ServerSocket serverSocket;
    private Map<Socket, WebSocketClient> clientesWebSocket;
    private boolean ejecutando;
    
    // Conexión al servidor TCP de chat existente
    private String hostChat = "localhost";
    private int puertoChat = 12345;
    
    public WebSocketServer(int puerto) {
        this.puerto = puerto;
        this.clientesWebSocket = new ConcurrentHashMap<>();
        this.ejecutando = false;
    }
    
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(puerto);
            ejecutando = true;
            
            System.out.println("=== SERVIDOR WEBSOCKET INICIADO ===");
            System.out.println("Puerto WebSocket: " + puerto);
            System.out.println("Conectando a Chat TCP: " + hostChat + ":" + puertoChat);
            System.out.println("===================================");
            
            while (ejecutando) {
                Socket clienteSocket = serverSocket.accept();
                System.out.println("Nueva conexión WebSocket desde: " + clienteSocket.getInetAddress());
                
                // Manejar handshake WebSocket
                new Thread(() -> manejarNuevaConexion(clienteSocket)).start();
            }
            
        } catch (IOException e) {
            if (ejecutando) {
                System.err.println("Error en servidor WebSocket: " + e.getMessage());
            }
        }
    }
    
    private void manejarNuevaConexion(Socket clienteSocket) {
        try {
            BufferedReader entrada = new BufferedReader(
                new InputStreamReader(clienteSocket.getInputStream())
            );
            PrintWriter salida = new PrintWriter(clienteSocket.getOutputStream(), true);
            
            // Leer headers del handshake WebSocket
            String linea;
            Map<String, String> headers = new HashMap<>();
            
            while ((linea = entrada.readLine()) != null && !linea.isEmpty()) {
                if (linea.contains(": ")) {
                    String[] partes = linea.split(": ", 2);
                    headers.put(partes[0].toLowerCase(), partes[1]);
                }
            }
            
            // Verificar si es una solicitud WebSocket válida
            if (headers.containsKey("sec-websocket-key")) {
                String webSocketKey = headers.get("sec-websocket-key");
                String acceptKey = generarWebSocketAcceptKey(webSocketKey);
                
                // Enviar respuesta de handshake
                salida.println("HTTP/1.1 101 Switching Protocols");
                salida.println("Upgrade: websocket");
                salida.println("Connection: Upgrade");
                salida.println("Sec-WebSocket-Accept: " + acceptKey);
                salida.println();
                salida.flush();
                
                // Crear cliente WebSocket
                WebSocketClient cliente = new WebSocketClient(clienteSocket, this);
                clientesWebSocket.put(clienteSocket, cliente);
                cliente.start();
                
                System.out.println("Handshake WebSocket completado");
            } else {
                clienteSocket.close();
            }
            
        } catch (IOException e) {
            System.err.println("Error en handshake WebSocket: " + e.getMessage());
            try {
                clienteSocket.close();
            } catch (IOException ex) {
                // Ignorar
            }
        }
    }
    
    private String generarWebSocketAcceptKey(String key) {
        try {
            String magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            String concat = key + magic;
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(concat.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error generando WebSocket accept key", e);
        }
    }
    
    public void removerCliente(Socket socket) {
        clientesWebSocket.remove(socket);
        System.out.println("Cliente WebSocket desconectado. Restantes: " + clientesWebSocket.size());
    }
    
    public void transmitirATodosLosClientes(String mensaje) {
        List<Socket> desconectados = new ArrayList<>();
        
        for (WebSocketClient cliente : clientesWebSocket.values()) {
            try {
                cliente.enviarMensaje(mensaje);
            } catch (Exception e) {
                desconectados.add(cliente.getSocket());
            }
        }
        
        // Remover clientes desconectados
        for (Socket socket : desconectados) {
            removerCliente(socket);
        }
    }
    
    public void detener() {
        ejecutando = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error cerrando servidor WebSocket: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        int puerto = 12346; // Puerto WebSocket (puerto TCP + 1)
        
        if (args.length > 0) {
            try {
                puerto = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto inválido, usando puerto por defecto: " + puerto);
            }
        }
        
        WebSocketServer servidor = new WebSocketServer(puerto);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nCerrando servidor WebSocket...");
            servidor.detener();
        }));
        
        servidor.iniciar();
    }
}