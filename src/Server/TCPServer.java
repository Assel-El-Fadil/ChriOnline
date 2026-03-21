package Server;

import java.net.*;

public class TCPServer {

    public static void main(String[] args) throws Exception {

        try(ServerSocket server = new ServerSocket(8081)){
            System.out.println("Server started...");

            while (true) {

                Socket client = server.accept();
                System.out.println("Client connected");

                ClientHandler handler = new ClientHandler(client);
                handler.start();
            }
        }
    }
}
