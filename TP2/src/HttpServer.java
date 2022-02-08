import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// este protocolo ocorre na camada 7 do modelo OSI

public class HttpServer implements Runnable {
    private int port;
    private File folder;

    public HttpServer(int port, File folder) {
        this.folder = folder;
        this.port = port;
    }

    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) { // using a TCP socket to handle a TCP connection
            while (true) {
                try (Socket client = serverSocket.accept()) { // espera ate poder ter uma conexao de um cliente para
                                                              // aceitar
                    handleClient(client, folder);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket client, File folder) throws IOException {

        BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream())); // ler a request para um
                                                                                                // buffered reader

        StringBuilder requestBuilder = new StringBuilder();
        String line;
        while (!(line = br.readLine()).isBlank()) { // A request acaba sempre com uma linha vazia (\r\n), portanto
                                                    // terminamos de ler nesse caso
            requestBuilder.append(line + "\r\n"); //// ler a request a partir do socket do cliente.
        }

        File[] files = getFiles2(folder);
        String[] filesToLogs = fileArrayToStringArray2(files);

        // Parsing da request
        String request = requestBuilder.toString();
        String[] requestsLines = request.split("\r\n"); // \r\n indica new line
        String[] requestLine = requestsLines[0].split(" "); // separar em: {"GET", "/", "HTTP/1.1"}
        String method = requestLine[0]; // method = "GET"
        String path = requestLine[1]; // path = "/"
        String version = requestLine[2]; // version = "HTTP/1.1"
        String host = requestsLines[1].split(" ")[1]; // host = "localhost:8080"

        List<String> headers = new ArrayList<>();
        for (int h = 2; h < requestsLines.length; h++) { // headers começam a partir da terceira linha (corresponde ao
                                                         // indice 2)
            String header = requestsLines[h];
            headers.add(header);
        }

        String accessLog = String.format("Client %s, method %s, path %s, version %s, host %s, headers %s",
                client.toString(), method, path, version, host, headers.toString());

        if (!Objects.equals(path, "/")) {
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write("HTTP/1.1 200 OK\r\n".getBytes());
            clientOutput.write(("ContentType: text/html\r\n").getBytes());
            clientOutput.write("\r\n".getBytes());
            clientOutput.write("<b>404 Not found</b>".getBytes());
            clientOutput.write("\r\n\r\n".getBytes());
            clientOutput.flush();
            client.close();
        }

        else {
            // enviar a resposta para o output stream do cliente
            OutputStream clientOutput = client.getOutputStream();
            clientOutput.write("HTTP/1.1 200 OK\r\n".getBytes());
            clientOutput.write(("ContentType: text/html\r\n").getBytes());
            clientOutput.write("\r\n".getBytes());
            for (String s : filesToLogs) {
                clientOutput.write(s.getBytes());
            }
            clientOutput.write("\r\n\r\n".getBytes());
            clientOutput.flush();
            client.close();
        }

    }

    public static File[] getFiles2(File folderHttp) {
        File[] allFiles_Http = folderHttp.listFiles();

        return allFiles_Http;
    }

    public static String[] fileArrayToStringArray2(File[] files) {
        String[] names = new String[files.length];
        String[] sorted = new String[files.length];

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                sorted[i] = "[DIRECTORY] " + files[i].getName();
            } else {
                sorted[i] = files[i].getName();
            }
        }
        Arrays.sort(sorted);
        for (int i = 0; i < files.length; i++) {
            if (sorted[i].startsWith("[DIRECTORY]")) {
                String[] diretoria = sorted[i].split(" ");
                names[i] = "<b style=\"background-color:DodgerBlue;\">" + diretoria[0] + "</b>" + "<b>" + diretoria[1]
                        + "</b><br>";
            } else {
                names[i] = "<b>" + sorted[i] + "</b><br>";
            }
        }
        return names;
    }

}
