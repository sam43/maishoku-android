package com.maishoku.android.activities;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.maishoku.android.API;
import com.maishoku.android.IO;
import com.maishoku.android.RedTitleBarActivity;
import com.maishoku.android.R;
import com.maishoku.android.Result;
import com.maishoku.android.models.Address;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class NewAddressActivity extends RedTitleBarActivity {

	protected static final String TAG = NewAddressActivity.class.getSimpleName();
	private ProgressDialog progressDialog;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, R.layout.new_address);
		setCustomTitle(R.string.add_new_address);
		final EditText addressEditText = (EditText) findViewById(R.id.newAddressAddressEditText);
		final EditText buildingNameEditText = (EditText) findViewById(R.id.newAddressBuildingNameEditText);
		final Button submitButton = (Button) findViewById(R.id.newAddressSubmitButton);
		TextWatcher textWatcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				submitButton.setEnabled(addressEditText.getText().length() > 0 && buildingNameEditText.getText().length() > 0);
			}
		};
		addressEditText.requestFocus();
		addressEditText.addTextChangedListener(textWatcher);
		buildingNameEditText.addTextChangedListener(textWatcher);
		submitButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				progressDialog = new ProgressDialog(NewAddressActivity.this);
				progressDialog.setTitle(R.string.loading);
				progressDialog.show();
				String address = addressEditText.getText().toString();
				String buildingName = buildingNameEditText.getText().toString();
				new NewAddressTask(address, buildingName).execute();
			}
		});
	}
	
	private class NewAddressTask extends AsyncTask<Void, Void, Result<Address>> {
		
		protected final String TAG = NewAddressTask.class.getSimpleName();
		
		private final String address;
		private final String buildingName;
		private final AtomicReference<String> message;
		
		public NewAddressTask(String address, String buildingName) {
			this.address = address;
			this.buildingName = buildingName;
			this.message = new AtomicReference<String>();
		}
		
		@Override
		protected Result<Address> doInBackground(Void... params) {
			try {
				final URI url = API.getURL("/addresses");
				HttpHeaders httpHeaders = new HttpHeaders();
				httpHeaders.setContentType(MediaType.APPLICATION_JSON);
				Address a = new Address();
				a.setAddress(address);
				a.setBuilding_name(buildingName);
				HttpEntity<Address> httpEntity = API.getHttpEntity(NewAddressActivity.this, a, httpHeaders);
				RestTemplate restTemplate = new RestTemplate();
				restTemplate.setErrorHandler(new ResponseErrorHandler() {
					@Override
					public boolean hasError(ClientHttpResponse response) throws IOException {
						return response.getStatusCode() != HttpStatus.CREATED;
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
				ResponseEntity<Address> responseEntity = restTemplate.postForEntity(url, httpEntity, Address.class);
				return new Result<Address>(responseEntity.getStatusCode() == HttpStatus.CREATED, message.get(), responseEntity.getBody());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
				return new Result<Address>(false, message.get(), null);
			}
		}
		
		@Override
		protected void onPostExecute(Result<Address> result) {
			progressDialog.dismiss();
			if (result.success) {
				Log.i(TAG, "Successfully added new address");
				NewAddressActivity.this.finish();
			} else {
				Log.i(TAG, "Failed to add new address");
				new AlertDialog.Builder(NewAddressActivity.this)
					.setTitle(R.string.error)
					.setMessage(result.message)
					.setPositiveButton(R.string.ok, null)
					.show();
			}
		}
	
	}

}
