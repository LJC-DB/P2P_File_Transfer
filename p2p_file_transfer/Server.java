package p2p_file_transfer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import p2p_file_transfer.Message;



public class Server {
    private static InetAddress serverAddress;
    private static int serverPort = 10098;
    private static ConcurrentHashMap<String, ArrayList<String>> files = new ConcurrentHashMap<String, ArrayList<String>>();
    private static ConcurrentHashMap<String, String> waitingAlive = new ConcurrentHashMap<String, String>();
    private static DatagramSocket serverSocket;

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        try{
            // 4g) Receives the server's socket IP from keyboard to initialize
            System.out.println("IP: ");
            serverAddress = InetAddress.getByName(scanner.nextLine());
            serverSocket = new DatagramSocket(serverPort, serverAddress);
        } catch (Exception e){
            e.printStackTrace();
        }

        // Starts the thread to send Alive messages for each peer that joins server
        new Alive().start();

        while(true){
            byte[] text = new byte[1024];
            DatagramPacket readPacket = new DatagramPacket(text, text.length);
            serverSocket.receive(readPacket);

            InetAddress requestAddress = readPacket.getAddress();
            int requestPort = readPacket.getPort();

            String request = new String(readPacket.getData(),
                        readPacket.getOffset(),
                        readPacket.getLength());

            // 4a) Initiates a thread to treat the response, enabling the server to respond
            // and read new requests simultaneously
            Rensponse response = new Rensponse(request, requestAddress, requestPort);
            response.start();
        }
    }

    public static void sendMessage(Message message, InetAddress sendAddress, int sendPort) {
        try{
            byte[] text = new byte[1024];
            text = message.toJson().getBytes();
            DatagramPacket sendPacket = new DatagramPacket(text, text.length, sendAddress, sendPort);
            serverSocket.send(sendPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 4f) Class responsible for sending an Alive message to confirm that the peer
    // did not unexpectedly closed its connection
    private static class Alive extends Thread{
        @Override
        public void run() {
            while(true){
                try{
                    // Send the message for each peer
                    for(String client : files.keySet()){
                        Message message = Message.aliveMessage();
                        String[] clientInfo = client.split(":");
                        InetAddress clientAddress = InetAddress.getByName(clientInfo[0]);
                        int clientPort = Integer.valueOf(clientInfo[1]);
                        sendMessage(message, clientAddress, clientPort);
                        // Add the peer to the list of peers that need to respond
                        waitingAlive.putIfAbsent(client, message.ID);
                    }

                    // Wait the required time between Alive checks
                    TimeUnit.SECONDS.sleep(30);

                    for(String client : waitingAlive.keySet()){
                        System.out.print("Peer " + client + " morto. Eliminando seus arquivos");
                        for (String fn : files.get(client))
                            System.out.print(" " + fn);
                        System.out.println();
                    }

                    // Removes the peers that did not respond on the interval
                    files.keySet().removeAll(waitingAlive.keySet());

                } catch (InterruptedException e) {
                    // If the thread is restarted before the required time it will not
                    // remove any peer
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // Empty the list after each cycle, either if it was interrupted or not
                    waitingAlive.clear();
                }
            }
        }
    }

    private static class Rensponse extends Thread{
        private String request;
        private InetAddress requestAddress;
        private int requestPort;
        private String client;

        public Rensponse(String request, InetAddress requestAddress, int requestPort){
            this.request = request;
            this.requestAddress = requestAddress;
            this.requestPort = requestPort;
            client = requestAddress.getHostAddress() + ":" + requestPort;
        }
        @Override
        public void run() {
            Message message = ProcessRequest(request);

            if(message == null)
                return;

            sendMessage(message, requestAddress, requestPort);
        }

        private Message ProcessRequest(String request) {
            Message requestMessage = Message.fromJson(request);
            if(!requestMessage.type.equals(Message.Join) && !files.containsKey(client))
                return null;

            switch (requestMessage.type){
                case Message.Join: return Join(requestMessage);
                case Message.Leave: return Leave(requestMessage);
                case Message.Update: return Update(requestMessage);
                case Message.Search: return Search(requestMessage);
                case Message.Alive_OK: return Alive_OK(requestMessage);
            }

            return null;
        }

        // 4b) Treats Join requests from the peer
        private Message Join(Message requestMessage) {
            ArrayList<String> fileNames = requestMessage.fileNames;
            // Save the client information
            files.putIfAbsent(client, fileNames);
            System.out.print("Peer " + client + " adicionado com arquivos");
            for (String fn : fileNames)
                System.out.print(" " + fn);
            System.out.println();
            // Prepare to send an Ok response
            return Message.joinOKMessage(requestMessage.ID);
        }

        // 4c) Treats Leave requests from the peer
        private Message Leave(Message requestMessage){
            // Removes the client information
            files.remove(client);
            // Prepare to send an Ok response
            return Message.leaveOKMessage(requestMessage.ID);
        }

        // 4e) Treats Update requests from the peer
        private Message Update(Message requestMessage) {
            // Receives the file downloaded from the message
            String file = requestMessage.file;
            ArrayList<String> fileList;
            // Add the received file to the list of files pertaining to
            // the client if it is not already present
            if ((fileList = files.get(client)) != null)
                if(!files.get(client).contains(file))
                    fileList.add(file);
            // Prepare to send an Ok response
            return Message.updateOKMessage(requestMessage.ID);
        }

        // 4d) Treats Search requests from the peer
        private Message Search(Message requestMessage) {
            // Receives the requested file from the message
            String file = requestMessage.file;
            System.out.println("Peer " + client + " solicitou arquivo " + file);
            // Creates an empty list to save the options of peers from who
            // the file can be downloaded
            ArrayList<String> clientsWithFile = new ArrayList<String>();
            ArrayList<String> fileList;
            // Check the list provided from each client for the file and
            // add it to the list if found
            for (String client : files.keySet())
                if ((fileList = files.get(client)) != null)
                    if(fileList.contains(file))
                        clientsWithFile.add(client);
            // Prepare to send an Ok response
            return Message.searchOKMessage(requestMessage.ID, clientsWithFile);
        }

        // 4f) Receives the Alive_OK response from the peer to indicate
        // that it is still alive
        private Message Alive_OK(Message requestMessage) {
            String ID = waitingAlive.get(client);
            // Removes the peer from the list of peers that still did not respond
            if(ID != null && ID.equals(requestMessage.ID))
                waitingAlive.remove(client);
            return null;
        }
    }
}
