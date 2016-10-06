package org.apache.cordova.health;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataTypeCreateRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.DataTypeResult;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Health plugin Android code.
 * MIT licensed.
 */
public class HealthPlugin extends CordovaPlugin {
    //logger tag
    private static final String TAG = "cordova-plugin-health";

    //calling activity
    private CordovaInterface cordova;


    //actual Google API client
    private GoogleApiClient mClient;

    public static final int REQUEST_OAUTH = 1;
    public static final LinkedList<String> dynPerms = new LinkedList<String>();
    public static final int REQUEST_DYN_PERMS = 2;

    //Scope for read/write access to activity-related data types in Google Fit.
    //These include activity type, calories consumed and expended, step counts, and others.
    public static Map<String, DataType> activitydatatypes = new HashMap<String, DataType>();

    static {
        activitydatatypes.put("steps", DataType.TYPE_STEP_COUNT_DELTA);
        activitydatatypes.put("calories", DataType.TYPE_CALORIES_EXPENDED);
        activitydatatypes.put("calories.basal", DataType.TYPE_BASAL_METABOLIC_RATE);
        activitydatatypes.put("activity", DataType.TYPE_ACTIVITY_SEGMENT);
    }

    //Scope for read/write access to biometric data types in Google Fit. These include heart rate, height, and weight.
    public static Map<String, DataType> bodydatatypes = new HashMap<String, DataType>();

    static {
        bodydatatypes.put("height", DataType.TYPE_HEIGHT);
        bodydatatypes.put("weight", DataType.TYPE_WEIGHT);
        bodydatatypes.put("heart_rate", DataType.TYPE_HEART_RATE_BPM);
        bodydatatypes.put("fat_percentage", DataType.TYPE_BODY_FAT_PERCENTAGE);
    }

    //Scope for read/write access to location-related data types in Google Fit. These include location, distance, and speed.
    public static Map<String, DataType> locationdatatypes = new HashMap<String, DataType>();

    static {
        locationdatatypes.put("distance", DataType.TYPE_DISTANCE_DELTA);
    }

    //Scope for read/write access to nutrition data types in Google Fit.
    public static Map<String, DataType> nutritiondatatypes = new HashMap<String, DataType>();

    static {
        //nutritiondatatypes.put("food", DataType.TYPE_NUTRITION);
    }

    public static Map<String, DataType> customdatatypes = new HashMap<String, DataType>();


    public HealthPlugin() {
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cordova = cordova;
    }

    private CallbackContext authReqCallbackCtx;
    private void authReqSuccess(){
        //Create custom data types
        cordova.getThreadPool().execute(new Runnable(){

            @Override
            public void run() {
                try{
                    String packageName = cordova.getActivity().getApplicationContext().getPackageName();
                    DataTypeCreateRequest request = new DataTypeCreateRequest.Builder()
                            .setName(packageName+".gender")
                            .addField("gender",Field.FORMAT_STRING)
                            .build();
                    PendingResult<DataTypeResult> pendingResult =  Fitness.ConfigApi.createCustomDataType(mClient, request);
                    DataTypeResult dataTypeResult = pendingResult.await();
                    if(!dataTypeResult.getStatus().isSuccess()){
                        authReqCallbackCtx.error(dataTypeResult.getStatus().getStatusMessage());
                        return;
                    }
                    customdatatypes.put("gender", dataTypeResult.getDataType());

                    request = new DataTypeCreateRequest.Builder()
                            .setName(packageName + ".date_of_birth")
                            .addField("day",Field.FORMAT_INT32)
                            .addField("month",Field.FORMAT_INT32)
                            .addField("year",Field.FORMAT_INT32)
                            .build();
                    pendingResult = Fitness.ConfigApi.createCustomDataType(mClient, request);
                    dataTypeResult = pendingResult.await();
                    if(!dataTypeResult.getStatus().isSuccess()){
                        authReqCallbackCtx.error(dataTypeResult.getStatus().getStatusMessage());
                        return;
                    }
                    customdatatypes.put("date_of_birth", dataTypeResult.getDataType());

                    Log.i(TAG, "All custom data types created");
                    requestDynamicPermissions();
                } catch (Exception ex){
                    authReqCallbackCtx.error(ex.getMessage());
                }
            }
        });
    }

    public void requestDynamicPermissions(){
        if(dynPerms.isEmpty()){
            authReqCallbackCtx.success();
        } else {
            LinkedList<String> perms = new LinkedList<String>();
            for(String p: dynPerms){
                if(!cordova.hasPermission(p)){
                    perms.add(p);
                }
            }
            if(perms.isEmpty()){
                authReqCallbackCtx.success();
            } else {
                cordova.requestPermissions(this, REQUEST_DYN_PERMS, perms.toArray(new String[perms.size()]));
            }
        }
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if(requestCode == REQUEST_DYN_PERMS) {
            for(int i=0; i<grantResults.length; i++) {
                if(grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    String errmsg = "Permission denied ";
                    for(String perm : permissions){
                        errmsg += " " + perm;
                    }
                    authReqCallbackCtx.error("Permission denied: " + permissions[i]);
                    return;
                }
            }
            //all accepted!
            authReqCallbackCtx.success();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_OAUTH) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "Got authorisation from Google Fit");
                if(!mClient.isConnected() && !mClient.isConnecting()){
                    Log.d(TAG, "Re-trying connection with Fit");
                    mClient.connect();
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // The user cancelled the login dialog before selecting any action.
                authReqCallbackCtx.error("User cancelled the dialog");
            } else authReqCallbackCtx.error("Authorisation failed, result code "+ resultCode);
        }
    }

    /**
     * The "execute" method that Cordova calls whenever the plugin is used from the JavaScript
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        if("isAvailable".equals(action)){
            isAvailable(callbackContext);
            return true;
        } else  if ("requestAuthorization".equals(action)) {
            requestAuthorization(args, callbackContext);
            return true;
        } else if("query".equals(action)){
            cordova.getThreadPool().execute(new Runnable(){
                @Override
                public void run() {
                    try{
                        query(args, callbackContext);
                    } catch (Exception ex){
                        callbackContext.error(ex.getMessage());
                    }
                }
            });
            return true;
        } else if("queryAggregated".equals(action)){
            cordova.getThreadPool().execute(new Runnable(){
                @Override
                public void run() {
                    try{
                        queryAggregated(args, callbackContext);
                    } catch (Exception ex){
                        callbackContext.error(ex.getMessage());
                    }
                }
            });
            return true;
        } else if("store".equals(action)) {
            store(args, callbackContext);
            return true;
        }

        return false;
    }


    private void isAvailable(final CallbackContext callbackContext){
        //first check that the Google APIs are available
        GoogleApiAvailability gapi = GoogleApiAvailability.getInstance();
        int apiresult = gapi.isGooglePlayServicesAvailable(this.cordova.getActivity());
        if(apiresult != ConnectionResult.SUCCESS){
            PluginResult result;
            result = new PluginResult(PluginResult.Status.OK, false);
            callbackContext.sendPluginResult(result);
            if(gapi.isUserResolvableError(apiresult)){
                // show the dialog, but no action is performed afterwards
                gapi.showErrorDialogFragment(this.cordova.getActivity(), apiresult, 1000);
            }
        } else {
            // check that Google Fit is actually installed
            PackageManager pm = cordova.getActivity().getApplicationContext().getPackageManager();
            try {
                pm.getPackageInfo("com.google.android.apps.fitness", PackageManager.GET_ACTIVITIES);
                // Success return object
                PluginResult result;
                result = new PluginResult(PluginResult.Status.OK, true);
                callbackContext.sendPluginResult(result);
            } catch (PackageManager.NameNotFoundException e) {
                //show popup for downloading app
                //code from http://stackoverflow.com/questions/11753000/how-to-open-the-google-play-store-directly-from-my-android-application

                try {
                  cordova.getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.fitness")));
                } catch (android.content.ActivityNotFoundException anfe) {
                    cordova.getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.fitness")));
                }

                PluginResult result;
                result = new PluginResult(PluginResult.Status.OK, false);
                callbackContext.sendPluginResult(result);
            }
        }
    }

    private void requestAuthorization(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.cordova.setActivityResultCallback(this);
        authReqCallbackCtx = callbackContext;

        //reset scopes
        boolean bodyscope = false;
        boolean activityscope = false;
        boolean locationscope = false;
        boolean nutritionscope = false;

        for(int i=0; i< args.length(); i++){
            String type= args.getString(i);
            if(bodydatatypes.get(type) != null)
                bodyscope = true;
            if(activitydatatypes.get(type) != null)
                activityscope = true;
            if(locationdatatypes.get(type) != null)
                locationscope = true;
            if(nutritiondatatypes.get(type) != null)
                nutritionscope = true;
        }
        dynPerms.clear();

        // peckinc, crashs my 5.0.1 phone
//        if(locationscope)
//            dynPerms.add(Manifest.permission.ACCESS_FINE_LOCATION);
//        if(bodyscope)
//            dynPerms.add(Manifest.permission.BODY_SENSORS);

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(this.cordova.getActivity());
        builder.addApi(Fitness.HISTORY_API);
        builder.addApi(Fitness.CONFIG_API);
        builder.addApi(Fitness.SESSIONS_API);
        //scopes: https://developers.google.com/android/reference/com/google/android/gms/common/Scopes.html
        if(bodyscope) builder.addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE));
        if(activityscope) builder.addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE));
        if(locationscope) builder.addScope(new Scope(Scopes.FITNESS_LOCATION_READ_WRITE));
        if(nutritionscope) builder.addScope(new Scope(Scopes.FITNESS_NUTRITION_READ_WRITE));

        builder.addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        mClient.unregisterConnectionCallbacks(this);
                        Log.i(TAG, "Google Fit connected");
                        authReqSuccess();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        String message = "";
                        if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                            message = "connection lost, network lost";
                        } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                            message = "connection lost, service disconnected";
                        } else message = "connection lost, code: " + i;
                        Log.e(TAG, message);
                        callbackContext.error(message);
                    }
                });

        builder.addOnConnectionFailedListener(
                new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.i(TAG, "Connection failed, cause: " + result.toString());
                        if (!result.hasResolution()) {
                            Log.e(TAG, "Connection failure has no resolution: " + result.getErrorMessage());
                            callbackContext.error(result.getErrorMessage());
                        } else {
                            // The failure has a resolution. Resolve it.
                            // Called typically when the app is not yet authorized, and an
                            // authorization dialog is displayed to the user.
                            try {
                                Log.i(TAG, "Attempting to resolve failed connection");
                                result.startResolutionForResult(cordova.getActivity(), REQUEST_OAUTH);
                            } catch (IntentSender.SendIntentException e) {
                                Log.e(TAG, "Exception while starting resolution activity", e);
                                callbackContext.error(result.getErrorMessage());
                            }
                        }
                    }
                }
        );
        mClient = builder.build();
        mClient.connect();
    }

    private boolean lightConnect(){
        this.cordova.setActivityResultCallback(this);

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(this.cordova.getActivity().getApplicationContext());
        builder.addApi(Fitness.HISTORY_API);
        builder.addApi(Fitness.CONFIG_API);
        builder.addApi(Fitness.SESSIONS_API);

        mClient = builder.build();
        mClient.blockingConnect();
        if(mClient.isConnected()){
            Log.i(TAG, "Google Fit connected (light)");
            return true;
        } else {
            return false;
        }
    }

    private void query(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if(!args.getJSONObject(0).has("startDate")){
            callbackContext.error("Missing argument startDate");
            return;
        }
        long st = args.getJSONObject(0).getLong("startDate");
        if(!args.getJSONObject(0).has("endDate")){
            callbackContext.error("Missing argument endDate");
            return;
        }
        long et = args.getJSONObject(0).getLong("endDate");
        if(!args.getJSONObject(0).has("dataType")){
            callbackContext.error("Missing argument dataType");
            return;
        }
        String datatype = args.getJSONObject(0).getString("dataType");
        DataType dt = null;

        if(bodydatatypes.get(datatype) != null)
            dt = bodydatatypes.get(datatype);
        if(activitydatatypes.get(datatype) != null)
            dt = activitydatatypes.get(datatype);
        if(locationdatatypes.get(datatype) != null)
            dt = locationdatatypes.get(datatype);
        if(nutritiondatatypes.get(datatype) != null)
            dt = nutritiondatatypes.get(datatype);
        if (customdatatypes.get(datatype) != null)
            dt = customdatatypes.get(datatype);
        if (dt == null) {
            callbackContext.error("Datatype " + datatype + " not supported");
            return;
        }
        final DataType DT = dt;

        if ((mClient == null) || (!mClient.isConnected())){
            if(!lightConnect()){
                callbackContext.error("Cannot connect to Google Fit");
                return;
            }
        }


        DataReadRequest readRequest =  new DataReadRequest.Builder()
                .setTimeRange(st, et, TimeUnit.MILLISECONDS)
                .read(dt)
                .build();

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await();

        if (dataReadResult.getStatus().isSuccess()) {
            JSONArray resultset = new JSONArray();
            List<DataSet> datasets = dataReadResult.getDataSets();
            for (DataSet dataset : datasets) {
                for (DataPoint datapoint : dataset.getDataPoints()) {
                    JSONObject obj = new JSONObject();
                    obj.put("startDate", datapoint.getStartTime(TimeUnit.MILLISECONDS));
                    obj.put("endDate", datapoint.getEndTime(TimeUnit.MILLISECONDS));
                    DataSource dataSource = datapoint.getOriginalDataSource();
                    if (dataSource != null) {
						String sourceName = dataSource.getName();
						obj.put("sourceName", sourceName);
                        String sourceBundleId = dataSource.getAppPackageName();
                        obj.put("sourceBundleId", sourceBundleId);
                    }

                    //reference for fields: https://developers.google.com/android/reference/com/google/android/gms/fitness/data/Field.html
                    if (DT.equals(DataType.TYPE_STEP_COUNT_DELTA)) {
                        int steps = datapoint.getValue(Field.FIELD_STEPS).asInt();
                        obj.put("value", steps);
                        obj.put("unit", "count");
                    } else if (DT.equals(DataType.TYPE_DISTANCE_DELTA)) {
                        float distance = datapoint.getValue(Field.FIELD_DISTANCE).asFloat();
                        obj.put("value", distance);
                        obj.put("unit", "m");
                    } else if (DT.equals(DataType.TYPE_CALORIES_EXPENDED)) {
                        float calories = datapoint.getValue(Field.FIELD_CALORIES).asFloat();
                        obj.put("value", calories);
                        obj.put("unit", "kcal");
                    } else if (DT.equals(DataType.TYPE_BASAL_METABOLIC_RATE)) {
                        float calories = datapoint.getValue(Field.FIELD_CALORIES).asFloat();
                        obj.put("value", calories);
                        obj.put("unit", "kcal");
                    } else if (DT.equals(DataType.TYPE_HEIGHT)) {
                        float height = datapoint.getValue(Field.FIELD_HEIGHT).asFloat();
                        obj.put("value", height);
                        obj.put("unit", "m");
                    } else if (DT.equals(DataType.TYPE_WEIGHT)) {
                        float weight = datapoint.getValue(Field.FIELD_WEIGHT).asFloat();
                        obj.put("value", weight);
                        obj.put("unit", "kg");
                    } else if (DT.equals(DataType.TYPE_HEART_RATE_BPM)) {
                        float weight = datapoint.getValue(Field.FIELD_BPM).asFloat();
                        obj.put("value", weight);
                        obj.put("unit", "bpm");
                    } else if (DT.equals(DataType.TYPE_BODY_FAT_PERCENTAGE)) {
                        float weight = datapoint.getValue(Field.FIELD_PERCENTAGE).asFloat();
                        obj.put("value", weight);
                        obj.put("unit", "percent");
                    } else if (DT.equals(DataType.TYPE_ACTIVITY_SEGMENT)) {
                        String activity = datapoint.getValue(Field.FIELD_ACTIVITY).asActivity();
                        obj.put("value", activity);
                        obj.put("unit", "activityType");
                    } else if (DT.equals(customdatatypes.get("gender"))) {
                        for(Field f : customdatatypes.get("gender").getFields()){
                            //there should be only one field named gender
                            String gender = datapoint.getValue(f).asString();
                            obj.put("value", gender);
                        }
                    } else if (DT.equals(customdatatypes.get("date_of_birth"))) {
                        JSONObject dob = new JSONObject();
                        for(Field f : customdatatypes.get("date_of_birth").getFields()){
                            //all fields are integers
                            int fieldvalue = datapoint.getValue(f).asInt();
                            dob.put(f.getName(), fieldvalue);
                        }
                        obj.put("value", dob);
                    }

                    resultset.put(obj);
                }
            }
            callbackContext.success(resultset);
        } else {
            callbackContext.error(dataReadResult.getStatus().getStatusMessage());
        }
    }

    private void queryAggregated(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if(!args.getJSONObject(0).has("startDate")){
            callbackContext.error("Missing argument startDate");
            return;
        }
        long st = args.getJSONObject(0).getLong("startDate");
        if(!args.getJSONObject(0).has("endDate")){
            callbackContext.error("Missing argument endDate");
            return;
        }
        long et = args.getJSONObject(0).getLong("endDate");
        if(!args.getJSONObject(0).has("dataType")){
            callbackContext.error("Missing argument dataType");
            return;
        }
        String datatype = args.getJSONObject(0).getString("dataType");

        if ((mClient == null) || (!mClient.isConnected())){
            if(!lightConnect()){
                callbackContext.error("Cannot connect to Google Fit");
                return;
            }
        }

        //basal metabolic rate is treated in a different way
        if(datatype.equalsIgnoreCase("calories.basal")){
            //when querying for basal calories, the aggregated value is computed over a period that shall be larger than a day
            //as we don't expect basal calories to change much over time (they are a usually function of age, sex, weight and height)
            //so we'll choose a week as time window and then renormalise the value to the original time range
            //TODO: a better approach would be using a time window similar to the original one, in case the window is larger than a week
            Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(new Date(et));
            //set start time to a week before end time
            cal.add(Calendar.WEEK_OF_YEAR, -1);
            long nst = cal.getTimeInMillis();

            DataReadRequest.Builder builder = new DataReadRequest.Builder();
            builder.aggregate(DataType.TYPE_BASAL_METABOLIC_RATE, DataType.AGGREGATE_BASAL_METABOLIC_RATE_SUMMARY);
            builder.bucketByTime(1, TimeUnit.DAYS);
            builder.setTimeRange(nst, et, TimeUnit.MILLISECONDS);
            DataReadRequest readRequest = builder.build();

            DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await();

            if (dataReadResult.getStatus().isSuccess()) {
                JSONObject obj = new JSONObject();
                float avgs = 0;
                int avgsN = 0;
                for (Bucket bucket : dataReadResult.getBuckets()) {
                    // in the com.google.bmr.summary data type, each data point represents
                    // the average, maximum and minimum basal metabolic rate, in kcal per day, over the time interval of the data point.
                    DataSet ds = bucket.getDataSet(DataType.AGGREGATE_BASAL_METABOLIC_RATE_SUMMARY);
                    for (DataPoint dp : ds.getDataPoints()) {
                        float avg = dp.getValue(Field.FIELD_AVERAGE).asFloat();
                        avgs += avg;
                        avgsN++;
                    }
                }
                if(avgs == 0){
                    // strange case
                    // maybe the time window is too small or Fit is missing information for computing the basal
                    // let's give an error
                    // TODO: a better approach would be giving some kind of approximation (like a fixed value)
                    callbackContext.error("No basal metabolic energy expenditure found");
                }
                // do the average of the averages
                avgs /= avgsN;
                // renormalise to the original time window
                // avgs is the daily average
                avgs = (avgs / (24*60*60*1000)) * (et-st);
                obj.put("value", avgs);
                obj.put("startDate", st);
                obj.put("endDate", et);
                obj.put("unit", "kcal");

                callbackContext.success(obj);
            } else {
                callbackContext.error(dataReadResult.getStatus().getStatusMessage());
            }
            // no need to go further
            return;
        }

        DataReadRequest.Builder builder = new DataReadRequest.Builder();
        builder.setTimeRange(st, et, TimeUnit.MILLISECONDS);
        int allms = (int)(et-st);

        if(datatype.equalsIgnoreCase("steps")){
            builder.aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA);
            builder.bucketByTime(allms, TimeUnit.MILLISECONDS);
        } else if(datatype.equalsIgnoreCase("distance")){
            builder.aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA);
            builder.bucketByTime(allms, TimeUnit.MILLISECONDS);
        } else if(datatype.equalsIgnoreCase("calories")){
            builder.aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED);
            builder.bucketByTime(allms, TimeUnit.MILLISECONDS);
        } else if(datatype.equalsIgnoreCase("activity")){
            builder.aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY);
            builder.bucketByTime(allms, TimeUnit.MILLISECONDS);
        } else {
            callbackContext.error("Datatype " + datatype + " not supported");
            return;
        }

        DataReadRequest readRequest = builder.build();
        DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await();

        if (dataReadResult.getStatus().isSuccess()) {
            JSONObject obj = new JSONObject();
            for(Bucket bucket : dataReadResult.getBuckets()){
                for (DataSet dataset : bucket.getDataSets()) {
                    for (DataPoint datapoint : dataset.getDataPoints()) {
                        long nsd = datapoint.getStartTime(TimeUnit.MILLISECONDS);
                        if(obj.has("startDate")) {
                            long osd = obj.getLong("startDate");
                            if(nsd < osd) obj.put("startDate", nsd);
                        } else
                            obj.put("startDate", nsd);

                        long ned = datapoint.getEndTime(TimeUnit.MILLISECONDS);
                        if(obj.has("endDate")) {
                            long oed = obj.getLong("endDate");
                            if(ned > oed) obj.put("endDate", ned);
                        } else
                            obj.put("endDate", ned);

                        if (datatype.equalsIgnoreCase("steps")) {
                            int nsteps = datapoint.getValue(Field.FIELD_STEPS).asInt();
                            if(obj.has("value")) {
                                int osteps = obj.getInt("value");
                                obj.put("value", osteps + nsteps);
                            } else {
                                obj.put("value", nsteps);
                                obj.put("unit", "count");
                            }
                        } else if (datatype.equalsIgnoreCase("distance")) {
                            float ndist = datapoint.getValue(Field.FIELD_DISTANCE).asFloat();
                            if(obj.has("value")) {
                                double odist = obj.getDouble("value");
                                obj.put("value", odist + ndist);
                            } else {
                                obj.put("value", ndist);
                                obj.put("unit", "m");
                            }
                        } else if (datatype.equalsIgnoreCase("calories")) {
                            float ncal = datapoint.getValue(Field.FIELD_CALORIES).asFloat();
                            if(obj.has("value")) {
                                double ocal = obj.getDouble("value");
                                obj.put("value", ocal + ncal);
                            } else {
                                obj.put("value", ncal);
                                obj.put("unit", "kcal");
                            }
                        } else if (datatype.equalsIgnoreCase("activity")) {
                            JSONObject actobj;
                            String activity = datapoint.getValue(Field.FIELD_ACTIVITY).asActivity();
                            if(obj.has("value")) {
                                actobj = obj.getJSONObject("value");
                            } else {
                                actobj = new JSONObject();
                                obj.put("unit", "activitySummary");
                            }
                            JSONObject summary;
                            int ndur = datapoint.getValue(Field.FIELD_DURATION).asInt();
                            if(actobj.has(activity)){
                                summary = actobj.getJSONObject(activity);
                                int odur = summary.getInt("duration");
                                summary.put("duration", odur + ndur);
                            } else {
                                summary = new JSONObject();
                                summary.put("duration", ndur);
                            }
                            actobj.put(activity, summary);
                            obj.put("value", actobj);
                        }
                    }
                }
            }
            callbackContext.success(obj);
        } else {
            callbackContext.error(dataReadResult.getStatus().getStatusMessage());
        }
    }


    private void store(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (!args.getJSONObject(0).has("startDate")) {
            callbackContext.error("Missing argument startDate");
            return;
        }
        long st = args.getJSONObject(0).getLong("startDate");
        if (!args.getJSONObject(0).has("endDate")) {
            callbackContext.error("Missing argument endDate");
            return;
        }
        long et = args.getJSONObject(0).getLong("endDate");
        if (!args.getJSONObject(0).has("dataType")) {
            callbackContext.error("Missing argument dataType");
            return;
        }
        String datatype = args.getJSONObject(0).getString("dataType");
        if (!args.getJSONObject(0).has("value")) {
            callbackContext.error("Missing argument value");
            return;
        }
        if (!args.getJSONObject(0).has("sourceName")) {
            callbackContext.error("Missing argument sourceName");
            return;
        }
        String sourceName = args.getJSONObject(0).getString("sourceName");

		String sourceBundleId = cordova.getActivity().getApplicationContext().getPackageName();
		if (args.getJSONObject(0).has("sourceBundleId")) {
            sourceBundleId = args.getJSONObject(0).getString("sourceBundleId");
        }

        DataType dt = null;
        if (bodydatatypes.get(datatype) != null)
            dt = bodydatatypes.get(datatype);
        if (activitydatatypes.get(datatype) != null)
            dt = activitydatatypes.get(datatype);
        if (locationdatatypes.get(datatype) != null)
            dt = locationdatatypes.get(datatype);
        if (nutritiondatatypes.get(datatype) != null)
            dt = nutritiondatatypes.get(datatype);
        if (customdatatypes.get(datatype) != null)
            dt = customdatatypes.get(datatype);
        if (dt == null) {
            callbackContext.error("Datatype " + datatype + " not supported");
            return;
        }

        if ((mClient == null) || (!mClient.isConnected())){
            if(!lightConnect()){
                callbackContext.error("Cannot connect to Google Fit");
                return;
            }
        }

        DataSource datasrc = new DataSource.Builder()
                .setAppPackageName(sourceBundleId)
                .setName(sourceName)
                .setDataType(dt)
                .setType(DataSource.TYPE_RAW)
                .build();

        DataSet dataSet = DataSet.create(datasrc);
        DataPoint datapoint = DataPoint.create(datasrc);
        datapoint.setTimeInterval(st, et, TimeUnit.MILLISECONDS);
        if (dt.equals(DataType.TYPE_STEP_COUNT_DELTA)) {
            String value = args.getJSONObject(0).getString("value");
            int steps = Integer.parseInt(value);
            datapoint.getValue(Field.FIELD_STEPS).setInt(steps);
        } else if (dt.equals(DataType.TYPE_DISTANCE_DELTA)) {
            String value = args.getJSONObject(0).getString("value");
            float dist = Float.parseFloat(value);
            datapoint.getValue(Field.FIELD_DISTANCE).setFloat(dist);
        } else if (dt.equals(DataType.TYPE_CALORIES_EXPENDED)) {
            String value = args.getJSONObject(0).getString("value");
            float cals = Float.parseFloat(value);
            datapoint.getValue(Field.FIELD_CALORIES).setFloat(cals);
        } else if (dt.equals(DataType.TYPE_HEIGHT)) {
            String value = args.getJSONObject(0).getString("value");
            float height = Float.parseFloat(value);
            datapoint.getValue(Field.FIELD_HEIGHT).setFloat(height);
        } else if (dt.equals(DataType.TYPE_WEIGHT)) {
            String value = args.getJSONObject(0).getString("value");
            float weight = Float.parseFloat(value);
            datapoint.getValue(Field.FIELD_WEIGHT).setFloat(weight);
        } else if (dt.equals(DataType.TYPE_HEART_RATE_BPM)) {
            String value = args.getJSONObject(0).getString("value");
            float hr = Float.parseFloat(value);
            datapoint.getValue(Field.FIELD_BPM).setFloat(hr);
        } else if (dt.equals(DataType.TYPE_BODY_FAT_PERCENTAGE)) {
            String value = args.getJSONObject(0).getString("value");
            float perc = Float.parseFloat(value);
            datapoint.getValue(Field.FIELD_PERCENTAGE).setFloat(perc);
        }  else if (dt.equals(DataType.TYPE_ACTIVITY_SEGMENT)) {
            String value = args.getJSONObject(0).getString("value");
            datapoint.getValue(Field.FIELD_ACTIVITY).setActivity(value);
        } else if (dt.equals(customdatatypes.get("gender"))) {
            String value = args.getJSONObject(0).getString("value");
            for(Field f : customdatatypes.get("gender").getFields()){
                //we expect only one field named gender
                datapoint.getValue(f).setString(value);
            }
        } else if(dt.equals(customdatatypes.get("date_of_birth"))){
            JSONObject dob = args.getJSONObject(0).getJSONObject("value");
            int year = dob.getInt("year");
            int month = dob.getInt("month");
            int day = dob.getInt("day");

            for(Field f : customdatatypes.get("date_of_birth").getFields()){
                if(f.getName().equalsIgnoreCase("day"))
                    datapoint.getValue(f).setInt(day);
                if(f.getName().equalsIgnoreCase("month"))
                    datapoint.getValue(f).setInt(month);
                if(f.getName().equalsIgnoreCase("year"))
                    datapoint.getValue(f).setInt(year);
            }
        }
        dataSet.add(datapoint);


        Status insertStatus = Fitness.HistoryApi.insertData(mClient, dataSet)
                .await(1, TimeUnit.MINUTES);

        if (!insertStatus.isSuccess()) {
            callbackContext.error(insertStatus.getStatusMessage());
        } else {
            callbackContext.success();
        }
    }
}
