import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {
    private String nombreUsuario;
    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter salida;
    private boolean conectado;
    
    public Cliente(String host, int puerto, String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
        this.conectado = false;
        
        try {
            socket = new Socket(host, puerto);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);
            conectado = true;
            
            System.out.println("Conectado al servidor de chat en " + host + ":" + puerto);
            
        } catch (IOException e) {
            System.err.println("Error al conectar con el servidor: " + e.getMessage());
        }
    }
    
    public void iniciar() {
        if (!conectado) {
            System.out.println("No se pudo establecer la conexión.");
            return;
        }
        
        try {
            // Leer solicitud de nombre de usuario del servidor
            String solicitudNombre = entrada.readLine();
            System.out.println(solicitudNombre);
            
            // Enviar nombre de usuario
            salida.println(nombreUsuario);
            
            // Iniciar hilo para escuchar mensajes
            Thread hiloEscucha = new Thread(this::escucharMensajes);
            hiloEscucha.setDaemon(true);
            hiloEscucha.start();
            
            // Interfaz para enviar mensajes
            enviarMensajes();
            
        } catch (IOException e) {
            System.err.println("Error durante la comunicación: " + e.getMessage());
        } finally {
            cerrarConexion();
        }
    }
    
    private void enviarMensajes() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n=== CHAT INICIADO ===");
        System.out.println("Escribe tus mensajes (escribe '/salir' para desconectar):");
        System.out.println("=====================\n");
        
        while (conectado) {
            String mensaje = scanner.nextLine();
            
            if (mensaje.equalsIgnoreCase("/salir")) {
                break;
            }
            
            if (!mensaje.trim().isEmpty()) {
                salida.println(mensaje);
            }
        }
    }
    
    private void escucharMensajes() {
        try {
            String mensaje;
            while (conectado && (mensaje = entrada.readLine()) != null) {
                System.out.println(mensaje);
            }
        } catch (IOException e) {
            if (conectado) {
                System.err.println("Error al recibir mensajes: " + e.getMessage());
            }
        }
    }
    
    private void cerrarConexion() {
        conectado = false;
        try {
            if (salida != null) {
                salida.println("/salir");
                salida.close();
            }
            if (entrada != null) entrada.close();
            if (socket != null) socket.close();
            
            System.out.println("Desconectado del chat.");
            
        } catch (IOException e) {
            System.err.println("Error al cerrar conexión: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        String host = "localhost";
        int puerto = 12345;
        String nombreUsuario = "Usuario";
        
        // Permitir parámetros de línea de comandos
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try {
                puerto = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto inválido, usando puerto por defecto: " + puerto);
            }
        }
        if (args.length >= 3) nombreUsuario = args[2];
        
        // Si no se proporciona nombre por parámetro, solicitarlo
        if (args.length < 3) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Ingrese su nombre de usuario: ");
            String nombre = scanner.nextLine().trim();
            if (!nombre.isEmpty()) {
                nombreUsuario = nombre;
            }
        }
        
        Cliente cliente = new Cliente(host, puerto, nombreUsuario);
        cliente.iniciar();
    }
}