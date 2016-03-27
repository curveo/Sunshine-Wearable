package com.example.android.sunshine.app;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.Future;

/**
 * Created by curtis on 3/21/16.
 */
public class DataLayerService implements DataApi.DataListener, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = "DataLayerService";
    public static final String PATH = "/weather-update";
    private static final boolean DEBUG_ENABLED = false;

    public interface DataLayerServiceCallbacks {
        void onDataUpdated(DataItem dataItem);
    }

    private DataLayerServiceCallbacks mDataLayerServiceCallbacks;
    private GoogleApiClient mGoogleApiClient;

    public DataLayerService(Context context, DataLayerServiceCallbacks callBacks) {
        mDataLayerServiceCallbacks = callBacks;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }

    public void connectAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(!mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.blockingConnect();
                    LOGD(TAG, "blockingConnect done, is connected? " + mGoogleApiClient.isConnected());
                    Wearable.DataApi.addListener(mGoogleApiClient, DataLayerService.this);
                }
                getPendingData();
            }
        }).start();
    }

    public void disconnect() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    private void getPendingData() {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {

            @Override
            public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                for(Node n: getConnectedNodesResult.getNodes()) {
                    Uri uri = new Uri.Builder()
                            .scheme(PutDataRequest.WEAR_URI_SCHEME)
                            .path(PATH)
                            .authority(n.getId())
                            .build();
                    Wearable.DataApi.getDataItem(mGoogleApiClient, uri)
                            .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {

                                @Override
                                public void onResult(DataApi.DataItemResult dataItemResult) {
                                    if (dataItemResult.getStatus().isSuccess()) {
                                        mDataLayerServiceCallbacks.onDataUpdated(dataItemResult.getDataItem());
                                    }
                                }
                            });
                }
            }

        });
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        LOGD("SunshineWatchface", "onDataChanged");
        for(DataEvent event: dataEventBuffer) {
            mDataLayerServiceCallbacks.onDataUpdated(event.getDataItem());
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        LOGD("SunshineWatchface", "onConnectionFailed");
    }

    public static final void LOGD(String tag, String msg) {
        if(DEBUG_ENABLED)
            Log.d(tag, "WEARABLE_TAG: " + msg);
    }
}
