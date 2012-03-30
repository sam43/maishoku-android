package com.maishoku.android.activities;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.maishoku.android.API;
import com.maishoku.android.IO;
import com.maishoku.android.R;
import com.maishoku.android.RedTitleBarListActivity;
import com.maishoku.android.Result;
import com.maishoku.android.models.Address;
import com.maishoku.android.models.Cart;
import com.maishoku.android.models.Restaurant;

public class RestaurantListActivity extends RedTitleBarListActivity {

	protected static final String TAG = RestaurantListActivity.class.getSimpleName();
	
	private static final int HEIGHT = 40;
	private static final int WIDTH = 60;
	private static final int ADJUSTED_HEIGHT = (int) (HEIGHT * 1.5);
	private static final int ADJUSTED_WIDTH = (int) (WIDTH * 1.5);
	private final AtomicBoolean restaurantsLoaded = new AtomicBoolean(false);
	private ArrayAdapter<Restaurant> adapter;
	private Drawable blank;
	private ProgressDialog progressDialog;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setCustomTitle(R.string.restaurants);
		blank = getResources().getDrawable(R.drawable.blank60x40);
		blank.setBounds(0, 0, WIDTH, HEIGHT);
		adapter = new ArrayAdapter<Restaurant>(this, R.layout.list_item) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				if (view instanceof TextView) {
					Restaurant restaurant = getItem(position);
					TextView textView = (TextView) view;
					textView.setLines(2);
					textView.setText(Html.fromHtml(String.format("%s<br><small>%s</small>", restaurant.getName(), restaurant.getCommaSeparatedCuisines())));
					textView.setCompoundDrawablePadding(textView.getPaddingLeft());
					textView.setCompoundDrawablesWithIntrinsicBounds(blank, null, null, null);
					new LoadDirlogoImageTask(restaurant.getDirlogo_image_url(), textView).execute();
				}
				return view;
			}
		};
		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
		layoutParams.rightMargin = 5;
		ImageButton imageButton = new ImageButton(this);
		imageButton.setBackgroundDrawable(getResources().getDrawable(android.R.drawable.btn_default_small));
		imageButton.setImageResource(R.drawable.refresh);
		imageButton.setLayoutParams(layoutParams);
		imageButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				progressDialog.show();
				new LoadRestaurantsTask().execute();
			}
		});
		RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.titleBarRelativeLayout);
		relativeLayout.addView(imageButton);
		ListView listView = getListView();
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Restaurant restaurant = adapter.getItem(position);
				if (restaurant != API.restaurant) {
					Cart.clear();
					API.restaurant = restaurant;
				}
				startActivity(new Intent(RestaurantListActivity.this, RestaurantActivity.class));
			}
		});
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		if (!restaurantsLoaded.get()) {
			progressDialog = new ProgressDialog(RestaurantListActivity.this);
			progressDialog.setTitle(R.string.loading);
			progressDialog.show();
			new LoadRestaurantsTask().execute();
		}
	}
	
	private class LoadDirlogoImageTask extends AsyncTask<Void, Void, Drawable> {
		
		private final String dirlogoImageURL;
		private final TextView textView;
		
		public LoadDirlogoImageTask(String dirlogoImageURL, TextView textView) {
			this.dirlogoImageURL = dirlogoImageURL;
			this.textView = textView;
		}
		
		@Override
		protected Drawable doInBackground(Void... params) {
			try {
				return Drawable.createFromStream(API.getImage(RestaurantListActivity.this, dirlogoImageURL), "src");
			} catch (Exception e) {
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(Drawable result) {
			if (result != null) {
				result.setBounds(0, 0, ADJUSTED_WIDTH, ADJUSTED_HEIGHT);
				textView.setCompoundDrawables(result, null, null, null);
			}
		}
	
	}
	
	private class LoadRestaurantsTask extends AsyncTask<Void, Void, Result<Restaurant[]>> {
		
		protected final String TAG = LoadRestaurantsTask.class.getSimpleName();
		
		private final AtomicReference<String> message;
		
		public LoadRestaurantsTask() {
			this.message = new AtomicReference<String>();
		}
		
		@Override
		protected Result<Restaurant[]> doInBackground(Void... params) {
			try {
				Address address = API.address;
				double lat = address.getLat();
				double lon = address.getLon();
				final URI url = API.getURL(String.format("/restaurants/search/%s?lat=%f&lon=%f", API.orderMethod.name(), lat, lon));
				HttpEntity<Void> httpEntity = API.getHttpEntity(RestaurantListActivity.this);
				RestTemplate restTemplate = new RestTemplate();
				restTemplate.setErrorHandler(new ResponseErrorHandler() {
					@Override
					public boolean hasError(ClientHttpResponse response) throws IOException {
						return response.getStatusCode() != HttpStatus.OK;
					}
					@Override
					public void handleError(ClientHttpResponse response) throws IOException {
						try {
							message.set(IO.readString(response.getBody()));
						} catch (IllegalStateException e) {
							// Content has been consumed
						}
					}
				});
				ResponseEntity<Restaurant[]> responseEntity = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Restaurant[].class);
				return new Result<Restaurant[]>(responseEntity.getStatusCode() == HttpStatus.OK, message.get(), responseEntity.getBody());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				return new Result<Restaurant[]>(false, message.get(), null);
			}
		}
		
		@Override
		protected void onPostExecute(Result<Restaurant[]> result) {
			progressDialog.dismiss();
			if (result.success) {
				Log.i(TAG, "Successfully loaded restaurants");
				adapter.clear();
				for (Restaurant restaurant: result.resource) {
					adapter.add(restaurant);
				}
				restaurantsLoaded.set(true);
			} else {
				Log.e(TAG, "Failed to load restaurants");
			}
		}
	
	}

}
