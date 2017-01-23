package com.deazzle.launchapp;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

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

	public static final String strTAG = "appLaunchupi";
	private final int START_UPI = 1;

	private CallbackContext callbackContext;

	private Context launchContext;

	private boolean NO_PARSE_INTENT_VALS = false;

	public launchApp() {}

	@Override
	public boolean execute(String pluginAction, JSONArray appLaunchArguments, final CallbackContext callbackContext) throws JSONException {
		this.callbackContext = callbackContext;
		this.launchContext = this.cordova.getActivity();
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
				
				JSONObject bankAppDetails;// = new HashMap<>();
				//PackageManager packageManager = launchContext.getPackageManager();
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
						
						//Bank details
						if (intentsParameters.has("bankAppDetails")) {
							
							bankAppDetails = intentsParameters.getJSONObject("bankAppDetails");
							Iterator<?> bankAppKeys = bankAppDetails.keys();

							while( bankAppKeys.hasNext() ){
								String bank = (String)bankAppKeys.next();
								String bankSign = bankAppDetails.getString(bank);
								Log.d(strTAG, "Bank details key = " + bank + " and value = " + bankSign);
								try {
									PackageInfo packageInfo=packageManager.getPackageInfo(bank,PackageManager.GET_META_DATA | PackageManager.GET_SIGNATURES);
									if (null != packageManager.getInstallerPackageName(bank)) { //If installed from Play Store
										if (packageManager.getInstallerPackageName(bank).equals("com.android.vending")) {
											String currentBankAppSign = "";
											int totalSignature = packageInfo.signatures.length;
											for (Signature signature : packageInfo.signatures) {
												MessageDigest md = MessageDigest.getInstance("SHA");
												md.update(signature.toByteArray());
												//String currentSignature1 = Base64.encodeToString(md.digest(), Base64.DEFAULT);
												currentBankAppSign = currentBankAppSign + Base64.encodeToString(md.digest(), Base64.NO_WRAP);
												if(totalSignature > 1) { //Handle apps with multiple signs by having appended signs
													totalSignature--;
													continue;
												}
												Log.d(strTAG, "############  current signature of  " + bank + " is " + currentBankAppSign + "  complete");
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
							//Intent chooser = Intent.createChooser(launchIntent, "Pay with...");
							Intent chooser = CustomIntentSelector.create(packageManager,launchIntent, "Pay with ",whitelist);//Intent.createChooser(intent, "Pay with...");
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
		boolean flagResponse = false;
		if((null != intent) && (requestCode == START_UPI) && (resultCode == RESULT_OK)) {
				try {
					String[] strArray = intent.getStringExtra("response").split("&");
                    for(int i =0; i<strArray.length; i++) {
                    
                        if(strArray[i].startsWith("txnRef")){
							flagResponse = true;
                            String[] txnRef = strArray[i].split("=");
                            //txnRef[1];
							this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, txnRef[1]));//intent.getStringExtra("response")));
							break;
                        }
						
                    }
					if(!flagResponse) {
						this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
					}
				} catch (Exception e) {
					Log.d(strTAG, "Error");
					this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
				}
			
		} else {
			this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
		}

	}
}
