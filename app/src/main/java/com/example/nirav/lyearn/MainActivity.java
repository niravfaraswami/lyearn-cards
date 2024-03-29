package com.example.nirav.lyearn;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.parse.ParseObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;


public class MainActivity extends Activity implements OnClickListener,
        ConnectionCallbacks, OnConnectionFailedListener{

    private static final int RC_SIGN_IN = 0;

    private static final int STATE_DEFAULT = 0;
    private static final int STATE_SIGN_IN = 1;


    // Logcat tag
    private static final String TAG = "MainActivity";

    // Profile pic image size in pixels
    private static final int PROFILE_PIC_SIZE = 400;

    // Google client to interact with Google API
    private GoogleApiClient mGoogleApiClient;

    // We use mSignInProgress to track whether user has clicked sign in.
    // mSignInProgress can be one of three values:
    //
    //       STATE_DEFAULT: The default state of the application before the user
    //                      has clicked 'sign in', or after they have clicked
    //                      'sign out'.  In this state we will not attempt to
    //                      resolve sign in errors and so will display our
    //                      Activity in a signed out state.
    //       STATE_SIGN_IN: This state indicates that the user has clicked 'sign
    //                      in', so resolve successive errors preventing sign in
    //                      until the user has successfully authorized an account
    //                      for our app.
    //   STATE_IN_PROGRESS: This state indicates that we have started an intent to
    //                      resolve an error, and so we should not start further
    //                      intents until the current intent completes.
    private int mSignInProgress;


    private static final String SAVED_PROGRESS = "sign_in_progress";

    /**
     * A flag indicating that a PendingIntent is in progress and prevents us
     * from starting further intents.
     */
    private boolean mIntentInProgress;

    private boolean mSignInClicked;

    private ConnectionResult mConnectionResult;

    private SignInButton btnSignIn;
    private ProgressBar mProgressBar;

    private String personName;
    private String personPhotoUrl;
    private String personGooglePlusProfile;
    private String email;

    private String[] userName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSignIn = (SignInButton) findViewById(R.id.btn_sign_in);
        btnSignIn.setVisibility(View.INVISIBLE);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar1);
        mProgressBar.setVisibility(View.VISIBLE);

        // Button click listeners
        btnSignIn.setOnClickListener(this);

        if (savedInstanceState != null) {
            mSignInProgress = savedInstanceState
                    .getInt(SAVED_PROGRESS, STATE_DEFAULT);
        }

        mGoogleApiClient = buildGoogleApiClient();

    }

    private GoogleApiClient buildGoogleApiClient() {
        // When we build the GoogleApiClient we specify where connected and
        // connection failed callbacks should be returned, which Google APIs our
        // app uses and which OAuth 2.0 scopes our app requests.
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API, Plus.PlusOptions.builder().build())
                .addScope(Plus.SCOPE_PLUS_LOGIN);

        return builder.build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVED_PROGRESS, mSignInProgress);
    }



    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (!result.hasResolution()) {
            btnSignIn.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.INVISIBLE);
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
            return;
        }

        if (!mIntentInProgress) {
            mConnectionResult = result;
            btnSignIn.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.INVISIBLE);
            if (mSignInClicked) {
                btnSignIn.setVisibility(View.INVISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                resolveSignInError();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (requestCode == RC_SIGN_IN) {
            if (responseCode != RESULT_OK) {
                mSignInClicked = false;
            }else {
                // If the error resolution was not successful or the user canceled,
                // we should stop processing errors.
                btnSignIn.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.INVISIBLE);
                mSignInProgress = STATE_DEFAULT;
            }

            mIntentInProgress = false;

            if (!mGoogleApiClient.isConnecting()) {
                btnSignIn.setVisibility(View.INVISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                mGoogleApiClient.connect();
            }
        }
    }

    @Override
    public void onConnected(Bundle arg0) {

        mProgressBar.setVisibility(View.VISIBLE);
        btnSignIn.setVisibility(View.INVISIBLE);

        mSignInClicked = false;
        Toast.makeText(this, "User is connected!", Toast.LENGTH_LONG).show();

        // Get user's information
        getProfileInformation();

        //send user info to server, ask for more user information(if exist) and update UI
        checkUserInfo();
    }

    private void checkUserInfo() {

        JSONObject userInfo = new JSONObject();
        try {
            userInfo.put("firstName", userName[0]);
            userInfo.put("lastName", userName[1]);
            userInfo.put("imageUrl", personPhotoUrl);
            userInfo.put("emailID", email);

        } catch (Exception e) {

            e.printStackTrace();

        }

        RequestQueue requestQueue = VolleySingleton.getsInstance().getRequestQueue();
        JsonObjectRequest request  = new JsonObjectRequest(Config.LOGIN_URL, userInfo ,new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.d("User Info", response.toString());
                parseJSONObject(response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("User Info", error.toString());
            }
        });

        requestQueue.add(request);
    }

    private void parseJSONObject(JSONObject response) {

        try {
            JSONObject userJSON = new JSONObject(response.toString());
            String userID = userJSON.optString("userId");
            JSONObject userObjectJSON = userJSON.optJSONObject("userObject");
            String firstName = userObjectJSON.optString("firstName");
            String lastName = userObjectJSON.optString("lastName");
            String imageUrl = userObjectJSON.optString("imageUrl");
            String emailID = userObjectJSON.optString("emailID");
            String phoneNumber = null;
            String skype = null;
            String whatIDo = null;
            String points = null;

            if(userObjectJSON.optString("phoneNumber") != null) {
                phoneNumber = userObjectJSON.optString("phoneNumber");
            }


            if(userObjectJSON.optString("skype") != null) {
                skype = userObjectJSON.optString("skype");
            }

            if(userObjectJSON.optString("whatIDo") != null) {
                whatIDo = userObjectJSON.optString("whatIDo");
            }

            if(userObjectJSON.optString("points") != null) {
                points = userObjectJSON.optString("points");
            }

            UserProfileInfo.UserInfo mUserInfo = new UserProfileInfo.UserInfo();
            mUserInfo.setInfo(firstName, lastName, whatIDo, skype, phoneNumber, imageUrl, emailID, points, userID);

            // Update the UI after signin
            updateUI(true);

            // Indicate that the sign in process is complete.
            mSignInProgress = STATE_DEFAULT;

        }
        catch (JSONException e) {
             Log.e("JSONException Error", e.toString());
        }


    }

    /**
     * Updating the UI, showing/hiding buttons and profile layout
     * */
    private void updateUI(boolean isSignedIn) {
        if (isSignedIn) {
            mProgressBar.setVisibility(View.INVISIBLE);
            startApp();
        }
    }

    public void startApp() {
        Intent intent = new Intent(this, com.example.nirav.lyearn.HomeActivity.class);
        String[] userProfile = {personName, personGooglePlusProfile, personPhotoUrl, email};
        intent.putExtra(getString(R.string.key_name), userProfile);
        startActivityForResult(intent, 1);
    }

    /**
     * Fetching user's information name, email, profile pic
     * */
    private void getProfileInformation() {
        try {
            if (Plus.PeopleApi.getCurrentPerson(mGoogleApiClient) != null) {
                Person currentPerson = Plus.PeopleApi
                        .getCurrentPerson(mGoogleApiClient);
                personName = currentPerson.getDisplayName();
                personPhotoUrl = currentPerson.getImage().getUrl();
                personGooglePlusProfile = currentPerson.getUrl();
                email = Plus.AccountApi.getAccountName(mGoogleApiClient);

                Log.e(TAG, "Name: " + personName + ", plusProfile: "
                        + personGooglePlusProfile + ", email: " + email
                        + ", Image: " + personPhotoUrl);

                userName = personName.split("\\s+");


                // by default the profile url gives 50x50 px image only
                // we can replace the value with whatever dimension we want by
                // replacing sz=X
                personPhotoUrl = personPhotoUrl.substring(0,
                        personPhotoUrl.length() - 2)
                        + PROFILE_PIC_SIZE;

            } else {
                Toast.makeText(getApplicationContext(),
                        "Person information is null", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionSuspended(int arg0) {
        mGoogleApiClient.connect();
        updateUI(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Button on click listener
     * */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_sign_in:
                // Signin button clicked
                mSignInProgress = STATE_SIGN_IN;
                btnSignIn.setVisibility(View.INVISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                signInWithGplus();
                break;
        }
    }

    /**
     * Sign-in into google
     * */
    private void signInWithGplus() {
        if (!mGoogleApiClient.isConnecting()) {
            mSignInClicked = true;
            resolveSignInError();
        }
    }

    private void resolveSignInError() {
        if (mConnectionResult.hasResolution()) {
            try {
                mIntentInProgress = true;
                mConnectionResult.startResolutionForResult(this, RC_SIGN_IN);
            } catch (SendIntentException e) {
                mIntentInProgress = false;
                mGoogleApiClient.connect();
            }
        }
    }
}


/*

            userInfo.put("userID", "");
            userInfo.put("firstName", userName[0]);
            userInfo.put("lastName", userName[1]);
            userInfo.put("phoneNumber", "");
            userInfo.put("skype", "");
            userInfo.put("imageUrl", personPhotoUrl);
            userInfo.put("whatIDo", "");
            userInfo.put("points","");
            userInfo.put("emailID", email);
 */