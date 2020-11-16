package com.example.Offline_Learning;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.view.Menu;
import android.view.MenuItem;

import com.example.offline_learning.R;
import com.google.android.gms.nearby.connection.Strategy;

public class MainActivity extends AppCompatActivity implements HelpFragment.OnFragmentInteractionListener {

    String TAG = "MainActivity";
    public static final String ServiceId = "offlinelearning";  //need a unique value to identify app.
    public static final int REQUEST_ACCESS_COURSE_LOCATION= 1;
    public static final int REQUEST_ACCESS_MANAGE_STORAGE= 1;

    FragmentManager fragmentManager;

    /**
     * The connection strategy we'll use for Nearby Connections. In this case, we've decided on
     * P2P_STAR, which is a combination of Bluetooth Classic and WiFi Hotspots.  this is 1 to many, so 1 advertise and many discovery.
     * NOTE: in tests, the discovery changed the wifi to a hotspot on most occasions.  on disconnect, it changed back.
     */
    public static final Strategy STRATEGY = Strategy.P2P_STAR;
//    public static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;
    //public static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.frag_container, new HelpFragment()).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //since I'm intercepting the back button, need to provide an exit method.
        int id = item.getItemId();
        if (id == R.id.exit) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(int id) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        // Replace whatever is in the fragment_container view with this fragment,
        if (id == 2) { //client
            transaction.replace(R.id.frag_container, new DiscoveryFragment());
        } else { //server
            transaction.replace(R.id.frag_container, new AdvertiseFragment());
        }
        // and add the transaction to the back stack so the user can navigate back
        transaction.addToBackStack(null);
        // Commit the transaction
        transaction.commit();
    }
}