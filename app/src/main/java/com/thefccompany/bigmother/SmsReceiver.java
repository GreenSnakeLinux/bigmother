package com.thefccompany.bigmother;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
		Bundle bundle = intent.getExtras();
		if (bundle != null && bundle.containsKey("pdus")) { // pdus is key for SMS in bundle
			Object[] pdus = (Object[]) bundle.get("pdus");
			SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdus[0]);
			String senderNumber = sms.getOriginatingAddress();
			String message = sms.getMessageBody();
			MainActivity.loadAppPref(context);
			if( !isBigMotherPhone(senderNumber, message)) {
				abortBroadcast();
			} else
			{
				MainActivity.getLocationAndSendSMS(context);
			}
		}    
    }

    boolean isBigMotherPhone(String senderNumber, String message) {
    	return MainActivity.getPhoneNumber().equals(senderNumber) && (MainActivity.getMessageKeyWord().isEmpty() || message.toLowerCase().contains(MainActivity.getMessageKeyWord().toLowerCase()));
	}
}