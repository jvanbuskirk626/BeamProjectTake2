package edu.temple.beamprojecttake2;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;

public class KeyService extends Service {

    KeyPairGenerator kpg;
    KeyPair kp;
    PublicKey publicKey;
    PrivateKey privateKey;
    private final IBinder kBinder=new LocalBinder();
    Map <String, String> partnerKeys;
    boolean keysMade=false;

    public class LocalBinder extends Binder {
        KeyService getService() {
            // Return this instance of KeyService so clients can call public methods
            return KeyService.this;
        }
    }

    public void genKeyPair(){

        try{
            if(!keysMade){
                kpg=KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                kp=kpg.genKeyPair();
                publicKey=(RSAPublicKey) kp.getPublic();
                privateKey=(RSAPrivateKey) kp.getPrivate();
                keysMade=true;
            }
            else{
                kp=new KeyPair(publicKey,privateKey);
            }
        }catch (Exception e){
            //e.printStackTrace();
        }
    }

    public String getPublicKey(){

        if(partnerKeys==null)
            return "no key";
        else{
            byte[] pubKeyBytes=publicKey.getEncoded();
            String publicKeyStr = Base64.encodeToString(pubKeyBytes,Base64.DEFAULT);
            String pKey = "-----BEGIN PUBLIC KEY-----\n"+publicKeyStr+"-----END PUBLIC KEY-----";
            return pKey;
        }

        //return "no key";
    }

    public void storePartnerKey(String partner, String partnerKey){

        String pKey=partnerKey.replace("-----BEGIN PUBLIC KEY-----\n", "");
        pKey = pKey.replace("-----END PUBLIC KEY-----", "");
        partnerKeys.put(partner, pKey);

    }

    public RSAPublicKey getPartnerPublicKey(String partnerName) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String publicKey = partnerKeys.get(partnerName);
        byte[] publicBytes = Base64.decode(publicKey, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    public RSAPrivateKey getPrivateKey(){
        return (RSAPrivateKey) privateKey;
    }


    public KeyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        partnerKeys = new HashMap<String, String>();
        return kBinder;
    }
}
