package p2p_file_transfer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import p2p_file_transfer.Message;

public class Peer {
    private static InetAddress peerAddress;
    private static int peerPort;
    private static String peerName;
    private static DatagramSocket peerSocket;
    private static ArrayList<String> fileNames;
    private static File folder;
    private static String fileToDownload;
    private static ConcurrentHashMap<String, Message> needResend = new ConcurrentHashMap<String, Message>();
    private static Responses responsesThread = null;
    private static ArrayList<String> receivedClients;
    private static Scanner scanner = new Scanner(System.in);
    private static Random random = new Random();

    public static void main(String[] args) throws Exception {

        // 5l) Initializes the peer using a Join Message that will request for the user to
        // input the IP, port and folder to be utilized
        Join();

        while(true){
            System.out.println("Selecione próxima ação:");
            System.out.println("1 - Join");
            System.out.println("2 - Leave");
            System.out.println("3 - Download");
            System.out.println("4 - Search");

            String input;
            try {
                while((input = scanner.nextLine()).equals(""));
                int value = Integer.valueOf(input);
                switch (value){
                    case 1:
                            Join();
                            break;

                    case 2:
                            Leave();
                            break;

                    case 3:
                            new DownloadRequest(fileToDownload, receivedClients).start();;
                            break;
                    case 4:
                            Search();
                            break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 5b) Sends a Join request to the server
    private static void Join(){
        try {
            System.out.println("IP: ");
            peerAddress = InetAddress.getByName(scanner.nextLine());

            System.out.println("Porta: ");
            peerPort = Integer.valueOf(scanner.nextLine());

            peerSocket = new DatagramSocket(peerPort, peerAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }

        peerName = peerAddress.getHostAddress() + ":" + peerPort;

        System.out.println("Pasta: ");
        folder = new File(scanner.nextLine());

        File[] listOfFiles = folder.listFiles();
        fileNames = new ArrayList<String>();
        for(File f : listOfFiles)
            fileNames.add(f.getName());
        Message message = Message.joinMessage(fileNames);

        sendMessage(message);

        if(responsesThread == null){
            // 5a) After sending a request to join the server
            // starts looking for messages from the server and
            // other peers simultaneously
            responsesThread = new Responses();
            responsesThread.start();
        }
    }

    // 5c) Sends a Leave request to the server
    private static void Leave(){
        Message message = Message.leaveMessage();
        sendMessage(message);
    }

    // 5d) Sends a Update request to the server
    private static void Update(String file) {
        Message message = Message.updateMessage(file);
        sendMessage(message);
    }

    // 5f) Sends a Search request to the server
    private static void Search() {
        System.out.println("Nome do arquivo:");
        fileToDownload = scanner.nextLine();
        Message message = Message.searchMessage(fileToDownload);
        sendMessage(message);
    }

    // 5h) Sends a Download request to the selected peer
    private static class DownloadRequest extends Thread{
        String file;
        String client;
        String IP;
        int port;
        ArrayList<String> clients;

        private DownloadRequest(String file, ArrayList<String> clients){
            System.out.println("IP: ");
            this.IP = scanner.nextLine();
            System.out.println("Porta: ");
            this.port = Integer.valueOf(scanner.nextLine());
            this.file = file;
            this.clients = clients;
            this.client = IP + ":" + port;
        }

        private DownloadRequest(String file, String client, ArrayList<String> clients){
            this.file = file;
            this.client = client;
            this.clients = clients;
            String[] clientInfo = client.split(":");
            this.IP = clientInfo[0];
            this.port = Integer.valueOf(clientInfo[1]);
        }

        @Override
        public void run() {
            try {
                if(peerName.equals(client))
                    return;

                Socket s = new Socket(IP, port);

                DataOutputStream writer = new DataOutputStream(s.getOutputStream());
                writer.writeBytes(file + "\n");

                InputStreamReader is = new InputStreamReader(s.getInputStream());
                BufferedReader readerAccept = new BufferedReader(is);
                String accept = readerAccept.readLine();


                // 5k) If the Download request was rejected, it will try again until a peer accepts
                // the request
                if(accept.equals(Message.Download_Negado)){
                    String newClient = client;
                    int size = clients.size();
                    // If there are other peers available to provide the file, it will randomly choose a new peer
                    // from the list provided by the search method
                    if(size > 1) {
                        int i;
                        do i = random.nextInt(size); while ((newClient = clients.get(i)).equals(client));
                    }
                    // Otherwise, the thread will await some time before attempting the request with the same peer
                    else {
                        boolean done = false;
                        while (!done) {
                            try {
                                TimeUnit.SECONDS.sleep(2);
                                done = true;
                            } catch (InterruptedException e) {
                                // If the thread was awaken before the time was elapsed, only restarts
                                // the count on the next cycle
                            }
                        }
                    }
                    System.out.println("peers " + client + " negou o download, pedindo agora para o peer " + newClient);
                    new DownloadRequest(file, newClient, clients).start();
                    return;
                }

                String downloadLocation = new File(folder, file).getPath();

                DataInputStream reader = new DataInputStream(s.getInputStream());
                FileOutputStream fileWriter = new FileOutputStream(downloadLocation);

                byte[] buffer = new byte[1024];

                int read;
                while((read = reader.read(buffer)) > 0){
                    fileWriter.write(buffer, 0, read);
                }

                System.out.println("Arquivo " + file + " baixado com sucesso na pasta " + folder.getPath());

                fileNames.add(file);
                Update(file);

                writer.close();
                fileWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendMessage(Message message){
        InetAddress address;
        try{
            address = InetAddress.getByName("localhost");
            // If not specified, sends the message to server's default location
            sendMessage(message, address, 10098);

        } catch (Exception e){
            e.printStackTrace();
        }
    }
    private static void sendMessage(Message message, InetAddress address, int port){
        try {
            if(peerSocket == null)
                return;

            byte[] text = new byte[1024];
            text = message.toJson().getBytes();
            DatagramPacket sendPacket = new DatagramPacket(text, text.length, address, port);
            peerSocket.send(sendPacket);
            if(message.type != Message.Alive_OK){
                if(!needResend.containsKey(message.ID))
                    new Resend(message.ID).start();
                needResend.putIfAbsent(message.ID, message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static class Responses extends Thread{

        @Override
        public void run() {

            // 5i) Starts the thread that awaits for download requests on the TCP socket
            new DownloadServer().start();

            // 5b-g) Receives incoming UTP messages from the server (OK messages and Alive messages)
            while(peerSocket != null){
                try {
                    byte[] text = new byte[1024];
                    DatagramPacket readPacket = new DatagramPacket(text, text.length);
                    peerSocket.receive(readPacket);

                    String json = new String(readPacket.getData(),
                                readPacket.getOffset(),
                                readPacket.getLength());

                    Message received = Message.fromJson(json);

                    // 5e) If the received message is an Alive message coming from the server
                    // sends an Alive_OK response to it
                    if(received.type.equals(Message.Alive))
                        sendMessage(Message.aliveOKMessage(received.ID));
                    // 5g) If the ID was already removed it corresponds to a duplicate response and
                    // will not be considered
                    else if(needResend.get(received.ID) != null){
                        needResend.remove(received.ID);
                        ProcessResponse(received);
                    }
                    else
                    {
                        // Possible extra treatments of duplicated responses
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void ProcessResponse(Message message) {
        if(message.type.equals(Message.Join_OK)){
            System.out.print("Sou peer " + peerName + " com arquivos");
            for (String fn : fileNames)
                System.out.print(" " + fn);
            System.out.println();
        }
        else if(message.type.equals(Message.Leave_OK)){
            peerSocket.close();
            peerSocket = null;
            peerName = null;
            peerPort = 0;
            responsesThread = null;
        }
        else if(message.type.equals(Message.Search_OK)){
            receivedClients = message.clients;
            System.out.print("peers com arquivo solicitado:");
            for (String c : receivedClients)
                System.out.print(" " + c);
            System.out.println();
        }
        else if(message.type.equals(Message.Update_OK)){
            // Possible treatment for update_OK responses
        }


    }


    // 5g) Resend the message if its OK response was not received after each interval cycle
    private static class Resend extends Thread{
        private String ID;
        private Resend(String ID) {
            this.ID = ID;
        }
        @Override
        public void run(){
            while(peerSocket != null){
                try {
                    TimeUnit.SECONDS.sleep(5);

                    // Possible alternative for maintaining a single Resend thread
                    // for (Message message : needResend.values())
                    //     sendMessage(message);

                    Message message;
                    if((message = needResend.get(ID)) == null)
                        return;
                    sendMessage(message);

                } catch (InterruptedException e) {
                    // If the thread was awaken before the time was elapsed, only restarts
                    // the count on the next cycle
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private static class DownloadServer extends Thread{
        @Override
        public void run() {
            try {
                ServerSocket ss = new ServerSocket(peerSocket.getLocalPort());
                while(peerSocket != null){
                    Socket s = ss.accept();
                    new DownloadResponse(s).start();
                }
            } catch (Exception e) {
            e.printStackTrace();
            }
        }
    }

    // 5i) Receives the Download request from another peer and chooses if it will provide
    // the file.
    private static class DownloadResponse extends Thread{
        private Socket s;

        private DownloadResponse(Socket s){
            this.s = s;
        }

        @Override
        public void run() {
            try {
                InputStreamReader is = new InputStreamReader(s.getInputStream());
                BufferedReader reader = new BufferedReader(is);

                String file = reader.readLine();

                DataOutputStream writer = new DataOutputStream(s.getOutputStream());

                // 5i) Chooses randomly if the download will be accepted and sends the appropriate
                // responses to inform the requesting peer
                if(random.nextBoolean())
                    writer.writeBytes(Message.Download_OK + "\n");
                else{
                    writer.writeBytes(Message.Download_Negado + "\n");
                    return;
                }

                String fileLocation = new File(folder, file).getPath();

                FileInputStream fileReader = new FileInputStream(fileLocation);
                byte[] buffer = new byte[1024];

                // Sends the file on sizes of the maximum between the buffer and the remaining file
                int size;
                while ((size = fileReader.read(buffer)) > 0){
                    writer.write(buffer, 0, size);
                }

                fileReader.close();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
