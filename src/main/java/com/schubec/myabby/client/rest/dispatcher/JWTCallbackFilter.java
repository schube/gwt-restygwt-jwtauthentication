package com.schubec.myabby.client.rest.dispatcher;

import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.callback.CallbackFilter;

import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;

public class JWTCallbackFilter implements CallbackFilter {

	@Override
	public RequestCallback filter(Method method, Response response, RequestCallback callback) {
		//GWT.log("Bernitest " + response.getStatusText());
		return callback;
	}

}
