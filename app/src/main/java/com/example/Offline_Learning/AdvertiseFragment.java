package com.example.Offline_Learning;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.Fragment;

import com.example.offline_learning.R;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

/**
 * This is the advertise side of Offline Learning.  (server)
 */

public class AdvertiseFragment extends Fragment {

    String TAG = "AdvertiseFragment";
    String UserNickName = "AdvertiseOfflineLearning";
    TextView logger;
    boolean mIsAdvertising = false;

    String ConnectedEndPointId = "";

    Button btnBack, btnForward, btnSend;

    private WebView browser;

    public AdvertiseFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View myView = inflater.inflate(R.layout.fragment_advertise, container, false);
        logger = myView.findViewById(R.id.ad_output);

        if (mIsAdvertising){
            logthis("Was already advertising, stopped advertising.");
            stopAdvertising();  //already advertising, turn it off
        }
        else {
            logthis("Initiating advertising...");
            startAdvertising();
        }

//        myView.findViewById(R.id.start_advertise).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (mIsAdvertising){
//                    logthis("Was already advertising, stopped advertising.");
//                    stopAdvertising();  //already advertising, turn it off
//                }
//                else {
//                    logthis("Initiating advertising...");
//                    startAdvertising();
//                }
//            }
//        });
        myView.findViewById(R.id.end_advertise).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ConnectedEndPointId.compareTo("") != 0) { //connected to someone
                    Nearby.getConnectionsClient(getContext()).disconnectFromEndpoint(ConnectedEndPointId);
                    ConnectedEndPointId = "";
                }
                if (mIsAdvertising) {
                    stopAdvertising();
                }
            }
        });

        //setup the WebView object and give it the initial destination.
        browser = (WebView) myView.findViewById(R.id.webkit);

        WebSettings settings = browser.getSettings();
        settings.setDomStorageEnabled(true);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setBuiltInZoomControls(true);

        browser.loadUrl("https://www.udacity.com");

        //setup the callBack, so when the user clicks a link, we intercept it and kept everything
        //in the app.
        browser.setWebViewClient(new AdvertiseFragment.CallBack());

        btnBack = myView.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (browser.canGoBack()) {
                    browser.goBack();
                }
            }
        });

        btnForward =  myView.findViewById(R.id.btnForward);
        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (browser.canGoForward()) {
                    browser.goForward();
                }
            }
        });

        btnSend =  myView.findViewById(R.id.btnSend);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImageChooser(ConnectedEndPointId);
            }
        });

        browser.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });

        return myView;
    }

    /*
     * This is override, so i can intercept when a user clicks a link, so it won't leave the app.
     */
    private class CallBack extends WebViewClient {

        //API 24+, so the N check is just for studio to shut up about it.
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.loadUrl(request.getUrl().toString());
            }
            return true;
        }

        //deprecated in API 24
        public boolean shouldOverrideUrlLoading(WebView view, String url) {

            browser.loadUrl(url);
            return true;
        }
    }

    /**
     * Callbacks for connections to other devices.  These call backs are used when a connection is initiated
     * and connection, and disconnect.
     * <p>
     * we auto accept any connection.  We with another callback that allows us to read the data.
     */
    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {

                private final SimpleArrayMap<Long, Payload> incomingPayloads = new SimpleArrayMap<>();

                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    logthis("Connection Initiated :" + endpointId + " Name is " + connectionInfo.getEndpointName());
                    // Automatically accept the connection on both sides.
                    // setups the callbacks to read data from the other connection.
                    Nearby.getConnectionsClient(getContext()).acceptConnection(endpointId, //mPayloadCallback);
                            new PayloadCallback() {
                                @Override
                                public void onPayloadReceived(String endpointId, Payload payload) {

                                    if (payload.getType() == Payload.Type.BYTES) {
                                        logthis("we got bytes. not handled");
                                    } else if (payload.getType() == Payload.Type.FILE) {
                                        incomingPayloads.put(payload.getId(), payload);
                                        logthis("We got a file.  not handled");
                                    }
                                    else if (payload.getType() == Payload.Type.STREAM)
                                        //payload.asStream().asInputStream()
                                        logthis("We got a stream, not handled");
                                }

                                @Override
                                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                                    //if stream or file, we need to know when the transfer has finished.  ignoring this right now.
                                }
                            });
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    logthis("Connection accept :" + endpointId + " result is " + result.toString());

                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            // We're connected! Can now start sending and receiving data.
                            ConnectedEndPointId = endpointId;
                            //if we don't then more can be added to conversation, when an List<string> of endpointIds to send to, instead a string.
                            // ... .add(endpointId);
                            stopAdvertising();  //and comment this out to allow more then one connection.
                            logthis("Status ok, making send button visible");
                            btnSend.setVisibility(View.VISIBLE);
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            logthis("Status rejected.  :(");
                            // The connection was rejected by one or both sides.
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            logthis("Status error.");
                            // The connection broke before it was able to be accepted.
                            break;
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    logthis("Connection disconnected :" + endpointId);
                    ConnectedEndPointId = "";  //need a remove if using a list.
                    btnSend.setVisibility(View.INVISIBLE);
                }
            };

    /**
     *  Start advertising the nearby.  It sets the callback from above with what to once we get a connection
     *  request.
     */
    private void startAdvertising() {

        Nearby.getConnectionsClient(getContext())
                .startAdvertising(
                        UserNickName,    //human readable name for the endpoint.
                        MainActivity.ServiceId,  //unique identifier for advertise endpoints
                        mConnectionLifecycleCallback,  //callback notified when remote endpoints request a connection to this endpoint.
                        new AdvertisingOptions.Builder().setStrategy(MainActivity.STRATEGY).build()
                )
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                mIsAdvertising = true;
                                logthis("Advertising started!");
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                mIsAdvertising = false;
                                // We were unable to start advertising.
                                logthis("Failed to advertise");
                                e.printStackTrace();
                            }
                        });
    }

    /**
     * turn off advertising.  Note, you can not add success and failure listeners.
     */
    public void stopAdvertising() {
        mIsAdvertising = false;
        Nearby.getConnectionsClient(getContext()).stopAdvertising() ;
        logthis("Advertising stopped.");
    }

    private static final int READ_REQUEST_CODE = 42;
    private static final String ENDPOINT_ID_EXTRA = "com.example.offlinelearning.EndpointId";

    /**
     * Fires an intent to spin up the file chooser UI and select an image for sending to endpointId.
     */

    private void showImageChooser(String endpointId) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(ENDPOINT_ID_EXTRA, endpointId);
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE
                && resultCode == Activity.RESULT_OK
                && resultData != null) {
            String endpointId = resultData.getStringExtra(ENDPOINT_ID_EXTRA);

            // The URI of the file selected by the user.
            Uri uri = resultData.getData();

            final Payload filePayload;
            try {
                // Open the ParcelFileDescriptor for this URI with read access.
                ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r");
                filePayload = Payload.fromFile(pfd);
            } catch (FileNotFoundException e) {
                Log.e("MyApp", "File not found", e);
                return;
            }


            // Construct a simple message mapping the ID of the file payload to the desired filename.
            final String filenameMessage = filePayload.getId() + ":" + uri.getLastPathSegment();

            // Send the filename message as a bytes payload.
            Payload filenameBytesPayload =
                    Payload.fromBytes(filenameMessage.getBytes(StandardCharsets.UTF_8));
            Nearby.getConnectionsClient(getContext()).sendPayload(ConnectedEndPointId, filenameBytesPayload).
                    addOnSuccessListener(new OnSuccessListener<Void>() {  //don't know if need this one.
                        @Override
                        public void onSuccess(Void aVoid) {
                            logthis("pic filename: " + filenameMessage + " sent successfully.");

                            // Finally, send the file payload.
                            Nearby.getConnectionsClient(getContext()).sendPayload(ConnectedEndPointId, filePayload).
                                    addOnSuccessListener(new OnSuccessListener<Void>() {  //don't know if need this one.
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            logthis("file send successful");
                                        }
                                    })
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            logthis("file send complete");
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            logthis("file send failed.");
                                            e.printStackTrace();
                                        }
                                    });
                        }
                    })
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            logthis("Filename send completed.");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            logthis("Filename send failed. Did not send file");
                            e.printStackTrace();
                        }
                    });
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        stopAdvertising();
    }

    /**
     * helper function to log and add to a textview.
     */
    public void logthis(String msg) {
        logger.append(msg + "\n");
        Log.d(TAG, msg);
    }
}
