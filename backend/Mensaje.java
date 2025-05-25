import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Mensaje {
    private String remitente;
    private String contenido;
    private LocalDateTime timestamp;
    
    public Mensaje(String remitente, String contenido) {
        this.remitente = remitente;
        this.contenido = contenido;
        this.timestamp = LocalDateTime.now();
    }
    
    public String formatearParaEnvio() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        return "[" + timestamp.format(formatter) + "] " + remitente + ": " + contenido;
    }
    
    // Getters
    public String getRemitente() { return remitente; }
    public String getContenido() { return contenido; }
    public LocalDateTime getTimestamp() { return timestamp; }
}