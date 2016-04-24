package com.mac.isaac.castcompanionapp;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

public class CastActivity extends AppCompatActivity {

    private Button mButton;
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mMediaRouteSelector;
    private MediaRouter.Callback mMediaRouterCallback;
    private CastDevice mSelectedDevice;
    private GoogleApiClient mApiClient;
    private RemoteMediaPlayer mRemoteMediaPlayer;
    private Cast.Listener mCastClientListener;
    private boolean mWaitingForReconnect = false;
    private boolean mApplicationStarted = false;
    private boolean mVideoIsLoaded;
    private boolean mIsPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("MYTAG", "onCreate()");

        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mVideoIsLoaded)
                    startVideo();
                else
                    controlVideo();
            }
        });

        initMediaRouter();
    }

    private void initMediaRouter() {
        Log.i("MYTAG", "initMediaRouter()");
        // Configure Cast device discovery
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(
                        CastMediaControlIntent.categoryForCast(getString(R.string.app_id)))
                .build();
        mMediaRouterCallback = new MediaRouterCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("MYTAG", "onResume()");
        // Start media router discovery
        mMediaRouter.addCallback( mMediaRouteSelector,
                mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu ) {
        Log.i("MYTAG", "onCreateOptionsMenu()");
        super.onCreateOptionsMenu( menu );
        getMenuInflater().inflate( R.menu.menu, menu );
        MenuItem mediaRouteMenuItem = menu.findItem( R.id.media_route_menu_item );
        MediaRouteActionProvider mediaRouteActionProvider = (MediaRouteActionProvider) MenuItemCompat.getActionProvider( mediaRouteMenuItem );
        mediaRouteActionProvider.setRouteSelector( mMediaRouteSelector );
        return true;
    }

    private void startVideo() {
        Log.i("MYTAG", "startVideo()");
        MediaMetadata mediaMetadata = new MediaMetadata( MediaMetadata.MEDIA_TYPE_MOVIE );
        mediaMetadata.putString( MediaMetadata.KEY_TITLE, getString( R.string.video_title ) );

        MediaInfo mediaInfo = new MediaInfo.Builder( getString( R.string.video_url ) )
                .setContentType( getString( R.string.content_type_mp4 ) )
                .setStreamType( MediaInfo.STREAM_TYPE_BUFFERED )
                .setMetadata( mediaMetadata )
                .build();
        try {
            mRemoteMediaPlayer
                    .load( mApiClient, mediaInfo, true )
                    .setResultCallback( new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult( RemoteMediaPlayer.MediaChannelResult mediaChannelResult ) {
                            if( mediaChannelResult.getStatus().isSuccess() ) {
                                mVideoIsLoaded = true;
                                mButton.setText( getString( R.string.pause_video ) );
                            }
                        }
                    } );
        } catch( Exception e ) {
            Log.e("MYTAG", "Error starting video "+e.getMessage());
            e.printStackTrace();
        }
    }

    private void controlVideo() {
        Log.i("MYTAG", "controlVideo()");
        if( mRemoteMediaPlayer == null || !mVideoIsLoaded )
            return;
        if( mIsPlaying ) {
            mRemoteMediaPlayer.pause( mApiClient );
            mButton.setText( getString( R.string.resume_video ) );
        } else {
            mRemoteMediaPlayer.play( mApiClient );
            mButton.setText( getString( R.string.pause_video ) );
        }
    }

    private void reconnectChannels( Bundle hint ) {
        Log.i("MYTAG", "reconnectChannels()");
        if( ( hint != null ) && hint.getBoolean( Cast.EXTRA_APP_NO_LONGER_RUNNING ) ) {
            //Log.e( TAG, "App is no longer running" );
            teardown();
        } else {
            try {
                Cast.CastApi.setMessageReceivedCallbacks( mApiClient, mRemoteMediaPlayer.getNamespace(), mRemoteMediaPlayer );
            } catch( IOException e ) {
                Log.e( "MYTAG", "Exception while creating media channel ", e );
            } catch( NullPointerException e ) {
                Log.e( "MYTAG", "Something wasn't reinitialized for reconnectChannels" );
            }
        }
    }

    private class ConnectionFailedListener implements GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed( ConnectionResult connectionResult ) {
            Log.i("MYTAG", "onConnectionFailed()");
            teardown();
        }
    }

    @Override
    protected void onPause() {
        Log.i("MYTAG", "onPause()");
        if ( isFinishing() ) {
            // End media router discovery
            mMediaRouter.removeCallback( mMediaRouterCallback );
        }
        super.onPause();
    }

    private void teardown() {
        Log.i("MYTAG", "teardown()");
        if( mApiClient != null ) {
            if( mApplicationStarted ) {
                try {
                    Cast.CastApi.stopApplication( mApiClient );
                    if( mRemoteMediaPlayer != null ) {
                        Cast.CastApi.removeMessageReceivedCallbacks( mApiClient, mRemoteMediaPlayer.getNamespace() );
                        mRemoteMediaPlayer = null;
                    }
                } catch( IOException e ) {
                    Log.e( "MYTAG", "Exception while removing application " + e );
                }
                mApplicationStarted = false;
            }
            if( mApiClient.isConnected() )
                mApiClient.disconnect();
            mApiClient = null;
        }
        mSelectedDevice = null;
        mVideoIsLoaded = false;
    }

    private void initCastClientListener() {
        mCastClientListener = new Cast.Listener() {
            @Override
            public void onApplicationStatusChanged() {
                Log.i("MYTAG", "onApplicationStatusChanged()");
            }

            @Override
            public void onVolumeChanged() {
                Log.i("MYTAG", "onVolumeChanged()");
            }

            @Override
            public void onApplicationDisconnected( int statusCode ) {
                Log.i("MYTAG", "onApplicationDisconnected() "+statusCode);
                teardown();
            }
        };
    }

    private void launchReceiver() {
        Log.i("MYTAG", "launchReceiver()");
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder( mSelectedDevice, mCastClientListener );

        ConnectionCallbacks mConnectionCallbacks = new ConnectionCallbacks();
        ConnectionFailedListener mConnectionFailedListener = new ConnectionFailedListener();
        mApiClient = new GoogleApiClient.Builder(CastActivity.this)
                .addApi( Cast.API, apiOptionsBuilder.build() )
                .addConnectionCallbacks( mConnectionCallbacks )
                .addOnConnectionFailedListener( mConnectionFailedListener )
                .build();

        mApiClient.connect();
    }

    private void initRemoteMediaPlayer() {
        Log.i("MYTAG", "initRemoteMediaPlayer()");
        mRemoteMediaPlayer = new RemoteMediaPlayer();
        mRemoteMediaPlayer.setOnStatusUpdatedListener( new RemoteMediaPlayer.OnStatusUpdatedListener() {
            @Override
            public void onStatusUpdated() {
                MediaStatus mediaStatus = mRemoteMediaPlayer.getMediaStatus();
                mIsPlaying = mediaStatus.getPlayerState() == MediaStatus.PLAYER_STATE_PLAYING;
            }
        });

        mRemoteMediaPlayer.setOnMetadataUpdatedListener( new RemoteMediaPlayer.OnMetadataUpdatedListener() {
            @Override
            public void onMetadataUpdated() {
                Log.i("MYTAG", "onMetadataUpdated()");
            }
        });
    }

    private class MediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.i("MYTAG", "onRouteSelected()");
            initCastClientListener();
            initRemoteMediaPlayer();

            mSelectedDevice = CastDevice.getFromBundle( info.getExtras() );

            launchReceiver();
        }

        @Override
        public void onRouteUnselected( MediaRouter router, MediaRouter.RouteInfo info ) {
            Log.i("MYTAG", "onRouteUnselected()");
            teardown();
            mSelectedDevice = null;
            mButton.setText( getString( R.string.play_video ) );
            mVideoIsLoaded = false;
        }

    }

    private class ConnectionCallbacks implements GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected( Bundle hint ) {
            Log.i("MYTAG", "onConnected()");
            if( mWaitingForReconnect ) {
                mWaitingForReconnect = false;
                reconnectChannels( hint );
            } else {
                try {
                    Cast.CastApi.launchApplication( mApiClient, getString( R.string.app_id ), false )
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(
                                                Cast.ApplicationConnectionResult applicationConnectionResult) {
                                            Status status = applicationConnectionResult.getStatus();
                                            if( status.isSuccess() ) {
                                                //Values that can be useful for storing/logic
                                                ApplicationMetadata applicationMetadata =
                                                        applicationConnectionResult.getApplicationMetadata();
                                                String sessionId =
                                                        applicationConnectionResult.getSessionId();
                                                String applicationStatus =
                                                        applicationConnectionResult.getApplicationStatus();
                                                boolean wasLaunched =
                                                        applicationConnectionResult.getWasLaunched();

                                                mApplicationStarted = true;
                                                reconnectChannels( null );
                                            }
                                        }
                                    }
                            );
                } catch ( Exception e ) {
                    Log.e("MYTAG", "error launching application");
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.i("MYTAG", "onConnectionSuspended()");
            mWaitingForReconnect = true;
        }
    }

}

