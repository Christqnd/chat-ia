import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Servidor {
    private int puerto;
    private ServerSocket serverSocket;
    private List<ManejadorCliente> clientesConectados;
    private boolean ejecutando;
    
    public Servidor(int puerto) {
        this.puerto = puerto;
        this.clientesConectados = new ArrayList<>();
        this.ejecutando = false;
    }
    
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(puerto);
            ejecutando = true;
            
            System.out.println("=== SERVIDOR DE CHAT INICIADO ===");
            System.out.println("Puerto: " + puerto);
            System.out.println("Esperando conexiones...");
            System.out.println("==================================");
            
            aceptarConexiones();
            
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
        }
    }
    
    private void aceptarConexiones() {
        while (ejecutando) {
            try {
                Socket socketCliente = serverSocket.accept();
                System.out.println("Nueva conexión desde: " + socketCliente.getInetAddress());
                
                ManejadorCliente manejador = new ManejadorCliente(socketCliente, this);
                clientesConectados.add(manejador);
                manejador.start();
                
                System.out.println("Clientes conectados: " + clientesConectados.size());
                
            } catch (IOException e) {
                if (ejecutando) {
                    System.err.println("Error al aceptar conexión: " + e.getMessage());
                }
            }
        }
    }
    
    public synchronized void transmitirMensaje(String mensaje, ManejadorCliente remitente) {
        System.out.println("Transmitiendo: " + mensaje);
        
        List<ManejadorCliente> clientesDesconectados = new ArrayList<>();
        
        for (ManejadorCliente cliente : clientesConectados) {
            if (cliente != remitente) {
                try {
                    cliente.enviarMensaje(mensaje);
                } catch (Exception e) {
                    System.err.println("Error enviando mensaje a " + cliente.getNombreUsuario());
                    clientesDesconectados.add(cliente);
                }
            }
        }
        
        // Remover clientes desconectados
        for (ManejadorCliente clienteDesconectado : clientesDesconectados) {
            removerCliente(clienteDesconectado);
        }
    }
    
    public synchronized void removerCliente(ManejadorCliente cliente) {
        clientesConectados.remove(cliente);
        System.out.println("Cliente " + cliente.getNombreUsuario() + " desconectado. " +
                         "Clientes restantes: " + clientesConectados.size());
    }
    
    public void detener() {
        ejecutando = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar servidor: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        int puerto = 12345;
        
        if (args.length > 0) {
            try {
                puerto = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto inválido, usando puerto por defecto: " + puerto);
            }
        }
        
        Servidor servidor = new Servidor(puerto);
        
        // Agregar shutdown hook para cerrar el servidor limpiamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nCerrando servidor...");
            servidor.detener();
        }));
        
        servidor.iniciar();
    }
}