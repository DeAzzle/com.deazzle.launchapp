package com.deazzle.launchapp;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class launchApp extends CordovaPlugin {

	private static final String strTAG = "appLaunchupi";
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

	private void launch(final JSONArray appLaunchArguments, final CallbackContext callbackContext) {
		this.cordova.getActivity().runOnUiThread( new Runnable() {
			public void run() {
			Intent launchIntent;
			JSONObject intentsParameters;

			JSONObject intentsExtra;
			String intentsExtrasKey;
			String intentsExtrasValue;

			JSONObject bankAppDetails;
			PackageManager packageManager = cordova.getActivity().getApplicationContext().getPackageManager();
			List<String> whitelist = new ArrayList<String>();


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
							callbackContext.error("{\"ResultCode\":\"ErrorNoApp\",\"ResultData\":\"No such app\"}");
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

					//Bank details
					if (intentsParameters.has("bankAppDetails")) {

						bankAppDetails = intentsParameters.getJSONObject("bankAppDetails");
						Iterator<?> bankAppKeys = bankAppDetails.keys();

						while( bankAppKeys.hasNext() ){
							String bank = (String)bankAppKeys.next();
							String bankSign = bankAppDetails.getString(bank);
							Log.d(strTAG, "Bank details key = " + bank + " and value = " + bankSign);
							try {
								PackageInfo packageInfo=packageManager.getPackageInfo(bank, PackageManager.GET_META_DATA | PackageManager.GET_SIGNATURES);
								if (null != packageManager.getInstallerPackageName(bank)) { //If installed from Play Store
									if (packageManager.getInstallerPackageName(bank).equals("com.android.vending"))
									{
										String currentBankAppSign = "";
										int totalSignature = packageInfo.signatures.length;
										for (Signature signature : packageInfo.signatures) {
											MessageDigest md = MessageDigest.getInstance("SHA");
											md.update(signature.toByteArray());
											currentBankAppSign = currentBankAppSign + Base64.encodeToString(md.digest(), Base64.NO_WRAP);
											if(totalSignature > 1) { //Handle apps with multiple signs by having appended signs
												totalSignature --;
												continue;
											}
											//Log.d(strTAG, "############  current signature of  " + bank + " is " + currentBankAppSign + "  complete");
											//Log.d(strTAG, bank[index] + " current signature is " + currentSignature + " and whitelisted sign is " + bankSign[index]);
											//Log.d(strTAG, bank[index] + " signature1 " + currentSignature1 + "whitelisted sign " + bankSign[index]);
											if(currentBankAppSign.equals(bankSign)) {
												Log.d(strTAG,"Adding to whitelist " + bank);
												whitelist.add(bank);
											}
										}
									}
								}
							} catch (PackageManager.NameNotFoundException e) {
								Log.d(strTAG, e.toString() + " package does not exist on phone");
							} catch(NoSuchAlgorithmException e) {
								Log.d(strTAG,e.toString());
							}
						}
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
						//Intent chooser = Intent.createChooser(launchIntent, "Pay with");
						CustomIntentSelector customIntentSelector = new CustomIntentSelector();
						Intent chooser = customIntentSelector.create(packageManager,launchIntent, "Pay with ",whitelist);//Intent.createChooser(intent, "Pay with...");
						List<ResolveInfo> activities = packageManager.queryIntentActivities(chooser, 0);
						boolean isIntentSafe = customIntentSelector.getNumberOfApps() > 0 ;

						//if there is any app to receive this intent
						if (isIntentSafe) {
							cordova.setActivityResultCallback(com.deazzle.launchapp.launchApp.this);
							cordova.getActivity().startActivityForResult(chooser, START_UPI, null);
						} else {
							callbackContext.error("{\"ResultCode\":\"ErrorNoApp\",\"ResultData\":\"No UPI app\"}");

						}
					} else {

						List<ResolveInfo> activities = packageManager.queryIntentActivities(launchIntent, 0);
						boolean isIntentSafe = activities.size() > 0;

						//if there is any app to receive this intent
						if (isIntentSafe) {
							cordova.getActivity().startActivity(launchIntent);
						} else {
							callbackContext.error("{\"ResultCode\":\"ErrorNoApp\",\"ResultData\":\"No UPI app\"}");
						}
					}
				}
				else {
					callbackContext.error("{\"ResultCode\":\"Error\",\"ResultData\":\"No options\"}");
				}
			}
			catch (JSONException e) {
				callbackContext.error("{\"ResultCode\":\"Error\",\"ResultData\":" + e.getMessage() +"}");
				e.printStackTrace();
			}
			catch (IllegalAccessException e) {
				callbackContext.error("{\"ResultCode\":\"Error\",\"ResultData\":" + e.getMessage() +"}");
				e.printStackTrace();
			}
			catch (NoSuchFieldException e) {
				callbackContext.error("{\"ResultCode\":\"Error\",\"ResultData\":"+e.getMessage()+"}");
				e.printStackTrace();
			}
			catch (ActivityNotFoundException e) {
				callbackContext.error("{\"ResultCode\":\"Error\",\"ResultData\":"+e.getMessage()+"}");
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
		boolean flagResponse = false;
		boolean flagSuccessStatus = true;
		String[] txnRef = {""};
		if((null != intent) && (requestCode == START_UPI) && (resultCode == RESULT_OK)) {
			try {
				String[] strArray = intent.getStringExtra("response").split("&");
				for(int i =0; i<strArray.length; i++) {
					if(strArray[i].toLowerCase().startsWith("txnref")){
						if(flagSuccessStatus) {
							flagResponse = true;
							txnRef = strArray[i].split("=");
						}
					} else if(strArray[i].toLowerCase().startsWith("status")) {
						String[] status = strArray[i].split("=");
						if(status[1].toLowerCase().contains("fail")) {
							flagResponse = false;
							flagSuccessStatus = false;
						}
					}
				}
				if(!flagResponse) {
					this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "{\"ResultCode\":\"Error\",\"ResultData\":\"UPI app returned status as failure\"}"));
				} else {
					//txnRef if null we are throwing error
					this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, txnRef[1]));
				}
			} catch (Exception e) {
				Log.d(strTAG, "Error");
				this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "{\"ResultCode\":\"Error\",\"ResultData\":\"UPI app returned error. \""+ e + "}"));
			}
		} else {
			this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "{\"ResultCode\":\"Error\",\"ResultData\":\"UPI app returned error.\"}"));
		}

	}
}
