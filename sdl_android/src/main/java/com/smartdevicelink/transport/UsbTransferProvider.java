package com.smartdevicelink.transport;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.smartdevicelink.util.AndroidTools;

import java.lang.ref.WeakReference;

public class UsbTransferProvider {
    private static final String TAG = "UsbTransferProvider";

    private Context context = null;
    private boolean isBound = false;
    Messenger routerServiceMessenger = null;
    private ComponentName routerService = null;
    private int flags = 0;

    final Messenger clientMessenger;

    ParcelFileDescriptor usbPfd;

    private ServiceConnection routerConnection= new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Bound to service " + className.toString());
            routerServiceMessenger = new Messenger(service);
            isBound = true;
            //So we just established our connection
            //Register with router service
            Message msg = Message.obtain();
            msg.what = 5555; //TransportConstants.USB_CONNECTED_WITH_DEVICE;
            msg.arg1 = flags;
            msg.replyTo = clientMessenger;
            msg.obj = usbPfd;
            try {
                routerServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "UN-Bound from service " + className.getClassName());
            routerServiceMessenger = null;
            isBound = false;
        }
    };

    public UsbTransferProvider(Context context, ComponentName service, UsbAccessory usbAccessory){
        if(context == null || service == null || usbAccessory == null){
            throw new IllegalStateException("Supplied params are not correct. Context == null? "+ (context==null) + " ComponentName == null? " + (service == null) + " Usb Accessory == null? " + usbAccessory);
        }
        this.context = context;
        this.routerService = service;
        this.clientMessenger = new Messenger(new ClientHandler(this));
        usbPfd = getFileDescriptor(usbAccessory);
        if(usbPfd != null){
            checkIsConnected();
        }

    }

    @SuppressLint("NewApi")
    private ParcelFileDescriptor getFileDescriptor(UsbAccessory accessory){
         UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
         if(manager != null){
             return manager.openAccessory(accessory);
         }
         return  null;
    }

    public void setFlags(int flags){
        this.flags = flags;
    }

    public void checkIsConnected(){
        if(!AndroidTools.isServiceExported(context,routerService) || !bindToService()){
            //We are unable to bind to service
            Log.e(TAG, "Unable to bind to servicec");
            unBindFromService();
        }
    }

    public void cancel(){
        if(isBound){
            unBindFromService();
        }
    }

    private boolean bindToService(){
        if(isBound){
            return true;
        }
        if(clientMessenger == null){
            return false;
        }
        Intent bindingIntent = new Intent();
        bindingIntent.setClassName(this.routerService.getPackageName(), this.routerService.getClassName());//This sets an explicit intent
        //Quickly make sure it's just up and running
        context.startService(bindingIntent);
        bindingIntent.setAction( "TransportConstants.BIND_REQUEST_TYPE_USB");
        return context.bindService(bindingIntent, routerConnection, Context.BIND_AUTO_CREATE);
    }

    private void unBindFromService(){
        try{
            if(context!=null && routerConnection!=null){
                context.unbindService(routerConnection);
            }else{
                Log.w(TAG, "Unable to unbind from router service, context was null");
            }

        }catch(IllegalArgumentException e){
            //This is ok
        }
    }

    private void finish(){
        unBindFromService();
        routerServiceMessenger =null;
    }

    static class ClientHandler extends Handler {
        final WeakReference<UsbTransferProvider> provider;

        public ClientHandler(UsbTransferProvider provider){
            super(Looper.getMainLooper());
            this.provider = new WeakReference<UsbTransferProvider>(provider);
        }

        @Override
        public void handleMessage(Message msg) {
            if(provider.get()==null){
                return;
            }
            switch (msg.what) {
                case 5556: //TransportConstants.USB_ACC_RECEIVED:
                    Log.d(TAG, "Successful USB transfer");
                    provider.get().finish();
                    break;
                default:
                    break;
            }
        }
    };


}
