package edu.temple.beamprojecttake2;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONException;
import org.json.JSONObject;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;import java.util.ArrayList;
import javax.crypto.Cipher;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, NfcAdapter.CreateNdefMessageCallback{

    //app Utils
    NfcAdapter nfcAdapter;
    private PendingIntent pendingIntent;
    ArrayList<String> partners;
    ArrayAdapter<String> partnerAdatper;
    KeyService keyService;
    boolean kBound=false;
    boolean textMode;
    RSAPublicKey partnerPublicKey;
    RSAPrivateKey myPrivateKey;

    //UI stuff
    Button setUser, sendKey, sendMsg;
    EditText userNameBox, messageBox;
    TextView chatBox;
    Spinner partnerList;
    String userName, partnerName, msgToSend, msgReceived;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //all the UI stuff
        setUser=findViewById(R.id.setUser);
        sendKey=findViewById(R.id.sendKey);
        sendMsg=findViewById(R.id.sendMsg);
        userNameBox=findViewById(R.id.userBox);
        messageBox=findViewById(R.id.messageBox);
        chatBox=findViewById(R.id.chatBox);
        partnerList=findViewById(R.id.partnerList);
        partners=new ArrayList<String>();
        partnerAdatper=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item, partners);
        partnerList.setAdapter(partnerAdatper);
        partnerList.setOnItemSelectedListener(this);

        Intent intent = new Intent(this, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcAdapter.setNdefPushMessageCallback(this, this);

        //button listeners
        setUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                userName=userNameBox.getText().toString();
                keyService.genKeyPair();
            }
        });

        sendKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textMode=false;
            }
        });

        sendMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textMode=true;
                sendMessage();

            }
        });


    }


    private String sendMessage() {
        try {
            partnerPublicKey = keyService.getPartnerPublicKey(partnerName);
            msgToSend = messageBox.getText().toString();
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, partnerPublicKey);
            byte[] encryptedBytes = cipher.doFinal(msgToSend.getBytes());
            String encryptMsg=Base64.encodeToString(encryptedBytes, Base64.DEFAULT);

            return "{\"to\":\"" + partnerName + "\",\"from\":\""+ userName + "\",\"message\""+
                    ":\""+ encryptMsg +"\"}";

        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private String decryptMessage(String message){

        myPrivateKey=keyService.getPrivateKey();
        byte[] encrypted = Base64.decode(message, Base64.DEFAULT);

        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, myPrivateKey);
            return new String(cipher.doFinal(encrypted));

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        partnerName = parent.getItemAtPosition(position).toString();
    }
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String payload;
        if(textMode) {
            payload = sendMessage();
        }
        else {
            payload = sendKey();
        }

        NdefRecord record = NdefRecord.createTextRecord(null, payload);
        NdefMessage msg = new NdefMessage(new NdefRecord[]{record});

        return msg;
    }

    private String sendKey(){
        String pubKey = keyService.getPublicKey();
        return "{\"user\":\""+ userName +"\",\"key\":\""+ pubKey +"\"}";
    }

    void processIntent(Intent intent) {
        String payload = new String(
                ((NdefMessage) intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)[0]).getRecords()[0].getPayload());

        String jsonString = payload.substring(3);
        try {
            JSONObject json = new JSONObject(jsonString);
            if(json.has("message")){
                receiveMessage(json);
            }
            else if (json.has("key")){
                newPartner(json);
            }
        } catch (JSONException e) {}

    }

    private void newPartner(JSONObject json) {
        String partner = "Flying Spaghetti Monster";

        try {
            String owner = json.getString("user");
            String pemKey = json.getString("key");
            partner = owner;

            if(kBound)
                keyService.storePartnerKey(owner, pemKey);
        } catch (Exception e) {

        }

        partners.add(partner);
        partnerAdatper.notifyDataSetChanged();
    }


    private void receiveMessage(JSONObject json) {
        try {
            String partner = json.getString("from");
            String encryptedMessage = json.getString("message");

            String decryptedMessage = decryptMessage(encryptedMessage);
            chatBox.setText("From "+partner+": "+decryptedMessage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, KeyService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mConnection);
        kBound= false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            KeyService.LocalBinder binder = (KeyService.LocalBinder) service;
            keyService = binder.getService();
            kBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            kBound = false;
        }
    };
}
