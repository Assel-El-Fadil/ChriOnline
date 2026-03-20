package Server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPServer {
    public static void main(String[] args) throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(8081);
        byte[] donneeRecue = new byte[1024];
        byte[] donneeEnvoye;
        while (true){
            DatagramPacket paquetRecu = new DatagramPacket(donneeRecue,donneeRecue.length);
            serverSocket.receive(paquetRecu);
            String phrase = new String(paquetRecu.getData());
            InetAddress addresseIP = paquetRecu.getAddress();
            int port = paquetRecu.getPort();
            String phraseMaj = phrase.toUpperCase();
            donneeEnvoye =  phraseMaj.getBytes();
            DatagramPacket paquetenvoye = new DatagramPacket(donneeEnvoye,donneeEnvoye.length, addresseIP,port);
            serverSocket.send(paquetenvoye);
        }
    }
}
