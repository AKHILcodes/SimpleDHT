package edu.buffalo.cse.cse486586.simpledht;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    DatabaseHelper mOpenHelper;
    SQLiteDatabase db;
    static final int SERVER_PORT = 10000;
    static final String TAG = SimpleDhtActivity.class.getSimpleName();
    String hashedKey;
    String myPort;
    String hashedMyPort;
    String pred;
    String succ;
    String hashedPred;
    String hashedSucc;
    List listOfActivePorts;
    HashMap<String,String> map;
    boolean Join = false;
    boolean queryResultReceived = false;
    MatrixCursor matrixCursor = new MatrixCursor(new String[]{"key","value"});
    public static Cursor ResultAnswerCursor;
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht");
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String[] delKeys = {selection};
        db = mOpenHelper.getReadableDatabase();
        if(selection.equals("@")){
            db.delete(mOpenHelper.TABLE_NAME,null,null);
        } else if(selection.equals("*")){
            db.delete(mOpenHelper.TABLE_NAME,null,null);
            String requesterPort = (Integer.parseInt(myPort)*2)+""; //requesterPort is String and will have values like 11108, 11112
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "QueryDelete",requesterPort,"DeleteAll");
        } else {
            String[] myprojection = {mOpenHelper.COL_1, mOpenHelper.COL_2};
            String myselection = mOpenHelper.COL_1 + " = ?";
            String[] mySelectionArgs = {selection};
            Cursor answerCursor = db.query(mOpenHelper.TABLE_NAME, myprojection, myselection, mySelectionArgs, null, null, null);

            if(answerCursor.getCount() == 0){
                String requesterPort = (Integer.parseInt(myPort)*2)+"";
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "QueryDelete",requesterPort,"DeleteSingle",selection,(Integer.parseInt(succ)*2)+"");
            } else {
                Log.e(TAG,"key to be deleted is "+selection + " and found in " + myPort);
                db.delete(mOpenHelper.TABLE_NAME, myselection, delKeys);
                Log.e(TAG,"someone got deleted");
            }
        }
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String key=values.getAsString("key");
        String msg = values.getAsString("value");
        try {
            hashedKey = genHash(key);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG,"Gen Hash Key");
        }
        Log.e(TAG,"hashed key for " + key + " is "+hashedKey + "  hashed myPort is " + hashedMyPort + "  hashed Pred is " + hashedPred + " original key is "+key + " original port is " + myPort + " original pred is " + pred);

        if((hashedKey.compareTo(hashedMyPort) <= 0 && hashedKey.compareTo(hashedPred) > 0) || (listOfActivePorts.size() < 2 )){
            Log.d(TAG,"inserting in DB: number of nodes is "+ listOfActivePorts.size()+""+"; Key is "+key + " Port is "+myPort);
            db = mOpenHelper.getWritableDatabase();
            db.insert(mOpenHelper.TABLE_NAME,null,values);
        }
        else if((hashedMyPort.compareTo(hashedPred) < 0) && ((hashedKey.compareTo(hashedPred)>0) || (hashedKey.compareTo(hashedMyPort)<=0))){
            Log.d(TAG,"inserting in DB: number of nodes is "+ listOfActivePorts.size()+""+"; Key is "+key + " Port is "+myPort);
            db = mOpenHelper.getWritableDatabase();
            db.insert(mOpenHelper.TABLE_NAME,null,values);
        }
        else{
            StringBuilder sbInsert = new StringBuilder("insert;");
            sbInsert.append(key + ";");
            sbInsert.append(msg);
            String receiverPort = (Integer.parseInt(succ) * 2) + "";
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, sbInsert.toString(), receiverPort);
        }
        return null;
    }


    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        mOpenHelper = new DatabaseHelper(getContext());
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort= String.valueOf((Integer.parseInt(portStr) )); //myPort is String & will have values like 5554, 5556
        try {
            hashedMyPort = genHash(myPort);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG,"Gen Hash My Port like 5556");
        }
        listOfActivePorts = new ArrayList<String>();
        map = new HashMap<String,String>();
        pred = myPort;
        hashedPred = hashedMyPort;
        succ = myPort;
        hashedSucc = hashedMyPort;
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }

        //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, hashedMsg, myPort);
        if(myPort.equals("5554")) {
           listOfActivePorts.add(hashedMyPort);
            map.put(hashedMyPort,myPort);
        } else{
            StringBuilder sbJoin = new StringBuilder("join;");
            sbJoin.append((Integer.parseInt(myPort) * 2) + "");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, sbJoin.toString(), "11108");
        }
        //sendMsg(sbJoin.toString(),"5554");

        return false;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try {
                String str = "";
                while (true) {
                    Socket clSocket = serverSocket.accept();
                    try {
                        DataInputStream br = new DataInputStream(clSocket.getInputStream());
                        str = br.readUTF();
                        String[] tokens = str.split(";");
                        DataOutputStream out = new DataOutputStream(clSocket.getOutputStream());

                        if(tokens[0].equals("insert")){
                            String key=tokens[1];
                            String msg = tokens[2];
                            ContentValues contentValues = new ContentValues();
                            contentValues.put("key", key);
                            contentValues.put("value", msg);
                            insert(mUri,contentValues);

                        } else if(tokens[0].equals("join") && myPort.equals("5554")){
                            //tokens[1] will have values like 11108, 11112 in form of String
                            String newPort = (Integer.parseInt(tokens[1]) / 2) + ""; //String newPort will have values like 5554, 5556
                            Log.e(TAG,"Join request from "+ tokens[1]);
                            Log.e(TAG,"Manipulated join port number is "+ newPort);
                            try {
                                String hashedNewPort = genHash(newPort);
                                listOfActivePorts.add(hashedNewPort);
                                Collections.sort(listOfActivePorts);
                                map.put(hashedNewPort,newPort); //map will have values like (hashGen(5556), "5556")

                                StringBuilder sbPredSuccChange = new StringBuilder("Pred Succ Change;");
                                for(int i=0; i < listOfActivePorts.size(); i++){
                                    sbPredSuccChange.append(map.get(listOfActivePorts.get(i)));
                                    sbPredSuccChange.append(";");
                                }
                                out.writeUTF(sbPredSuccChange.toString());

                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }

                        } else if(tokens[0].equals("Pred Succ Change")){
                            Log.e(TAG,"entering pred succ change");
                            int size = tokens.length-1;

                            for(int i = 1; i <= size; i++){
                                if(!listOfActivePorts.contains(genHash(tokens[i]))){
                                    listOfActivePorts.add(genHash(tokens[i]));
                                    Collections.sort(listOfActivePorts);
                                }
                                if(myPort.equals(tokens[1])){
                                    pred = tokens[size];
                                    succ = tokens[2];

                                } else if(myPort.equals(tokens[size])){
                                    pred = tokens[size-1];
                                    succ = tokens[1];

                                } else {
                                    int pos;
                                    if(myPort.equals(tokens[i])){
                                        pos = i;
                                        pred = tokens[pos-1];
                                        succ = tokens[pos+1];
                                    }
                                }
                            }
                            try {
                                hashedPred = genHash(pred);
                                hashedSucc = genHash(succ);
                            } catch (NoSuchAlgorithmException e) {
                                e.printStackTrace();
                            }
                            Log.d(TAG,"pred of "+ myPort + " is "+pred);
                            Log.d(TAG,"succ of "+ myPort + " is "+succ);
                            Log.d(TAG,"List size for "+myPort+" is "+listOfActivePorts.size());

                        } else if(str.equals("QueryAll")){
                            String results = "";
                            Cursor queryCursor = query(mUri,null,"@",null,null,null);
                            queryCursor.moveToFirst();
                            if(queryCursor.moveToFirst()) {
                                do {
                                    results += queryCursor.getString(0);
                                    Log.e(TAG,"query all key is " + results);
                                    results += ";";
                                    results += queryCursor.getString(1);
                                    results += "/";
                                } while (queryCursor.moveToNext());
                            }
                           // queryCursor.close();
                            if(!results.equals(""))
                                Log.e(TAG,"query all result string is " + results);
                                out.writeUTF(results);

                        } else if(tokens[0].equals("QuerySingle")){
                            String toSearch = tokens[1];
                            Log.e(TAG,"key to be queried is "+ toSearch + " for port no. " + myPort);
                            String results="";
                            Cursor queryCursor = query(mUri,null,toSearch,null,null,null);
                            queryCursor.moveToFirst();
                            Log.e(TAG,"Cursor count for " + myPort + " is " + queryCursor.getCount());
                            Log.e(TAG,"queryCursor.getString(0) is " + queryCursor.getString(0));
                            Log.e(TAG,"queryCursor.getString(1) is " + queryCursor.getString(1));

                            while(!queryCursor.isAfterLast()){
                                results = results + queryCursor.getString(0) + ";" + queryCursor.getString(1) + ";";
                                queryCursor.moveToNext();
                            }
                            //queryCursor.close();
                            if(!results.equals("")) {
                                out.writeUTF(results);
                                Log.e(TAG, "Server side result for single query " + results);
                            }
                        }
                        else if(str.equals("DeleteAll")){
                            delete(mUri,"@",null);

                        } else if(tokens[0].equals("DeleteSingle")){
                            delete(mUri,tokens[1],null);
                        }
                        publishProgress(str);

                    }catch (NullPointerException e){
                        Log.e(TAG,"null pointer data input stream");
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                }
            }catch (IOException e){
                Log.e(TAG,"IOException in server");
            }
            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            return null;
        }

        //to communicate with UI part we need to implement onProgressUpdate
        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String[] array = new String[5];
            array[0] = REMOTE_PORT0;
            array[1] = REMOTE_PORT1;
            array[2] = REMOTE_PORT2;
            array[3] = REMOTE_PORT3;
            array[4] = REMOTE_PORT4;
            String joinDetails = "";
            String queryDetails = "";

             if(!msgs[0].equals("QueryDelete")){
                 String[] insertToken = msgs[0].split(";");
                 if(insertToken[0].equals("insert")){
                     try {

                         Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(msgs[1]));

                         String msgToSend = msgs[0];
                         DataOutputStream pw = new DataOutputStream(socket.getOutputStream());

                         pw.writeUTF(msgToSend);
                         pw.flush();
                     }catch (UnknownHostException e) {
                         Log.e(TAG, "ClientTask UnknownHostException");
                     } catch (IOException e) {
                         Log.e(TAG, "ClientTask socket IOException");
                     }
                 } else if(insertToken[0].equals("join")) {
                     try {

                         Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(msgs[1]));

                         String msgToSend = msgs[0];
                         DataOutputStream pw = new DataOutputStream(socket.getOutputStream());

                         pw.writeUTF(msgToSend);
                         pw.flush();
                         socket.setSoTimeout(1000);
                         try {
                             InputStream inFromServer = socket.getInputStream();
                             Log.e(TAG, "got response from server");
                             DataInputStream in = new DataInputStream(inFromServer);
                             joinDetails = in.readUTF();
                             Join = true;
                         } catch (SocketTimeoutException ste) {
                             Log.e(TAG, "Timeout exception while waiting for join");
                         }
                     } catch (UnknownHostException e) {
                         Log.e(TAG, "ClientTask UnknownHostException");
                     } catch (IOException e) {
                         Log.e(TAG, "ClientTask socket IOException");
                     }
                 }
             } else{
                 if(msgs[2].equals("QueryAll")) {
                     for (int i = 0; i < 5; i++) {
                         if (!array[i].equals(msgs[1])) {
                             try {
                                 Log.d(TAG, "entered query delete loop client");

                                 Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(array[i]));
                                 String msgToSend = msgs[2];
                                 DataOutputStream pw = new DataOutputStream(socket.getOutputStream());
                                 pw.writeUTF(msgToSend);
                                 pw.flush();
                                 socket.setSoTimeout(1000);
                                 try {
                                     InputStream inFromServer = socket.getInputStream();
                                     Log.e(TAG, "got query delete response from server");
                                     DataInputStream in = new DataInputStream(inFromServer);
                                     queryDetails = in.readUTF();
                                     Log.e(TAG,"qerydet "+queryDetails);
                                     MatrixCursor matrixCursor = new MatrixCursor(new String[] {"key","value"});
                                     if(!queryDetails.isEmpty())
                                     {
                                         String[] queryTokens = queryDetails.split("/");
                                         for (int j = 0; j < queryTokens.length; j++) {
                                             String[] insideTokens = queryTokens[j].split(";");
                                             Log.e(TAG, "length of insideTokens having keys and value " + insideTokens.length);
                                             matrixCursor.addRow(new String[]{insideTokens[0], insideTokens[1]});
                                         }

                                         Cursor[] temp = {ResultAnswerCursor, matrixCursor};
                                         ResultAnswerCursor = new MergeCursor(temp);
                                         Log.e(TAG, "number of rows is " + ResultAnswerCursor.getCount());
                                     }
                                 } catch (SocketTimeoutException ste) {
                                     Log.e(TAG, "Timeout exception while waiting for join");
                                 }

                             } catch (UnknownHostException e) {
                                 Log.e(TAG, "ClientTask UnknownHostException");
                             } catch (IOException e) {
                                 Log.e(TAG, "ClientTask socket IOException");
                             }
                         }
                     }
                     //Log.e(TAG,"number of rows is " + ResultAnswerCursor.getCount());
                     queryResultReceived = true;
                 } else if(msgs[2].equals("QuerySingle")){
                     try{
                                 Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(msgs[4]));
                                 String msgToSend = msgs[2] + ";" + msgs[3];
                                 DataOutputStream pw = new DataOutputStream(socket.getOutputStream());
                                 pw.writeUTF(msgToSend);
                                 pw.flush();
                                 socket.setSoTimeout(1000);
                                 try {
                                     InputStream inFromServer = socket.getInputStream();
                                     Log.e(TAG, "got single query response from server");
                                     DataInputStream in = new DataInputStream(inFromServer);
                                     queryDetails = in.readUTF();
                                     Log.e(TAG,"Return");
                                     String[] insideTokens = queryDetails.split(";");
                                     if(insideTokens.length == 2)
                                      {
                                          Log.e(TAG,"Check1");
                                          MatrixCursor matrixCursor = new MatrixCursor(new String[] {"key","value"});
                                          matrixCursor.addRow(insideTokens);
                                          ResultAnswerCursor =  matrixCursor;
                                          Log.e(TAG,"Check2");
                                          while (ResultAnswerCursor.moveToNext()){
                                              Log.e(TAG,"ResultAnswerCursor contents in client side for " + myPort + " are " + ResultAnswerCursor.getString(0) + " ; " + ResultAnswerCursor.getString(1));
                                          }
                                          Log.e(TAG,"Check3");
                                          queryResultReceived = true;
                                         Log.e(TAG,"Client Side result for query for key: " + insideTokens[0] + " value is "+ insideTokens[1]);
                                     }

                                 } catch (SocketTimeoutException ste) {
                                     Log.e(TAG, "Timeout exception while waiting for query result for " + msgs[3] + " from " + myPort);
                                 }

                         } catch (UnknownHostException e) {
                             Log.e(TAG, "ClientTask UnknownHostException");
                         } catch (IOException e) {
                             Log.e(TAG, "ClientTask socket IOException");
                         }
                     } else if(msgs[2].equals("DeleteAll")) {
                     for (int i = 0; i < 5; i++) {
                         if (!array[i].equals(msgs[1])) {
                             try {
                                 Log.d(TAG, "entered delete loop client");

                                 Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(array[i]));
                                 String msgToSend = msgs[2];
                                 DataOutputStream pw = new DataOutputStream(socket.getOutputStream());
                                 pw.writeUTF(msgToSend);
                                 pw.flush();

                             } catch (UnknownHostException e) {
                                 Log.e(TAG, "ClientTask UnknownHostException");
                             } catch (IOException e) {
                                 Log.e(TAG, "ClientTask socket IOException");
                             }
                         }
                         queryResultReceived = true;
                     }
                 } else if(msgs[2].equals("DeleteSingle")){
                     try{
                         Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(msgs[4]));
                         String msgToSend = msgs[2] + ";" + msgs[3];
                         DataOutputStream pw = new DataOutputStream(socket.getOutputStream());
                         pw.writeUTF(msgToSend);
                         pw.flush();

                     } catch (UnknownHostException e) {
                         Log.e(TAG, "ClientTask UnknownHostException");
                     } catch (IOException e) {
                         Log.e(TAG, "ClientTask socket IOException");
                     }
                 }
             }

            for (int i = 0; i < 5; i++) {
                if(Join && !joinDetails.equals(""))
                try {
                    Log.d(TAG,"entered second for loop client");
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(array[i]));

                    String msgToSend = joinDetails;
                    DataOutputStream pw = new DataOutputStream(socket.getOutputStream());

                    pw.writeUTF(msgToSend);
                    pw.flush();

                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                }
            }

            return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
        db = mOpenHelper.getReadableDatabase();
        Cursor prCursor = null;
        if(selection.equals("@")){
            prCursor = db.rawQuery("select * from " + mOpenHelper.TABLE_NAME,null);
        }
        else if(selection.equals("*")){
            Cursor cursor = db.rawQuery("select * from " + mOpenHelper.TABLE_NAME,null);
            String requesterPort = (Integer.parseInt(myPort)*2)+""; //requesterPort is String and will have values like 11108, 11112
            if(listOfActivePorts.size() >= 2 ) {
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "QueryDelete", requesterPort, "QueryAll");
                while (!queryResultReceived) {
                }
                queryResultReceived = false;

                Cursor[] temp = {ResultAnswerCursor, cursor};
                prCursor = new MergeCursor(temp);
                Log.e(TAG, "final * cursor :" + cursor.getCount());
                if (ResultAnswerCursor != null)
                    Log.e(TAG, "final * cursor :" + ResultAnswerCursor.getCount());
                ResultAnswerCursor = null;
                Log.e(TAG, "final * cursor :" + prCursor.getCount());
            }
            else
                prCursor = cursor;
        } else {
            try {
                String messageHashedKey = genHash(selection);
                if((messageHashedKey.compareTo(hashedMyPort) <= 0 && messageHashedKey.compareTo(hashedPred) > 0) || (listOfActivePorts.size() < 2 )){
                    String[] myprojection = {mOpenHelper.COL_1, mOpenHelper.COL_2};
                    String myselection = mOpenHelper.COL_1 + " = ?";
                    String[] mySelectionArgs = {selection};
                    Cursor answerCursor = db.query(mOpenHelper.TABLE_NAME, myprojection, myselection, mySelectionArgs, null, null, null);
                    prCursor = answerCursor;
                } else if((hashedMyPort.compareTo(hashedPred) < 0) && ((messageHashedKey.compareTo(hashedPred)>0) || (messageHashedKey.compareTo(hashedMyPort)<=0))){
                    String[] myprojection = {mOpenHelper.COL_1, mOpenHelper.COL_2};
                    String myselection = mOpenHelper.COL_1 + " = ?";
                    String[] mySelectionArgs = {selection};
                    Cursor answerCursor = db.query(mOpenHelper.TABLE_NAME, myprojection, myselection, mySelectionArgs, null, null, null);
                    prCursor = answerCursor;
                } else {
                    String requesterPort = (Integer.parseInt(myPort) * 2) + "";

                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "QueryDelete", requesterPort, "QuerySingle", selection, (Integer.parseInt(succ) * 2) + "");

                    while (!queryResultReceived) {
                    }
                    prCursor = ResultAnswerCursor;
                    queryResultReceived = false;
                    
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        return prCursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}
