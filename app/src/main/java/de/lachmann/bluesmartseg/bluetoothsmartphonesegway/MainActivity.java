package de.lachmann.bluesmartseg.bluetoothsmartphonesegway;

        import java.io.IOException;
        import java.io.InputStream;
        import java.io.OutputStream;
        import java.nio.ByteBuffer;
        import java.util.UUID;
        import android.app.Activity;
        import android.bluetooth.BluetoothAdapter;
        import android.bluetooth.BluetoothDevice;
        import android.bluetooth.BluetoothSocket;
        import android.graphics.Color;
        import android.hardware.Sensor;
        import android.hardware.SensorEvent;
        import android.hardware.SensorEventListener;
        import android.hardware.SensorManager;
        import android.os.Bundle;
        import android.util.Log;
        import android.view.View;
        import android.widget.Button;
        import android.widget.CompoundButton;
        import android.widget.EditText;
        import android.widget.SeekBar;
        import android.widget.TextView;
        import android.widget.Toast;
        import android.widget.ToggleButton;

public class MainActivity extends Activity {

    // UUID fuer Kommunikation mit Seriellen Modulen
    private UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String LOG_TAG = "FRAGDUINO";

    // Variablen
    private BluetoothAdapter adapter = null;
    private BluetoothSocket socket = null;
    private OutputStream stream_out = null;
    private InputStream stream_in = null;
    private boolean is_connected = false;
    private static String mac_adresse; // MAC Adresse des Bluetooth Adapters

    private static final byte SYNC_WORD = (byte) 0xFF;
    private ToggleButton buttonOnOff;
    private TextView samplest, pwmText, kpText, kdText, kpwmText, koText;
    private TextView phiz,pwmS,omegarad;
    private SeekBar kpSeekBar, kdSeekBar,kpwmSeekBar, koSeekBar, phisollSeekBar;

    public float xacc, yacc, zacc, xgyr, ygyr, zgyr;
    public double phi, phiacc, phimix, phisoll, kp, kd, kpwm,ko ;
    private double  omegaRad, schleifenzeit;
    private double phi_x, phi_y, phi_z;

    private int i, pwm, button=-1;
    private long zeitAktuell, sekunde, zeit = System.currentTimeMillis(), notaus;
    private SensorManager mgr;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeButton();
        initializeViews();
        initializeSeekbars();

        mgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        mgr.registerListener(listener, mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        mgr.registerListener(listener, mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_GAME);

        Log.d(LOG_TAG, "Bluetest: OnCreate");

        // Verbindung mit Bluetooth-Adapter herstellen
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Bitte Bluetooth aktivieren",
                    Toast.LENGTH_LONG).show();
            Log.d(LOG_TAG,
                    "onCreate: Bluetooth Fehler: Deaktiviert oder nicht vorhanden");
            finish();
            return;
        } else
            Log.d(LOG_TAG, "onCreate: Bluetooth-Adapter ist bereit");
    }

    public void verbinden(View v) {
        mac_adresse = ((EditText) findViewById(R.id.text_adresse)).getText()
                .toString();
        Log.d(LOG_TAG, "Verbinde mit " + mac_adresse);

        BluetoothDevice remote_device = adapter.getRemoteDevice(mac_adresse);

        // Socket erstellen
        try {
            socket = remote_device
                    .createInsecureRfcommSocketToServiceRecord(uuid);
            Log.d(LOG_TAG, "Socket erstellt");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Socket Erstellung fehlgeschlagen: " + e.toString());
        }

        adapter.cancelDiscovery();

        // Socket verbinden
        try {
            socket.connect();
            Log.d(LOG_TAG, "Socket verbunden");
            is_connected = true;
        } catch (IOException e) {
            is_connected = false;
            Log.e(LOG_TAG, "Socket kann nicht verbinden: " + e.toString());
        }

        // Socket beenden, falls nicht verbunden werden konnte
        if (!is_connected) {
            try {
                socket.close();
            } catch (Exception e) {
                Log.e(LOG_TAG,
                        "Socket kann nicht beendet werden: " + e.toString());
            }
        }

        // Outputstream erstellen:
        try {
            stream_out = socket.getOutputStream();
            Log.d(LOG_TAG, "OutputStream erstellt");
        } catch (IOException e) {
            Log.e(LOG_TAG, "OutputStream Fehler: " + e.toString());
            is_connected = false;
        }

        // Inputstream erstellen
        try {
            stream_in = socket.getInputStream();
            Log.d(LOG_TAG, "InputStream erstellt");
        } catch (IOException e) {
            Log.e(LOG_TAG, "InputStream Fehler: " + e.toString());
            is_connected = false;
        }

        if (is_connected) {
            Toast.makeText(this, "Verbunden mit " + mac_adresse,
                    Toast.LENGTH_LONG).show();
            ((Button) findViewById(R.id.bt_verbinden))
                    .setBackgroundColor(Color.GREEN);
        } else {
            Toast.makeText(this, "Verbindungsfehler mit " + mac_adresse,
                    Toast.LENGTH_LONG).show();
            ((Button) findViewById(R.id.bt_verbinden))
                    .setBackgroundColor(Color.RED);
        }
    }

    public void trennen(View v) {
        if (is_connected && stream_out != null) {
            is_connected = false;
            ((Button) findViewById(R.id.bt_trennen))
                    .setBackgroundColor(Color.RED);
            Log.d(LOG_TAG, "Trennen: Beende Verbindung");
            try {
                stream_out.flush();
                socket.close();
            } catch (IOException e) {
                Log.e(LOG_TAG,
                        "Fehler beim beenden des Streams und schliessen des Sockets: "
                                + e.toString());
            }
        } else
            Log.d(LOG_TAG, "Trennen: Keine Verbindung zum beenden");
    }

    private void sendArduinoInt(int rechts, int links) {

        byte[] bytes = ByteBuffer.allocate(4).putInt(rechts).array();
        byte[] bytes2 = ByteBuffer.allocate(4).putInt(links).array();
        byte[] message = new byte[5];

       message[0] = SYNC_WORD;
        message[1] = bytes[2];
        message[2] = bytes[3];
        message[3] = bytes2[2];
        message[4] = bytes2[3];

        if (is_connected) {
            Log.d(LOG_TAG, "Sende Nachricht: " + message);
            try {
                stream_out.write( message);
            } catch (IOException e) {
                Log.e(LOG_TAG,"Bluetest: Exception beim Senden: " + e.toString());
            }
        }
    }

/*	public void empfangen(View v) {
		byte[] buffer = new byte[1024]; // Puffer
		int laenge; // Anzahl empf. Bytes
		String msg = "";
		try {
			if (stream_in.available() > 0) {
				laenge = stream_in.read(buffer);
				Log.d(LOG_TAG,
						"Anzahl empfangender Bytes: " + String.valueOf(laenge));

				// Message zusammensetzen:
				for (int i = 0; i < laenge; i++)
					msg += (char) buffer[i];

				Log.d(LOG_TAG, "Message: " + msg);
				Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

			} else
				Toast.makeText(this, "Nichts empfangen", Toast.LENGTH_LONG)
						.show();
		} catch (Exception e) {
			Log.e(LOG_TAG, "Fehler beim Empfangen: " + e.toString());
		}
	}*/

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy. Trenne Verbindung, falls vorhanden");
        trennen(null);
    }

    private void initializeButton() {
        buttonOnOff = (ToggleButton) findViewById(R.id.arduinoled);
        buttonOnOff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (isChecked) {
                    button = 1;
                    kp = 658;
                    kpSeekBar.setProgress(350);
                    kd = 12;
                    kdSeekBar.setProgress(12);
                    kpwm = 130;
                    kpwmSeekBar.setProgress(130);
                    ko = 2700;
                    koSeekBar.setProgress(2480);
                    zeitAktuell = System.currentTimeMillis();
                    i = 0;

                } else {
                    button = -1;
//                    kp = 0;
//                    kpSeekBar.setProgress(0);
//                    kd = 0;
//                    kdSeekBar.setProgress(0);
//                    kpwm = 0;
//                    kpwmSeekBar.setProgress(0);
//                    ko = 0;
//                    koSeekBar.setProgress(0);
                    sendArduinoInt(0, 0);
                }
            }
        });
    }

    private void initializeSeekbars() {
        kpSeekBar=(SeekBar)findViewById(R.id.seekBar1);
        kdSeekBar=(SeekBar)findViewById(R.id.seekBar2);
        kpwmSeekBar=(SeekBar)findViewById(R.id.seekBar4);
        koSeekBar=(SeekBar)findViewById(R.id.seekBar0);
        phisollSeekBar=(SeekBar)findViewById(R.id.seekBar3);

        kpSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                                                 @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                                                         boolean fromUser) {
                                                     // change progress text label with current seekbar value
                                                     //kp = progress;
                                                     kp = progress;
                                                 }
                                                 @Override public void onStartTrackingTouch( SeekBar seekBar){
                                                 }
                                                 @Override public void onStopTrackingTouch(SeekBar seekBar) {
                                                     // TODO Auto-generated method stub
                                                     seekBar.setSecondaryProgress(seekBar.getProgress());
                                                     // textAction.setText("ended tracking touch");
                                                 }
                                             }
        );

        kdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                                                 @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                                                         boolean fromUser) {
                                                     // change progress text label with current seekbar value
                                                     kd = progress;
                                                 }
                                                 @Override public void onStartTrackingTouch( SeekBar seekBar){
                                                 }
                                                 @Override public void onStopTrackingTouch(SeekBar seekBar) {
                                                     // TODO Auto-generated method stub
                                                     seekBar.setSecondaryProgress(seekBar.getProgress());
                                                     // textAction.setText("ended tracking touch");
                                                 }
                                             }
        );
        kpwmSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                                                     @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                                                             boolean fromUser) {
                                                         // change progress text label with current seekbar value
                                                         kpwm = progress;
                                                     }
                                                     @Override public void onStartTrackingTouch( SeekBar seekBar){
                                                     }
                                                     @Override public void onStopTrackingTouch(SeekBar seekBar) {
                                                         // TODO Auto-generated method stub
                                                         seekBar.setSecondaryProgress(seekBar.getProgress());
                                                         // textAction.setText("ended tracking touch");
                                                     }
                                                 }
        );
        koSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                                                     @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                                                             boolean fromUser) {
                                                         // change progress text label with current seekbar value
                                                         ko = progress;
                                                     }
                                                     @Override public void onStartTrackingTouch( SeekBar seekBar){
                                                     }
                                                     @Override public void onStopTrackingTouch(SeekBar seekBar) {
                                                         // TODO Auto-generated method stub
                                                         seekBar.setSecondaryProgress(seekBar.getProgress());
                                                         // textAction.setText("ended tracking touch");
                                                     }
                                                 }
        );
        phisollSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                                                      @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                                                              boolean fromUser) {
                                                          // change progress text label with current seekbar value
                                                          phisoll = progress-3000;
                                                      }
                                                      @Override public void onStartTrackingTouch( SeekBar seekBar){
                                                      }
                                                      @Override public void onStopTrackingTouch(SeekBar seekBar) {
                                                          // TODO Auto-generated method stub
                                                          seekBar.setSecondaryProgress(seekBar.getProgress());
                                                          // textAction.setText("ended tracking touch");
                                                      }
                                                  }
        );
    }

    public void initializeViews() {
        samplest  = (TextView) findViewById(R.id.samplest);
        //pwmText  = (TextView) findViewById(R.id.pwmText);
        kpText  = (TextView) findViewById(R.id.kpText);
        kdText  = (TextView) findViewById(R.id.kdText);
        kpwmText  = (TextView) findViewById(R.id.kpwmText);
        koText  = (TextView) findViewById(R.id.koText);
        phiz  = (TextView) findViewById(R.id.phi_z);
        pwmS  = (TextView) findViewById(R.id.pwm);
        omegarad  = (TextView) findViewById(R.id.omegarad);

    }

    SensorEventListener listener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {

                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                    xacc = event.values[0];
                    yacc = event.values[1];
                    zacc = event.values[2];

                   // phi_x = Math.atan(xacc / Math.sqrt(yacc * yacc + zacc * zacc));
                   // phi_y = Math.atan(yacc / Math.sqrt(xacc * xacc + zacc * zacc));
                    phi_z = Math.atan(zacc / Math.sqrt(yacc * yacc + xacc * xacc));

                }
                if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                    xgyr = event.values[0];

                    i++;
                    sekunde = System.currentTimeMillis() - zeit;
                    if (sekunde > 1000) {
                        zeit = System.currentTimeMillis();
                       // samples = i;
                        i = 0;
                        samplest.setText(Double.toString(schleifenzeit));
                    }
                    schleifenzeit = System.currentTimeMillis() - zeitAktuell;
                    zeitAktuell = System.currentTimeMillis();
                    schleifenzeit = schleifenzeit / 1000;
                    if(schleifenzeit>1)schleifenzeit=0;
                    //Komplementaerfilter aus Beschleunigungssensor und Gyroskop
                    phimix = (0.98 * (phimix - xgyr * schleifenzeit) + 0.02 * phi_z);

                   // if(button == 1)
                        regelung();
                }
        }
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public void regelung(){
        //Regelungalgorithmus
        //Korrektur des Kippwinkels
        phi = phimix  - (phisoll - 1500) / 50000;
        phiz.setText(Double.toString(phi));
        //Integral aus Winkel des Aufbaus und Motormoment
        //ergibt die Winkelgeschwindigkeit der Raeder: Alpha = Km * Motormoment - Ka * phiAufbau
        //(Alpha entspricht der Winkelbeschleunigung der Raeder)
        //(Motormoment entspricht dem PWM-Signal)
        omegaRad = omegaRad + (10000 * phi + kpwm/10 * pwm   ) * schleifenzeit;
        //Regler
        if (omegaRad > 500)  omegaRad = 500;
        if (omegaRad < -500) omegaRad = -500;

        pwm = (int) (kp * phi + kd * -xgyr + ko/10000 * omegaRad );
        if (pwm > 255)  pwm = 255;
        if (pwm < -255) pwm = -255;
        // pwmText.setText(Double.toString(pwm));
        kpText.setText(Double.toString(kp));
        kdText.setText(Double.toString(kd));
        kpwmText.setText(Double.toString(kpwm));
        koText.setText(Double.toString(ko));
        pwmS.setText(Double.toString(pwm));
        omegarad.setText(Double.toString(omegaRad));

        //Notaus falls das Smartphone umkippt
        if(pwm == 255 || pwm == -255){
            notaus++;
            if(notaus > 5){
                button = -1;
                buttonOnOff.setChecked(false);
                sendArduinoInt(0, 0);
      //          sendArduinoInt(20  , 100;
            }
        }
        else{
            notaus = 0;
        }
        if(button == 1) {
              sendArduinoInt(pwm  , pwm);
        }
           else{
            omegaRad=0.0;
            pwm=0;
        }

    };


}