package com.deazzle.launchapp;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class launchApp extends CordovaPlugin {

	public static final String strTAG = "appLaunchupi";
	private final int START_UPI = 1;

	private CallbackContext callbackContext;

	private boolean NO_PARSE_INTENT_VALS = false;

	public launchApp() {}

	@Override
	public boolean execute(String pluginAction, JSONArray appLaunchArguments, final CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;
		if ("launch".equals(pluginAction)) {
			this.launch(appLaunchArguments, callbackContext);
		}
		return true;
	}

	public void launch(final JSONArray appLaunchArguments, final CallbackContext callbackContext) {
		this.cordova.getActivity().runOnUiThread( new Runnable() {
			public void run() {
				Intent launchIntent;
				JSONObject intentsParameters;
				JSONArray intentsFlags;

				JSONObject intentsExtra;
				JSONObject key_value;
				String intentsExtrasKey;
				String intentsExtrasValue;

				try {
					if (appLaunchArguments.get(0) instanceof JSONObject) {
						intentsParameters = appLaunchArguments.getJSONObject(0);

						if (intentsParameters.has("no_parse")) {
							NO_PARSE_INTENT_VALS = true;
						}

						if (intentsParameters.has("application")) {
							PackageManager manager = cordova.getActivity().getApplicationContext().getPackageManager();
							launchIntent = manager.getLaunchIntentForPackage(intentsParameters.getString("application"));

							if (launchIntent == null) {
								callbackContext.error("Error. No app found.");
								return;
							}
						}
						else if (intentsParameters.has("intent")) {
							launchIntent = new Intent(intentsParameters.getString("intent"));
						} else {
							launchIntent = new Intent();
						}

						if (intentsParameters.has("action")) {
							launchIntent.setAction(extractValueForIntent(intentsParameters.getString("action")));
						}
						//passed url to be converted to uri
						if (intentsParameters.has("uri")) {
							launchIntent.setData(Uri.parse(intentsParameters.getString("uri")));
						}
						//type of the intent
						if (intentsParameters.has("type")) {
							launchIntent.setType(intentsParameters.getString("type"));
						}

						//type of the package
						if (intentsParameters.has("package")) {
							launchIntent.setPackage(intentsParameters.getString("package"));
						}
						//category to be set
						if (intentsParameters.has("category")) {
							launchIntent.addCategory(extractValueForIntent(intentsParameters.getString("category")));
						}
						if (!appLaunchArguments.isNull(1)) {
							intentsExtra = appLaunchArguments.getJSONObject(1);
							Iterator<String> value = intentsExtra.keys();
							while (value.hasNext()) {
								intentsExtrasKey = value.next();
								intentsExtrasValue = intentsExtra.getString(intentsExtrasKey);
								launchIntent.putExtra(extractNameForIntent(intentsExtrasKey), intentsExtrasValue);
							}
						}

						if (intentsParameters.has("startActivity") && "forResult".equals(intentsParameters.getString("startActivity"))) {
							Log.d(strTAG, "New activity for result to be launched...");
							Intent chooser = Intent.createChooser(launchIntent, "Pay with...");

							PackageManager packageManager = cordova.getActivity().getPackageManager();
							List<ResolveInfo> activities = packageManager.queryIntentActivities(launchIntent, 0);
							boolean isIntentSafe = activities.size() > 0;

							//if there is any app to receive this intent
							if (isIntentSafe) {
								cordova.setActivityResultCallback(com.deazzle.launchapp.launchApp.this);
								cordova.getActivity().startActivityForResult(chooser, START_UPI, null);
							} else {
								(Toast.makeText(cordova.getActivity(), "No UPI app installed on this phone. Please install any one from PlayStore", Toast.LENGTH_LONG)).show();
								callbackContext.error("Error!");
							}
						} else {
							PackageManager packageManager = cordova.getActivity().getPackageManager();
							List<ResolveInfo> activities = packageManager.queryIntentActivities(launchIntent, 0);
							boolean isIntentSafe = activities.size() > 0;

							//if there is any app to receive this intent
							if (isIntentSafe) {
								cordova.getActivity().startActivity(launchIntent);
							} else {
								(Toast.makeText(cordova.getActivity(), "No UPI app installed on this phone. Please install any one from PlayStore", Toast.LENGTH_LONG)).show();
								callbackContext.error("Error!");
							}
						}
					}
					else {
						callbackContext.error("Error!");
					}
				}
				catch (JSONException e) {
					callbackContext.error("JSONException: " + e.getMessage());
					e.printStackTrace();
				}
				catch (IllegalAccessException e) {
					callbackContext.error("IllegalAccessException: " + e.getMessage());
					e.printStackTrace();
				}
				catch (NoSuchFieldException e) {
					callbackContext.error("NoSuchFieldException: " + e.getMessage());
					e.printStackTrace();
				}
				catch (ActivityNotFoundException e) {
					callbackContext.error("ActivityNotFoundException: " + e.getMessage());
					e.printStackTrace();
				}
			}
		} );
	}
	//Extract the value
	private String extractValueForIntent(String flag) throws NoSuchFieldException, IllegalAccessException {

		if(NO_PARSE_INTENT_VALS) {
			return flag;
		}

		Field intentField = Intent.class.getDeclaredField(flag);
		intentField.setAccessible(true);

		return (String) intentField.get(null);
	}

	private String extractNameForIntent(String name) {
		String extractExtraForIntent = name;

		try {
			extractExtraForIntent = extractValueForIntent(name);
		}
		catch(NoSuchFieldException e) {
			extractExtraForIntent = name;
		}
		catch(IllegalAccessException e) {
			e.printStackTrace();
			return name;
		}

		Log.e(strTAG, extractExtraForIntent);

		return extractExtraForIntent;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		Log.d(strTAG, "Inside onActivityResult");
		if((null != intent) && (requestCode == START_UPI) && (resultCode == RESULT_OK)) {
			this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, intent.getStringExtra("response")));
		} else {
			this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
		}

	}
}