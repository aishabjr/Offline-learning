package com.example.Offline_Learning;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.offline_learning.R;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * this is the Discovery side of Offline Learning.  (client)
 * This is the side likely to change the wifi to connect to advertise device.
 * <p>
 * Note, this code assumes one advertiser and that discovery connects to it.  There maybe many discovery connections to a single advertiser.
 * If uses P2P_CLUSTER and they can many advertisers, then it will ned change ConnectedEndPointID to a list
 * and comment out the stopDiscovery in the connection made section.
 */

public class DiscoveryFragment extends Fragment {
    String TAG = "DiscoveryFragment";
    TextView logger;
    Boolean mIsDiscovering = false;
    String UserNickName = "DiscoveryOfflineLearning";

    String ConnectedEndPointId;

    ImageView myImageView;


    public DiscoveryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View myView = inflater.inflate(R.layout.fragment_discovery, container, false);
        logger = myView.findViewById(R.id.di_output);

        myImageView = myView.findViewById(R.id.image);


        myView.findViewById(R.id.start_discovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsDiscovering) {
                    logthis("Was already in Discovery mode, stopped discovery.");
                    stopDiscovering();//in discovery mode, turn it off
                }
                else {
                    logthis("Initiaitng Discovery...");
                    startDiscovering();
                }
            }
        });
        myView.findViewById(R.id.end_discovery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ConnectedEndPointId.compareTo("") != 0) { //connected to someone
                    Nearby.getConnectionsClient(getContext()).disconnectFromEndpoint(ConnectedEndPointId);
                    ConnectedEndPointId = "";
                }
                if (mIsDiscovering) {
                    stopDiscovering();
                }
            }
        });
        return myView;
    }

    /**
     * Sets the device to discovery mode.  Once an endpoint is found, it will initiate a connection.
     */
    protected void startDiscovering() {
        Nearby.getConnectionsClient(getContext()).
                startDiscovery(
                        MainActivity.ServiceId,   //id for the service to be discovered.  ie, what are we looking for.

                        new EndpointDiscoveryCallback() {  //callback when we discovery that endpoint.
                            @Override
                            public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                                //we found an end point.
                                logthis("Found endpoint " + endpointId + " with name: " + info.getEndpointName());
                                //now make a initiate a connection to it.
                                makeConnection(endpointId);
                            }

                            @Override
                            public void onEndpointLost(String endpointId) {
                                logthis("Endpoint lost: " + endpointId);
                            }
                        },
                        new DiscoveryOptions.Builder().setStrategy(MainActivity.STRATEGY).build()
                )  //options for discovery.
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                mIsDiscovering = true;
                                logthis("Started discovery!");
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                mIsDiscovering = false;
                                logthis("Failed to start discovery.");
                                e.printStackTrace();
                            }
                        });
    }

    /**
     * Stops discovery.
     */
    protected void stopDiscovering() {
        mIsDiscovering = false;
        Nearby.getConnectionsClient(getContext()).stopDiscovery();
        logthis("Discovery Stopped.");
    }


    //the connection callback, both discovery and advertise use the same callback.
    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {

                private final SimpleArrayMap<Long, Payload> incomingPayloads = new SimpleArrayMap<>();

                @Override
                public void onConnectionInitiated(
                        String endpointId, ConnectionInfo connectionInfo) {
                    // Automatically accept the connection on both sides.
                    // setups the callbacks to read data from the other connection.
                    Nearby.getConnectionsClient(getContext()).acceptConnection(endpointId, new ReceiveFilePayloadCallback(getContext()));
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            // We're connected! Can now start sending and receiving data.
                            stopDiscovering();
                            ConnectedEndPointId = endpointId;
                            logthis("Status ok");
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
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                    logthis("Connection disconnected :" + endpointId);
                    ConnectedEndPointId = "";
                }
            };

    /**
     * Simple helper function to initiate a connect to the end point
     * it uses the callback setup above this function.
     */

    public void makeConnection(String endpointId) {
        Nearby.getConnectionsClient(getContext())
                .requestConnection(
                        UserNickName,   //human readable name for the local endpoint.  if null/empty, uses device name or model.
                        endpointId,
                        mConnectionLifecycleCallback)
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                logthis("Connection request successful");
                                // We successfully requested a connection. Now both sides
                                // must accept before the connection is established.
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Nearby Connections failed to request the connection.
                                logthis("Connection request failed");
                                e.printStackTrace();
                            }
                        });

    }

    class ReceiveFilePayloadCallback extends PayloadCallback {
        private final Context context;
        private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();

        public ReceiveFilePayloadCallback(Context context) {
            this.context = context;
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onPayloadReceived(String endpointId, Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                String payloadFilenameMessage = new String(payload.asBytes(), StandardCharsets.UTF_8);
                long payloadId = addPayloadFilename(payloadFilenameMessage);
            } else if (payload.getType() == Payload.Type.FILE) {
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingFilePayloads.put(payload.getId(), payload);
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is payloadId:filename.
         */
        private long addPayloadFilename(String payloadFilenameMessage) {
            String[] parts_id = payloadFilenameMessage.split(":");
            long payloadId = Long.parseLong(parts_id[0]);
            String[] parts_name = parts_id[parts_id.length-1].split("/");
            String filename = parts_name[parts_name.length-1];

            filePayloadFilenames.put(payloadId, filename);
            return payloadId;
        }

        private void processFilePayload(long payloadId) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.

            Payload filePayload = completedFilePayloads.get(payloadId);
            String filename = filePayloadFilenames.get(payloadId);

            if (filePayload != null && filename != null) {

                completedFilePayloads.remove(payloadId);
                filePayloadFilenames.remove(payloadId);

                File payloadFile = filePayload.asFile().asJavaFile();

                if (payloadFile == null) {
                    logthis("Payload java file is null in processFilePayload()");
                } else {
                    // Rename the file.
                    logthis("file renamed successfully: " + filename);
                    payloadFile.renameTo(new File(payloadFile.getParentFile(), filename));
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                logthis("onPayloadTransferUpdate: SUCCESS");
                Payload payload = incomingFilePayloads.remove(update.getPayloadId());
                completedFilePayloads.put(update.getPayloadId(), payload);

                processFilePayload(update.getPayloadId());
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        stopDiscovering();
    }

    /**
     * helper function to log and add to a textview.
     */
    public void logthis(String msg) {
        logger.append(msg + "\n");
        Log.d(TAG, msg);
    }
}
