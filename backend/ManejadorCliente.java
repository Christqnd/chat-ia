import java.io.*;
import java.net.Socket;

public class ManejadorCliente extends Thread {
    private Socket socketCliente;
    private BufferedReader entrada;
    private PrintWriter salida;
    private Servidor servidor;
    private String nombreUsuario;
    
    public ManejadorCliente(Socket socket, Servidor servidor) {
        this.socketCliente = socket;
        this.servidor = servidor;
        
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), true);
            
            // Solicitar nombre de usuario al conectarse
            salida.println("Ingrese su nombre de usuario:");
            nombreUsuario = entrada.readLine();
            
            if (nombreUsuario == null || nombreUsuario.trim().isEmpty()) {
                nombreUsuario = "Usuario" + socket.getPort();
            }
            
            // Notificar que el usuario se conectó
            servidor.transmitirMensaje("*** " + nombreUsuario + " se ha unido al chat ***", null);
            
        } catch (IOException e) {
            System.err.println("Error al inicializar ManejadorCliente: " + e.getMessage());
            cerrarConexion();
        }
    }
    
    @Override
    public void run() {
        try {
            String mensajeRecibido;
            while ((mensajeRecibido = entrada.readLine()) != null) {
                if (mensajeRecibido.equalsIgnoreCase("/salir")) {
                    break;
                }
                
                Mensaje mensaje = new Mensaje(nombreUsuario, mensajeRecibido);
                servidor.transmitirMensaje(mensaje.formatearParaEnvio(), this);
            }
        } catch (IOException e) {
            System.err.println("Error en la comunicación con " + nombreUsuario + ": " + e.getMessage());
        } finally {
            servidor.transmitirMensaje("*** " + nombreUsuario + " ha salido del chat ***", null);
            servidor.removerCliente(this);
            cerrarConexion();
        }
    }
    
    public void enviarMensaje(String mensaje) {
        if (salida != null) {
            salida.println(mensaje);
        }
    }
    
    private void cerrarConexion() {
        try {
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
            if (socketCliente != null) socketCliente.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar conexión: " + e.getMessage());
        }
    }
    
    public String getNombreUsuario() {
        return nombreUsuario;
    }
}