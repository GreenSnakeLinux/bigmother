package com.thefccompany.bigmother;

import static com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_ALL = 300;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 400;
    private static final int MY_PERMISSIONS_ACCESS_BACKGROUND_LOCATION_REQUEST = 500;
    private static final int RESULT_PICK_CONTACT = 500;
    private static final String PHONE_NUMBER_KEY = "PHONE_NUMBER_KEY";
    private static final String KEYWORD_KEY = "KEYWORD_KEY";

    private static final CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();

    private static String m_currentPhoneNumber;
    private static String m_currentKeyWord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String result = checkForSendSmsPermissions();
        result += checkForSmsReceivePermissions();
        result += checkForAccessFineLocationPermissions();
        result += checkForAccessCoarseLocationPermissions();

        String[] PERMISSIONS = new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION//,
                    //Manifest.permission.ACCESS_BACKGROUND_LOCATION // Cannot be asked at same time (Only for > Build.VERSION_CODES.Q)
            };

        if (!result.isEmpty()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, MY_PERMISSIONS_REQUEST_ALL);
        }

        loadAppPref();

        ImageButton vContact = findViewById(R.id.buttonContact);
        EditText vPhone = findViewById(R.id.editTextPhone);
        EditText vKeyword = findViewById(R.id.textViewKeyword);

        vPhone.setText(m_currentPhoneNumber);
        vKeyword.setText(m_currentKeyWord);

        vContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_CONTACTS}, MY_PERMISSIONS_REQUEST_READ_CONTACTS);
                } else {
                    Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                    startActivityForResult(contactPickerIntent, RESULT_PICK_CONTACT);
                }
            }
        });

        vPhone.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                m_currentPhoneNumber = s.toString();
                saveAppPref();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        vKeyword.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                m_currentKeyWord = s.toString();
                saveAppPref();
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void loadAppPref() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        m_currentPhoneNumber = prefs.getString(PHONE_NUMBER_KEY, "+33");
        m_currentKeyWord = prefs.getString(KEYWORD_KEY, "GPS");
    }

    private void saveAppPref() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(PHONE_NUMBER_KEY, m_currentPhoneNumber);
        prefsEditor.putString(KEYWORD_KEY, m_currentKeyWord);
        prefsEditor.apply();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // check whether the result is ok
        if (resultCode == RESULT_OK) {
            // Check for the request code, we might be using multiple startActivityForResult
            switch (requestCode) {
                case RESULT_PICK_CONTACT:
                    EditText vPhone = findViewById(R.id.editTextPhone);

                    Uri contactData = data.getData();
                    Cursor c = getContentResolver().query(contactData, null, null, null, null);
                    if (c.moveToFirst()) {
                        int phoneIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        String num = c.getString(phoneIndex);

                        new Thread(new Runnable() {
                            public void run() {
                                try {
                                    PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
                                    Phonenumber.PhoneNumber internationalNumber = null;
                                    internationalNumber = phoneUtil.parse(num, "FR");
                                    String iNum = phoneUtil.format(internationalNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);

                                    MainActivity.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            vPhone.setText(iNum);
                                            //Toast.makeText(this, "Number=" + num, Toast.LENGTH_LONG).show();
                                        }
                                    });
                                } catch (NumberParseException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).start();

                    }
                    c.close();
                    break;
            }
        } else {
            Log.e("ContactFragment", "Failed to pick contact");
        }
    }

    private static void sendSMS(Context context, String number, String message) {
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, context.getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
        } else {
            sendSMSGranted(context, number, message);
        }
    }

    private static void sendSMSGranted(Context context, String number, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(number, null, message, null, null);
        Toast.makeText(context, context.getString(R.string.send_sms), Toast.LENGTH_LONG).show();
    }

    String checkForSendSmsPermissions() {
        // Check if App already has permissions for send SMS
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            // App has permissions to send SMS messages
            return "";
        } else {
            // App don't have permissions to send SMS messages
            return Manifest.permission.SEND_SMS;
        }
    }

    String checkForSmsReceivePermissions() {
        // Check if App already has permissions for receiving SMS
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            // App has permissions to listen incoming SMS messages
            return "";
        } else {
            // App don't have permissions to listen incoming SMS messages
            return Manifest.permission.RECEIVE_SMS;
        }
    }

    String checkForAccessFineLocationPermissions() {
        // Check if App already has permissions for Access Fine Location
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // App has permissions to Access Fine Location
            return "";
        } else {
            // App don't have permissions to Access Fine Location
            return Manifest.permission.ACCESS_FINE_LOCATION;
        }
    }

    String checkForAccessCoarseLocationPermissions() {
        // Check if App already has permissions for Access Coarse Location
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // App has permissions to Access Coarse Location
            return "";
        } else {
            // App don't have permissions to Access Coarse Location
            return Manifest.permission.ACCESS_COARSE_LOCATION;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    String checkForAccessBackgroundLocationPermissions() {
        // Check if App already has permissions for Access Background Location
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // App has permissions to Access Background Location
            return "";
        } else {
            // App don't have permissions to Access Background Location
            return Manifest.permission.ACCESS_BACKGROUND_LOCATION;
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ALL: {
                boolean isPermissionForAllGranted = false;
                if (grantResults.length > 0 && permissions.length==grantResults.length) {
                    for (int i = 0; i < permissions.length; i++){
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED){
                            isPermissionForAllGranted = true;
                        } else {
                            isPermissionForAllGranted = false;
                        }
                    }
                }
                if (isPermissionForAllGranted) {
                    Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_LONG).show();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (!checkForAccessBackgroundLocationPermissions().isEmpty() ) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, MY_PERMISSIONS_ACCESS_BACKGROUND_LOCATION_REQUEST);
                        }
                    }
                } else {
                    Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
                }
                break;
            }
            case MY_PERMISSIONS_ACCESS_BACKGROUND_LOCATION_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent contactPickerIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                    startActivityForResult(contactPickerIntent, RESULT_PICK_CONTACT);
                } else {
                    Toast.makeText(this, getString(R.string.enter_phone_number_manually), Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    public static String getPhoneNumber() {
        return m_currentPhoneNumber.replace(" ", "");
    }

    public static String getMessageKeyWord() {
        return m_currentKeyWord;
    }

    public static void getLocationAndSendSMS(Context context) {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, context.getString(R.string.permission_not_granted), Toast.LENGTH_LONG).show();
            return;
        }
        Task<Location> currentLocationTask = fusedLocationClient.getCurrentLocation(
                PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.getToken()
        );

        currentLocationTask.addOnCompleteListener((new OnCompleteListener<Location>() {
            @Override
            public void onComplete(@NonNull Task<Location> task) {
                String result;
                if (task.isSuccessful() && task.getResult() != null) {
                    // Task completed successfully
                    Location location = task.getResult();
                    result = location.getLatitude() +
                            "," +
                            location.getLongitude();

                    sendSMS(context, getPhoneNumber(), "http://maps.google.com/maps?q=loc:" + result);
                } else {
                    // Task failed with an exception
                    Exception exception = task.getException();
                    result = "Exception thrown: " + exception;
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show();
                    sendSMS(context, getPhoneNumber(), context.getString(R.string.location_not_found));
                }
                Log.d("BIG_MOTHER", "getCurrentLocation() result: " + result);
            }
        }));
    }
}