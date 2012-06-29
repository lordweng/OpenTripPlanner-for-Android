/*
 * Copyright 2012 University of South Florida
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package edu.usf.cutr.opentripplanner.android.tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.opentripplanner.api.ws.GraphMetadata;
import org.osmdroid.util.GeoPoint;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.util.Log;
import de.mastacode.http.Http;
import edu.usf.cutr.opentripplanner.android.MyActivity;
import edu.usf.cutr.opentripplanner.android.OTPApp;
import edu.usf.cutr.opentripplanner.android.R;
import edu.usf.cutr.opentripplanner.android.listeners.OTPGeocodingListener;
import edu.usf.cutr.opentripplanner.android.model.Server;
import edu.usf.cutr.opentripplanner.android.pois.GooglePlaces;
import edu.usf.cutr.opentripplanner.android.pois.Nominatim;
import edu.usf.cutr.opentripplanner.android.pois.POI;
import edu.usf.cutr.opentripplanner.android.pois.Places;
import edu.usf.cutr.opentripplanner.android.util.LocationUtil;

/**
 * @author Khoa Tran
 *
 */

public class OTPGeocoding extends AsyncTask<String, Integer, Long> {
	private static final String TAG = "OTP";
	private ProgressDialog progressDialog;
	private Context context;
	private boolean isStartTextbox;
	private OTPGeocodingListener callback;
	private String placesService;
	
	private ArrayList<Address> addressesReturn = new ArrayList<Address>();
	
	private Server selectedServer;

	public OTPGeocoding(Context context, boolean isStartTextbox, Server selectedServer, String placesService, OTPGeocodingListener callback) {
		this.context = context;
		this.isStartTextbox = isStartTextbox;
		this.callback = callback;
		this.selectedServer = selectedServer;
		this.placesService = placesService;
		progressDialog = new ProgressDialog(context);
	}

	protected void onPreExecute() {
		progressDialog = ProgressDialog.show(context, "",
				"Processing geocoding. Please wait... ", true);
	}

	protected Long doInBackground(String... reqs) {
		long count = reqs.length;
		
		String address = reqs[0];
		
		if(address==null || address.equalsIgnoreCase("")) {
			return count;
		}

		if(address.equalsIgnoreCase(context.getString(R.string.my_location))) {
			GeoPoint currentLocation = LocationUtil.getLastLocation(context);
			if(currentLocation==null){
				return count;
			}
			
			Address addressReturn = new Address(Locale.US);
			addressReturn.setLatitude(currentLocation.getLatitudeE6()/1E6);
			addressReturn.setLongitude(currentLocation.getLongitudeE6()/1E6);
			addressReturn.setAddressLine(addressReturn.getMaxAddressLineIndex()+1, context.getString(R.string.my_location));
			
			addressesReturn.add(addressReturn);
			
			return count;
		}

		Geocoder gc = new Geocoder(context);
		ArrayList<Address> addresses = null;
		try {
			addresses = (ArrayList<Address>)gc.getFromLocationName(address, 
					R.integer.geocoder_max_results, 
					selectedServer.getLowerLeftLatitude(), 
					selectedServer.getLowerLeftLongitude(), 
					selectedServer.getUpperRightLatitude(), 
					selectedServer.getUpperRightLongitude());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if(addresses==null || addresses.isEmpty()){
			addresses = searchPlaces(address);
			if(addresses==null || addresses.isEmpty()){
				return count;
			}
		} else {
			for(int i=0; i<addresses.size(); i++){
				Address addr = addresses.get(i);
				String addressLine = "";
				addressLine += addr.getAddressLine(0)!=null ? addr.getAddressLine(0) : "no-name";
				addressLine += addr.getAddressLine(1)!=null ? "\n" + addr.getAddressLine(1) : "";
				addressLine += addr.getAddressLine(2)!=null ? ", " + addr.getAddressLine(2) : "";
				addr.setAddressLine(addr.getMaxAddressLineIndex()+1, addressLine);
			}
		}
		
		addressesReturn.addAll(addresses);
		
		return count;
	}
	
	private ArrayList<Address> searchPlaces(String name){
		HashMap<String, String> params = new HashMap<String, String>();
		Places p;
		
		if(placesService.equals("Google Places")){
			params.put(GooglePlaces.PARAM_LOCATION, Double.toString(selectedServer.getCenterLatitude()) + "," + Double.toString(selectedServer.getCenterLongitude()));
			params.put(GooglePlaces.PARAM_RADIUS, Double.toString(selectedServer.getRadius()));
			params.put(GooglePlaces.PARAM_NAME, name);

			String apiKey = "AIzaSyANO_4l0aroh4NuC6naYfk-vsPS12z2wco";
			p = new GooglePlaces(apiKey);
			
			Log.v(TAG, "Using Google Places!");
		} else {
			params.put(Nominatim.PARAM_NAME, name);
			p = new Nominatim(selectedServer.getLowerLeftLongitude(), 
					selectedServer.getLowerLeftLatitude(),
					selectedServer.getUpperRightLongitude(), 
					selectedServer.getUpperRightLatitude());
			
			Log.v(TAG, "Using Nominatim!");
		}

		ArrayList<POI> pois = new ArrayList<POI>();
		pois.addAll(p.getPlaces(params));

		ArrayList<Address> addresses = new ArrayList<Address>();

		for(int i=0; i<pois.size(); i++){
			POI poi = pois.get(i);
			Log.v(TAG, poi.getName() + " " + poi.getLatitude() + "," + poi.getLongitude());
			Address addr = new Address(Locale.US);
			addr.setLatitude(poi.getLatitude());
			addr.setLongitude(poi.getLongitude());
			String addressLine = poi.getAddress()==null ? poi.getName() : poi.getAddress();
			addr.setAddressLine(addr.getMaxAddressLineIndex()+1,addressLine); 
			addresses.add(addr);
		}

		return addresses;
	}

	protected void onPostExecute(Long result) {
		if (progressDialog.isShowing()) {
			progressDialog.dismiss();
		}
		
		callback.onOTPGeocodingComplete(isStartTextbox, addressesReturn);
	}
}