package p2p_file_transfer;

import java.util.ArrayList;
import java.util.UUID;

import com.google.gson.Gson;

public class Message {

    public String type;
    public String file;
    public ArrayList<String> fileNames;
    public ArrayList<String> clients;
    public final String ID;

    public static final String Join = "JOIN";
    public static final String Leave = "LEAVE";
    public static final String Update = "UPDATE";
    public static final String Search = "SEARCH";
    public static final String Alive = "ALIVE";

    public static final String Join_OK = "JOIN_OK";
    public static final String Leave_OK = "LEAVE_OK";
    public static final String Update_OK = "UPDATE_OK";
    public static final String Search_OK = "SEARCH_OK";
    public static final String Alive_OK = "ALIVE_OK";
    public static final String Download_OK = "DOWNLOAD_OK";
    public static final String Download_Negado = "DOWNLOAD_NEGADO";

    private Message(
        String type,
        String file,
        ArrayList<String> fileNames,
        ArrayList<String> clients,
        String ID
        ){
            this.file = file;
            this.type = type;
            this.fileNames = fileNames;
            this.clients = clients;
            if(ID == null)
                this.ID = UUID.randomUUID().toString();
            else
                this.ID = ID;
    }


    public static Message joinMessage(ArrayList<String> fileNames){
        return new Message(Join, null, fileNames, null, null);
    }

    public static Message leaveMessage(){
        return new Message(Leave, null, null, null, null);
    }

    public static Message updateMessage(String file){
        return new Message(Update, file, null, null, null);
    }

    public static Message searchMessage(String file){
        return new Message(Search, file, null, null, null);
    }

    public static Message aliveMessage(){
        return new Message(Alive, null, null, null, null);
    }

    public static Message joinOKMessage(String ID){
        return new Message(Join_OK, null, null, null, ID);
    }

    public static Message leaveOKMessage(String ID){
        return new Message(Leave_OK, null, null, null, ID);
    }

    public static Message updateOKMessage(String ID){
        return new Message(Update_OK, null, null, null, ID);
    }

    public static Message searchOKMessage(String ID, ArrayList<String> clients){
        return new Message(Search_OK, null, null, clients, ID);
    }

    public static Message aliveOKMessage(String ID){
        return new Message(Alive_OK, null, null, null, ID);
    }

    public String toJson() {
        Gson gson = new Gson();
        String json = gson.toJson(this);
        return json;
    }

    public static Message fromJson(String json) {
        Gson gson = new Gson();
        Message message = gson.fromJson(json, Message.class);
        return message;
    }
}
