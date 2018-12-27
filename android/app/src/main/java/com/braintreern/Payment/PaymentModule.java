package com.braintreern.Payment;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.braintreepayments.api.dropin.DropInActivity;
import com.braintreepayments.api.dropin.DropInRequest;
import com.braintreepayments.api.dropin.DropInResult;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.TextHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cz.msebera.android.httpclient.Header;

public class PaymentModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final String TAG = PaymentModule.class.getSimpleName();
    private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";
    private static final String E_ACTIVITY_USER_CANCELLATION = "E_ACTIVITY_USER_CANCELLATION";
    private static final String E_ACTIVITY_TRANSACTION_ERROR = "E_ACTIVITY_TRANSACTION_ERROR";
    private static final String E_REQUEST_TOKEN_ERROR = "E_REQUEST_TOKEN_ERROR";
    private static final String E_REQUEST_NONCE_ERROR = "E_REQUEST_NONCE_ERROR";
    private static final int BRAINTREE_REQUEST_CODE = 4949;

    private String clientToken;
    private String PATH_TO_TOKEN_SERVER;
    private String PATH_TO_NONCE_SERVER;
    private Promise mPayPromise;

    public PaymentModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "BrainTreePaymentCustom";
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if(requestCode == BRAINTREE_REQUEST_CODE){
            if (Activity.RESULT_OK == resultCode){
                DropInResult result = data.getParcelableExtra(DropInResult.EXTRA_DROP_IN_RESULT);
                String paymentNonce = result.getPaymentMethodNonce().getNonce();
                //send to your server
                sendPaymentNonceToServer(paymentNonce);
            }else if(resultCode == Activity.RESULT_CANCELED){
                Log.d(TAG, "User cancelled payment");
                mPayPromise.reject(E_ACTIVITY_USER_CANCELLATION, "User cancelled payment");
            }else {
                Exception error = (Exception)data.getSerializableExtra(DropInActivity.EXTRA_ERROR);
                Log.d(TAG, " error exception: " + error.getMessage());
                mPayPromise.reject(E_ACTIVITY_TRANSACTION_ERROR, error.getMessage());
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }

    @ReactMethod
    public void pay(ReadableMap config, final Promise promise){
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist");
            return;
        }

        mPayPromise = promise;
        PATH_TO_TOKEN_SERVER = config.getString("tokenServerUrl");
        PATH_TO_NONCE_SERVER = config.getString("nonceServerUrl");
        try{
            getClientTokenFromServer(currentActivity);
        } catch (Exception e){
            mPayPromise.reject(E_REQUEST_TOKEN_ERROR, "Request server to get token error");
        }

    }

    private void getClientTokenFromServer(final Activity currentActivity){
        AsyncHttpClient androidClient = new AsyncHttpClient();
        androidClient.get(PATH_TO_TOKEN_SERVER, null, new JsonHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Log.d(TAG, "Request token error: " + responseString);
                throw new RuntimeException("Get Error when Request server to get token");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                try {
                    clientToken = (String)response.get("clientToken");
                    DropInRequest dropInRequest = new DropInRequest().clientToken(clientToken);
                    currentActivity.startActivityForResult(dropInRequest.getIntent(currentActivity), BRAINTREE_REQUEST_CODE);
                    Log.d(TAG, "client token: " + clientToken);
                } catch (JSONException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void sendPaymentNonceToServer(String paymentNonce){
        RequestParams params = new RequestParams("nonce", paymentNonce);
        AsyncHttpClient androidClient = new AsyncHttpClient();
        androidClient.post(PATH_TO_NONCE_SERVER, params, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Log.d(TAG, "Error: Failed to create a transaction");
                mPayPromise.reject(E_REQUEST_NONCE_ERROR, "Error: Failed to create a transaction");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Log.d(TAG, "Output Transaction: " + responseString);
                mPayPromise.resolve(responseString);
            }
        });
    }
}